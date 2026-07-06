package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.mcpclient.config.McpServerConfig
import org.ivcode.aimo.mcpclient.protocol.ProtocolClient
import org.ivcode.aimo.mcpclient.protocol.lifecycle.ClientCapabilities
import org.ivcode.aimo.mcpclient.protocol.lifecycle.ClientInfo
import org.ivcode.aimo.mcpclient.protocol.lifecycle.LifecycleManager
import org.ivcode.aimo.mcpclient.protocol.transport.StdioTransport
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
        val protocolClient: ProtocolClient,
        val lifecycleManager: LifecycleManager,
        val discovery: ToolDiscovery,
        val callbackFactory: McpToolCallbackFactory,
        var cachedCallbacks: List<ToolCallback> = emptyList(),
    )

    fun initializeAll(): Map<String, List<ToolCallback>> {
        val allCallbacks = mutableMapOf<String, List<ToolCallback>>()

        for (server in serverConfig.servers) {
            try {
                val callbacks = initializeServer(server)
                allCallbacks[server.id] = callbacks
                log.info("Server '${server.id}' initialized with ${callbacks.size} tools")
            } catch (e: Exception) {
                if (mcpRequired) {
                    log.error("Failed to initialize required server '${server.id}'", e)
                    throw e
                } else {
                    log.warn("Failed to initialize optional server '${server.id}': ${e.message}")
                }
            }
        }

        return allCallbacks
    }

    private fun initializeServer(server: McpServerConfig.Server): List<ToolCallback> {
        val transport: ProtocolTransport = when (server.transport) {
            is McpServerConfig.Transport.StdioTransport -> {
                val stdio = server.transport as McpServerConfig.Transport.StdioTransport
                StdioTransport(stdio.command, stdio.args)
            }
            else -> throw IllegalArgumentException("Unsupported transport type: ${server.transport.type}")
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

        for ((serverId, connection) in serverClients) {
            try {
                val toolDefinitions = connection.discovery.discoverTools()
                val callbacks = toolDefinitions.map { connection.callbackFactory.createCallback(it) }
                connection.cachedCallbacks = callbacks
                results[serverId] = RefreshResult.Success(callbacks.size)
                log.info("Server '$serverId' refreshed: ${callbacks.size} tools")
            } catch (e: Exception) {
                results[serverId] = RefreshResult.Failure(e.message ?: "Unknown error")
                log.error("Failed to refresh server '$serverId'", e)
            }
        }

        return results
    }

    fun getAllCallbacks(): List<ToolCallback> {
        return serverClients.values.flatMap { it.cachedCallbacks }
    }

    fun shutdown() {
        for ((serverId, connection) in serverClients) {
            try {
                connection.lifecycleManager.terminate()
                connection.protocolClient.disconnect()
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
