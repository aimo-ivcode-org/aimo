package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.mcpclient.config.McpServerConfig
import org.ivcode.aimo.mcpclient.protocol.ProtocolClient
import org.ivcode.aimo.mcpclient.protocol.lifecycle.ClientCapabilities
import org.ivcode.aimo.mcpclient.protocol.lifecycle.ClientInfo
import org.ivcode.aimo.mcpclient.protocol.lifecycle.LifecycleManager
import org.ivcode.aimo.mcpclient.protocol.transport.StdioTransport
import org.ivcode.aimo.mcpclient.protocol.transport.HttpTransport
import org.ivcode.aimo.mcpclient.protocol.transport.ProtocolTransport
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages one persistent protocol client per configured MCP server.
 * Handles discovery of both tools and prompts (system messages), tool/prompt wrapping, and refresh.
 */
class McpClientManager(
    private val serverConfig: McpServerConfig,
    private val objectMapper: ObjectMapper,
    private val mcpRequired: Boolean = true,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val serverClients = ConcurrentHashMap<String, ServerConnection>()

    data class ServerConnection(
        val serverId: String,
        val protocolClient: ProtocolClient?,
        val lifecycleManager: LifecycleManager?,
        val toolDiscovery: ToolDiscovery?,
        val promptDiscovery: PromptDiscovery?,
        val toolCallbackFactory: McpToolCallbackFactory,
        val promptCallbackFactory: McpSystemMessageCallbackFactory,
        var cachedCallbacks: List<ToolCallback> = emptyList(),
        var cachedSystemMessages: List<SystemMessageCallback> = emptyList(),
    )

    fun initializeAll(): Map<String, List<ToolCallback>> {
        val allCallbacks = mutableMapOf<String, List<ToolCallback>>()
        log.info("Starting initialization of ${serverConfig.servers.size} MCP server(s)")

        for (server in serverConfig.servers) {
            runCatching {
                log.debug("Initializing server '${server.id}' from config...")
                val (tools, prompts) = initializeServer(server)
                allCallbacks[server.id] = tools
                log.info("✓ Server '${server.id}' initialized with ${tools.size} tools and ${prompts.size} prompts")
            }.onFailure { error ->
                if (mcpRequired) {
                    log.error("✗ Failed to initialize required server '${server.id}'", error)
                    throw error
                }

                log.warn(
                    "✗ Failed to initialize optional server '${server.id}': ${error.message}. " +
                        "Will retry on refresh.",
                    error,
                )
                // Create a placeholder connection with empty callbacks so the server can be retried on refresh.
                createFailedServerPlaceholder(server, objectMapper, serverClients)
                allCallbacks[server.id] = emptyList()
            }
        }

        val successCount = serverClients.values.count { it.protocolClient != null }
        val failedCount = serverClients.size - successCount
        log.info(
            "MCP initialization complete: " +
                "$successCount/${serverConfig.servers.size} servers initialized, " +
                "$failedCount will retry on refresh",
        )

        return allCallbacks
    }

    /**
     * Register notification handlers for a connected server.
     */
    private fun registerNotificationHandlers(server: McpServerConfig.Server, connection: ServerConnection) {
        val protocolClient = connection.protocolClient
        val toolDiscovery = connection.toolDiscovery
        val promptDiscovery = connection.promptDiscovery

        if (protocolClient == null || toolDiscovery == null || promptDiscovery == null) {
            return
        }

        protocolClient.onNotification("tools/listChanged") {
            log.debug("Received tools/listChanged notification for server '${server.id}'")
            runCatching {
                val newToolDefinitions = toolDiscovery.discoverTools()
                val newCallbacks = newToolDefinitions.map { connection.toolCallbackFactory.createCallback(it) }
                connection.cachedCallbacks = newCallbacks
                log.info("✓ Server '${server.id}' tools updated via notification: ${newCallbacks.size} tools")
            }.onFailure {
                log.error("Failed to process tools/listChanged notification for server '${server.id}'", it)
            }
        }

        protocolClient.onNotification("prompts/listChanged") {
            log.debug("Received prompts/listChanged notification for server '${server.id}'")
            runCatching {
                val newPromptDefinitions = promptDiscovery.discoverPrompts()
                val newSystemMessages = newPromptDefinitions.map { connection.promptCallbackFactory.createCallback(it) }
                connection.cachedSystemMessages = newSystemMessages
                log.info("✓ Server '${server.id}' prompts updated via notification: ${newSystemMessages.size} prompts")
            }.onFailure {
                log.error("Failed to process prompts/listChanged notification for server '${server.id}'", it)
            }
        }
    }

    private fun initializeServer(
        server: McpServerConfig.Server,
    ): Pair<List<ToolCallback>, List<SystemMessageCallback>> {
        val transport: ProtocolTransport = createTransport(server)

        val protocolClient = ProtocolClient(transport, objectMapper)
        protocolClient.connect()

        val lifecycleManager = LifecycleManager(protocolClient, objectMapper)
        runCatching {
            lifecycleManager.initialize(
                ClientInfo("aimo", "0.1"),
                ClientCapabilities(),
            )
        }.onFailure {
            protocolClient.disconnect()
        }.getOrThrow()

        val toolDiscovery = ToolDiscovery(protocolClient)
        val promptDiscovery = PromptDiscovery(protocolClient)

        val toolCallbackFactory = McpToolCallbackFactory(
            serverId = server.id,
            protocolClient = protocolClient,
            objectMapper = objectMapper,
            scopes = server.scope.toSet(),
        )

        val promptCallbackFactory = McpSystemMessageCallbackFactory(
            serverId = server.id,
            protocolClient = protocolClient,
            objectMapper = objectMapper,
            scopes = server.scope.toSet(),
        )

        // Discover tools
        val toolDefinitions = toolDiscovery.discoverTools()
        val toolCallbacks = toolDefinitions.map { toolCallbackFactory.createCallback(it) }

        // Discover prompts (system messages)
        val promptDefinitions = runCatching { promptDiscovery.discoverPrompts() }
            .onFailure { log.warn("Failed to discover prompts for server '${server.id}': ${it.message}") }
            .getOrDefault(emptyList())
        val systemMessageCallbacks = promptDefinitions.map { promptCallbackFactory.createCallback(it) }

        val connection = ServerConnection(
            serverId = server.id,
            protocolClient = protocolClient,
            lifecycleManager = lifecycleManager,
            toolDiscovery = toolDiscovery,
            promptDiscovery = promptDiscovery,
            toolCallbackFactory = toolCallbackFactory,
            promptCallbackFactory = promptCallbackFactory,
            cachedCallbacks = toolCallbacks,
            cachedSystemMessages = systemMessageCallbacks,
        )

        serverClients[server.id] = connection

        // Register notification handlers for tool and prompt changes
        registerNotificationHandlers(server, connection)

        return Pair(toolCallbacks, systemMessageCallbacks)
    }

    fun refresh(): Map<String, RefreshResult> {
        val results = mutableMapOf<String, RefreshResult>()
        val serverConfigMap = serverConfig.servers.associateBy { it.id }

        log.debug("Starting refresh of failed servers (only retrying placeholders)")

        for (server in serverConfig.servers) {
            results[server.id] = refreshServer(
                server = server,
                connection = serverClients[server.id],
                serverConfigMap = serverConfigMap,
                initializeServer = ::initializeServer,
                log = log,
            )
        }

        val healthyCount = serverClients.count { it.value.protocolClient != null && it.value.toolDiscovery != null }
        val failedCount = serverClients.size - healthyCount
        val retriedCount = results.size
        val refreshSummary = results.map { (serverId, result) ->
            result.toSummary(serverId)
        }.joinToString(", ")
        log.info(
            "Refresh completed: retried $retriedCount failed server(s) and skipped $healthyCount healthy server(s), " +
                "$failedCount connection(s) remain unresolved. Results: $refreshSummary",
        )

        return results
    }

    fun getAllCallbacks(): List<ToolCallback> {
        return serverClients.values.flatMap { it.cachedCallbacks }
    }

    fun getCallbacksForServer(serverId: String): List<ToolCallback> {
        return serverClients[serverId]?.cachedCallbacks ?: emptyList()
    }

    fun getAllSystemMessages(): List<SystemMessageCallback> {
        return serverClients.values.flatMap { it.cachedSystemMessages }
    }

    fun getSystemMessagesForServer(serverId: String): List<SystemMessageCallback> {
        return serverClients[serverId]?.cachedSystemMessages ?: emptyList()
    }

    val serverIds: List<String>
        get() = serverClients.keys.toList()

    fun getServerScopes(serverId: String): Set<String> {
        return serverClients[serverId]?.toolCallbackFactory?.getScopes() ?: emptySet()
    }

    fun shutdown() {
        for ((serverId, connection) in serverClients) {
            runCatching {
                connection.lifecycleManager?.terminate()
                connection.protocolClient?.disconnect()
                log.info("Server '$serverId' shutdown")
            }.onFailure { log.warn("Error shutting down server '$serverId'", it) }
        }
        serverClients.clear()
    }
}

/**
 * Creates the placeholder connection used for servers that fail initial startup.
 *
 * @param server the server that failed to initialize.
 * @param objectMapper shared mapper used by the callback factories.
 * @param serverClients registry that stores the placeholder connection.
 */
private fun createFailedServerPlaceholder(
    server: McpServerConfig.Server,
    objectMapper: ObjectMapper,
    serverClients: ConcurrentHashMap<String, McpClientManager.ServerConnection>,
) {
    val toolCallbackFactory = McpToolCallbackFactory(
        serverId = server.id,
        protocolClient = null,
        objectMapper = objectMapper,
        scopes = server.scope.toSet(),
    )
    val promptCallbackFactory = McpSystemMessageCallbackFactory(
        serverId = server.id,
        protocolClient = null,
        objectMapper = objectMapper,
        scopes = server.scope.toSet(),
    )

    serverClients[server.id] = McpClientManager.ServerConnection(
        serverId = server.id,
        protocolClient = null,
        lifecycleManager = null,
        toolDiscovery = null,
        promptDiscovery = null,
        toolCallbackFactory = toolCallbackFactory,
        promptCallbackFactory = promptCallbackFactory,
        cachedCallbacks = emptyList(),
        cachedSystemMessages = emptyList(),
    )
}

/**
 * Creates the transport implementation for a configured MCP server.
 *
 * @param server the server to translate into a transport.
 * @param objectMapper shared mapper for HTTP and SSE transports.
 * @return the transport instance for the server.
 */
private fun createTransport(server: McpServerConfig.Server): ProtocolTransport {
    return when (val transport = server.transport) {
        is McpServerConfig.Transport.StdioTransport -> StdioTransport(transport.command, transport.args)
        is McpServerConfig.Transport.HttpTransport -> HttpTransport(transport.url, transport.authToken, "2025-11-25")
        is McpServerConfig.Transport.SseTransport -> HttpTransport(transport.url, transport.authToken, "2025-11-25")
    }
}

/**
 * Refreshes one MCP server entry using its current connection state.
 *
 * @param server the server to refresh.
 * @param connection the current cached connection, if any.
 * @param serverConfigMap lookup table for the configured servers.
 * @param initializeServer initializer used when a server must be retried.
 * @return the refresh result for the server.
 */
private fun refreshServer(
    server: McpServerConfig.Server,
    connection: McpClientManager.ServerConnection?,
    serverConfigMap: Map<String, McpServerConfig.Server>,
    initializeServer: (McpServerConfig.Server) -> Pair<List<ToolCallback>, List<SystemMessageCallback>>,
    log: org.slf4j.Logger,
): RefreshResult {
    val serverId = server.id
    return when {
        connection == null -> {
            log.debug("Retrying initialization of uninitialized server '$serverId'...")
            runCatching { initializeServer(server) }.fold(
                onSuccess = { (tools, prompts) ->
                    log.info(
                        "✓ Server '$serverId' successfully initialized on retry with " +
                            "${tools.size} tools and ${prompts.size} prompts",
                    )
                    RefreshResult.Success(tools.size)
                },
                onFailure = { error ->
                    val retryError = error.message ?: "Unknown error during retry"
                    log.warn("✗ Failed to initialize server '$serverId' on retry: $retryError", error)
                    RefreshResult.Failure(retryError)
                },
            )
        }

        connection.protocolClient == null || connection.toolDiscovery == null -> {
            val serverConfig = serverConfigMap[serverId] ?: run {
                log.warn("Server config not found for '$serverId', cannot retry")
                return RefreshResult.Failure("Server config not found")
            }
            log.debug("Retrying initialization of placeholder server '$serverId'...")
            runCatching { initializeServer(serverConfig) }.fold(
                onSuccess = { (tools, prompts) ->
                    log.info(
                        "✓ Server '$serverId' successfully initialized on retry with " +
                            "${tools.size} tools and ${prompts.size} prompts",
                    )
                    RefreshResult.Success(tools.size)
                },
                onFailure = { error ->
                    val retryError = error.message ?: "Unknown error during retry"
                    log.warn("✗ Failed to initialize server '$serverId' on retry: $retryError", error)
                    RefreshResult.Failure(retryError)
                },
            )
        }

        else -> {
            log.debug(
                "Server '$serverId' is healthy, skipping refresh " +
                    "(tool/prompt changes handled via notifications)",
            )
            RefreshResult.Success(connection.cachedCallbacks.size)
        }
    }
}

sealed class RefreshResult {
    data class Success(val toolCount: Int) : RefreshResult()
    data class Failure(val error: String) : RefreshResult()
}

private fun RefreshResult.toSummary(serverId: String): String =
    when (this) {
        is RefreshResult.Success -> "$serverId=OK(${toolCount} tools)"
        is RefreshResult.Failure -> "$serverId=FAILED: $error"
    }

