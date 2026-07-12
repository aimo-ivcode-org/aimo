package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.model.ToolCallback
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
 * Handles discovery, tool wrapping, and refresh.
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
        val discovery: ToolDiscovery?,
        val callbackFactory: McpToolCallbackFactory,
        var cachedCallbacks: List<ToolCallback> = emptyList(),
    )

    fun initializeAll(): Map<String, List<ToolCallback>> {
        val allCallbacks = mutableMapOf<String, List<ToolCallback>>()
        log.info("Starting initialization of ${serverConfig.servers.size} MCP server(s)")

        for (server in serverConfig.servers) {
            try {
                log.debug("Initializing server '${server.id}' from config...")
                val callbacks = initializeServer(server)
                allCallbacks[server.id] = callbacks
                log.info("✓ Server '${server.id}' initialized with ${callbacks.size} tools")
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

        val successCount = allCallbacks.count { it.value.isNotEmpty() }
        val failedCount = serverConfig.servers.size - successCount
        log.info("MCP initialization complete: $successCount/${ serverConfig.servers.size} servers initialized, $failedCount will retry on refresh")

        return allCallbacks
    }

    /**
     * Create a placeholder ServerConnection for a server that failed initialization.
     * This allows the server to be included in provider discovery and retried on refresh.
     */
    private fun createFailedServerPlaceholder(server: McpServerConfig.Server) {
        // Create a minimal connection that can be retried on refresh
        // We store the server config so refresh can try full initialization later
        val callbackFactory = McpToolCallbackFactory(
            serverId = server.id,
            protocolClient = null,  // Will be created on successful initialization
            objectMapper = objectMapper,
            scopes = server.scope.toSet(),
        )

        val connection = ServerConnection(
            serverId = server.id,
            protocolClient = null,  // Placeholder
            lifecycleManager = null,  // Placeholder
            discovery = null,  // Placeholder
            callbackFactory = callbackFactory,
            cachedCallbacks = emptyList(),
        )

        serverClients[server.id] = connection
        log.debug("Created placeholder for server '${server.id}' (waiting for successful initialization on refresh)")
    }

    private fun initializeServer(server: McpServerConfig.Server): List<ToolCallback> {
        val transport: ProtocolTransport = when (server.transport) {
            is McpServerConfig.Transport.StdioTransport -> {
                val stdio = server.transport as McpServerConfig.Transport.StdioTransport
                StdioTransport(stdio.command, stdio.args)
            }
            is McpServerConfig.Transport.HttpTransport -> {
                val http = server.transport as McpServerConfig.Transport.HttpTransport
                HttpTransport(http.url, http.authToken, objectMapper)
            }
            is McpServerConfig.Transport.SseTransport -> {
                throw IllegalArgumentException("SSE transport not yet implemented")
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

        val discovery = ToolDiscovery(protocolClient, objectMapper)
        val callbackFactory = McpToolCallbackFactory(
            serverId = server.id,
            protocolClient = protocolClient,
            objectMapper = objectMapper,
            scopes = server.scope.toSet(),
        )

        val toolDefinitions = discovery.discoverTools()
        val callbacks = toolDefinitions.map { callbackFactory.createCallback(it) }

        val connection = ServerConnection(
            serverId = server.id,
            protocolClient = protocolClient,
            lifecycleManager = lifecycleManager,
            discovery = discovery,
            callbackFactory = callbackFactory,
            cachedCallbacks = callbacks,
        )

        serverClients[server.id] = connection

        return callbacks
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
                         val callbacks = initializeServer(server)
                         results[serverId] = RefreshResult.Success(callbacks.size)
                         log.info("✓ Server '$serverId' successfully initialized on retry with ${callbacks.size} tools")
                     } catch (retryException: Exception) {
                         results[serverId] = RefreshResult.Failure(retryException.message ?: "Unknown error during retry")
                         log.warn("✗ Failed to initialize server '$serverId' on retry: ${retryException.message}", retryException)
                     }
                 } else if (connection.protocolClient == null || connection.discovery == null) {
                     // Placeholder exists - try to initialize from config
                     log.debug("Retrying initialization of placeholder server '$serverId'...")
                     val serverConfig = serverConfigMap[serverId]

                     if (serverConfig == null) {
                         results[serverId] = RefreshResult.Failure("Server config not found")
                         log.warn("Server config not found for '$serverId', cannot retry")
                     } else {
                         try {
                             log.debug("Attempting to initialize server '$serverId'...")
                             val callbacks = initializeServer(serverConfig)
                             results[serverId] = RefreshResult.Success(callbacks.size)
                             log.info("✓ Server '$serverId' successfully initialized on retry with ${callbacks.size} tools")
                         } catch (retryException: Exception) {
                             log.warn("✗ Failed to initialize server '$serverId' on retry: ${retryException.message}", retryException)
                             results[serverId] = RefreshResult.Failure(retryException.message ?: "Unknown error during retry")
                             // Don't rethrow - keep the placeholder for next retry
                         }
                     }
                 } else {
                     // Server is healthy - skip refresh since it relies on tools/listChanged notifications
                     log.debug("Skipping already-healthy server '$serverId' (will use tools/listChanged notifications)")
                 }
             } catch (e: Exception) {
                 results[serverId] = RefreshResult.Failure(e.message ?: "Unknown error")
                 log.error("Unexpected error during refresh of server '$serverId'", e)
             }
         }

        val healthyCount = serverClients.count { it.value.protocolClient != null && it.value.discovery != null }
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

    fun getServerIds(): List<String> {
        return serverClients.keys.toList()
    }

    fun getServerScopes(serverId: String): Set<String> {
        return serverClients[serverId]?.callbackFactory?.getScopes() ?: emptySet()
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
