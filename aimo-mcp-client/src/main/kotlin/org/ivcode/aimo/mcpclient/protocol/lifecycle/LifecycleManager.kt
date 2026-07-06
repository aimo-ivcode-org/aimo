package org.ivcode.aimo.mcpclient.protocol.lifecycle

import org.ivcode.aimo.mcpclient.protocol.ProtocolClient
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Manages MCP lifecycle: initialize → capabilities negotiation → ready.
 */
class LifecycleManager(
    private val protocolClient: ProtocolClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun initialize(clientInfo: ClientInfo, capabilities: ClientCapabilities): ServerInfo {
        log.info("Initializing MCP client: name=${clientInfo.name} version=${clientInfo.version}")
        
        val params = objectMapper.createObjectNode().apply {
            put("protocolVersion", "2024-11-05")
            set("clientInfo", objectMapper.valueToTree(clientInfo))
            set("capabilities", objectMapper.valueToTree(capabilities))
        }

        val response = protocolClient.sendRequest("initialize", params)
        if (response.error != null) {
            throw LifecycleException("Initialize failed: ${response.error.message}")
        }

        val result = response.result ?: throw LifecycleException("Initialize response missing result")
        val serverInfo = objectMapper.treeToValue(result, ServerInfo::class.java)
        
        log.info("MCP server initialized: name=${serverInfo.serverInfo.name} version=${serverInfo.serverInfo.version}")
        
        // Send initialized notification
        protocolClient.sendNotification("notifications/initialized", null)
        
        return serverInfo
    }

    fun terminate() {
        try {
            log.info("Terminating MCP client")
            protocolClient.sendNotification("close", null)
            Thread.sleep(100)
        } catch (e: Exception) {
            log.warn("Error during terminate", e)
        }
    }
}

data class ClientInfo(val name: String, val version: String)
data class ClientCapabilities(val sampling: Map<String, Any>? = null, val experimental: Map<String, Any>? = null)
data class ServerInfo(val protocolVersion: String, val serverInfo: ServerDetails, val capabilities: ServerCapabilities)
data class ServerDetails(val name: String, val version: String)
data class ServerCapabilities(val tools: ToolsCapability? = null, val resources: Map<String, Any>? = null, val prompts: Map<String, Any>? = null, val experimental: Map<String, Any>? = null)
data class ToolsCapability(val listChanged: Boolean? = null)

class LifecycleException(message: String, cause: Throwable? = null) : Exception(message, cause)
