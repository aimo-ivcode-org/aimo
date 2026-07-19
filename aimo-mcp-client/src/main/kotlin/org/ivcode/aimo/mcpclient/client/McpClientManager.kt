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
            try {
                log.debug("Initializing server '${server.id}' from config...")
                val (tools, prompts) = initializeServer(server)
                allCallbacks[server.id] = tools
                log.info("✓ Server '${server.id}' initialized with ${tools.size} tools and ${prompts.size} prompts")
            } catch (e: Exception) {
                if (mcpRequired) {
                    log.error("✗ Failed to initialize required server '${server.id}'", e)
                    throw e
                } else {
                    log.warn("✗ Failed to initialize optional server '${server.id}': ${e.message}. Will retry on refresh.", e)
                    // Create a placeholder connection with empty callbacks so the server can be retried on refresh
                    createFailedServerPlaceholder(server)
                    allCallbacks[server.id] = emptyList()
                }
            }
        }

        val successCount = serverClients.values.count { it.protocolClient != null }
        val failedCount = serverClients.size - successCount
        log.info("MCP initialization complete: $successCount/${serverConfig.servers.size} servers initialized, $failedCount will retry on refresh")

        return allCallbacks
    }

    /**
     * Create a placeholder ServerConnection for a server that failed initialization.
     * This allows the server to be included in provider discovery and retried on refresh.
     */
    private fun createFailedServerPlaceholder(server: McpServerConfig.Server) {
        // Create minimal factories that can be retried on refresh
        val toolCallbackFactory = McpToolCallbackFactory(
            serverId = server.id,
            protocolClient = null,  // Will be created on successful initialization
            objectMapper = objectMapper,
            scopes = server.scope.toSet(),
        )

        val promptCallbackFactory = McpSystemMessageCallbackFactory(
            serverId = server.id,
            protocolClient = null,  // Will be created on successful initialization
            objectMapper = objectMapper,
            scopes = server.scope.toSet(),
        )

        val connection = ServerConnection(
            serverId = server.id,
            protocolClient = null,  // Placeholder
            lifecycleManager = null,  // Placeholder
            toolDiscovery = null,  // Placeholder
            promptDiscovery = null,  // Placeholder
            toolCallbackFactory = toolCallbackFactory,
            promptCallbackFactory = promptCallbackFactory,
            cachedCallbacks = emptyList(),
            cachedSystemMessages = emptyList(),
        )

        serverClients[server.id] = connection
        log.debug("Created placeholder for server '${server.id}' (waiting for successful initialization on refresh)")
    }

    /**
     * Register notification handlers for a connected server.
     */
    private fun registerNotificationHandlers(server: McpServerConfig.Server, connection: ServerConnection) {
        val protocolClient = connection.protocolClient ?: return
        val toolDiscovery = connection.toolDiscovery ?: return
        val promptDiscovery = connection.promptDiscovery ?: return
        val toolCallbackFactory = connection.toolCallbackFactory
        val promptCallbackFactory = connection.promptCallbackFactory

        protocolClient.onNotification("tools/listChanged") { params ->
            try {
                log.debug("Received tools/listChanged notification for server '${server.id}'")
                val newToolDefinitions = toolDiscovery.discoverTools()
                val newCallbacks = newToolDefinitions.map { toolCallbackFactory.createCallback(it) }
                connection.cachedCallbacks = newCallbacks
                log.info("✓ Server '${server.id}' tools updated via notification: ${newCallbacks.size} tools")
            } catch (e: Exception) {
                log.error("Failed to process tools/listChanged notification for server '${server.id}'", e)
            }
        }

        protocolClient.onNotification("prompts/listChanged") { params ->
            try {
                log.debug("Received prompts/listChanged notification for server '${server.id}'")
                val newPromptDefinitions = promptDiscovery.discoverPrompts()
                val newSystemMessages = newPromptDefinitions.map { promptCallbackFactory.createCallback(it) }
                connection.cachedSystemMessages = newSystemMessages
                log.info("✓ Server '${server.id}' prompts updated via notification: ${newSystemMessages.size} prompts")
            } catch (e: Exception) {
                log.error("Failed to process prompts/listChanged notification for server '${server.id}'", e)
            }
        }
    }

    private fun initializeServer(server: McpServerConfig.Server): Pair<List<ToolCallback>, List<SystemMessageCallback>> {
        val transport: ProtocolTransport = when (server.transport) {
            is McpServerConfig.Transport.StdioTransport -> {
                val stdio = server.transport as McpServerConfig.Transport.StdioTransport
                StdioTransport(stdio.command, stdio.args)
            }
            is McpServerConfig.Transport.HttpTransport -> {
                val http = server.transport as McpServerConfig.Transport.HttpTransport
                HttpTransport(http.url, http.authToken, "2025-11-25", objectMapper)
            }
            is McpServerConfig.Transport.SseTransport -> {
                val sse = server.transport as McpServerConfig.Transport.SseTransport
                // Treat SSE transport as Streamable HTTP with SSE response support.
                HttpTransport(sse.url, sse.authToken, "2025-11-25", objectMapper)
            }
        }

        val protocolClient = ProtocolClient(transport, objectMapper)
        protocolClient.connect()

        val lifecycleManager = LifecycleManager(protocolClient, objectMapper)
        try {
            lifecycleManager.initialize(
                ClientInfo("aimo", "0.1"),
                ClientCapabilities()
            )
        } catch (e: Exception) {
            protocolClient.disconnect()
            throw e
        }

        val toolDiscovery = ToolDiscovery(protocolClient, objectMapper)
        val promptDiscovery = PromptDiscovery(protocolClient, objectMapper)

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
        val promptDefinitions = try {
            promptDiscovery.discoverPrompts()
        } catch (e: Exception) {
            log.warn("Failed to discover prompts for server '${server.id}': ${e.message}")
            emptyList()
        }
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
             val serverId = server.id
             val connection = serverClients[serverId]

             try {
                 if (connection == null) {
                     // Server wasn't initialized (e.g. optional startup failure) — try to initialize now.
                     log.debug("Retrying initialization of uninitialized server '$serverId'...")
                     try {
                         val (tools, prompts) = initializeServer(server)
                         results[serverId] = RefreshResult.Success(tools.size)
                         log.info("✓ Server '$serverId' successfully initialized on retry with ${tools.size} tools and ${prompts.size} prompts")
                     } catch (retryException: Exception) {
                         results[serverId] = RefreshResult.Failure(retryException.message ?: "Unknown error during retry")
                         log.warn("✗ Failed to initialize server '$serverId' on retry: ${retryException.message}", retryException)
                     }
                 } else if (connection.protocolClient == null || connection.toolDiscovery == null) {
                     // Placeholder exists - try to initialize from config
                     log.debug("Retrying initialization of placeholder server '$serverId'...")
                     val serverConfig = serverConfigMap[serverId]

                     if (serverConfig == null) {
                         results[serverId] = RefreshResult.Failure("Server config not found")
                         log.warn("Server config not found for '$serverId', cannot retry")
                     } else {
                         try {
                             log.debug("Attempting to initialize server '$serverId'...")
                             val (tools, prompts) = initializeServer(serverConfig)
                             results[serverId] = RefreshResult.Success(tools.size)
                             log.info("✓ Server '$serverId' successfully initialized on retry with ${tools.size} tools and ${prompts.size} prompts")
                         } catch (retryException: Exception) {
                             log.warn("✗ Failed to initialize server '$serverId' on retry: ${retryException.message}", retryException)
                             results[serverId] = RefreshResult.Failure(retryException.message ?: "Unknown error during retry")
                             // Don't rethrow - keep the placeholder for next retry
                         }
                      }
                  } else {
                      // Server is healthy - skip; tool and prompt changes are handled via notifications
                      log.debug("Server '$serverId' is healthy, skipping refresh (tool/prompt changes handled via notifications)")
                      results[serverId] = RefreshResult.Success(connection.cachedCallbacks.size)
                  }
              } catch (e: Exception) {
                 results[serverId] = RefreshResult.Failure(e.message ?: "Unknown error")
                 log.error("Unexpected error during refresh of server '$serverId'", e)
             }
         }

        val healthyCount = serverClients.count { it.value.protocolClient != null && it.value.toolDiscovery != null }
        val failedCount = serverClients.size - healthyCount
        val retriedCount = results.size
        log.info("Refresh completed: retried $retriedCount failed server(s), skipped $healthyCount healthy server(s). Results: ${results.map { "${it.key}=${if (it.value is RefreshResult.Success) "OK(${(it.value as RefreshResult.Success).toolCount} tools)" else "FAILED: ${(it.value as RefreshResult.Failure).error}"}" }.joinToString(", ")}")

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

    fun getServerIds(): List<String> {
        return serverClients.keys.toList()
    }

    fun getServerScopes(serverId: String): Set<String> {
        return serverClients[serverId]?.toolCallbackFactory?.getScopes() ?: emptySet()
    }

    fun shutdown() {
        for ((serverId, connection) in serverClients) {
            try {
                connection.lifecycleManager?.terminate()
                connection.protocolClient?.disconnect()
                log.info("Server '$serverId' shutdown")
            } catch (e: Exception) {
                log.warn("Error shutting down server '$serverId'", e)
            }
        }
        serverClients.clear()
    }
}

sealed class RefreshResult {
    data class Success(val toolCount: Int) : RefreshResult()
    data class Failure(val error: String) : RefreshResult()
}
