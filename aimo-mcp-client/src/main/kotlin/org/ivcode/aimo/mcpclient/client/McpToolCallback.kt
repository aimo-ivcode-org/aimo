package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

/**
 * Wraps MCP tool definitions as AIMO tool callbacks.
 */
class McpToolCallbackFactory(
    private val serverId: String,
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient,
    private val objectMapper: ObjectMapper,
    private val scopes: Set<String> = emptySet(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createCallback(toolDefinition: ToolDefinition): ToolCallback {
        val namespacedName = "$serverId:${toolDefinition.name}"
        return McpToolCallback(
            namespacedName = namespacedName,
            toolDefinition = toolDefinition.copy(name = namespacedName),
            serverId = serverId,
            toolName = toolDefinition.name,
            protocolClient = protocolClient,
            objectMapper = objectMapper,
            scopes = scopes,
        )
    }
}

class McpToolCallback(
    private val namespacedName: String,
    override val toolDefinition: ToolDefinition,
    private val serverId: String,
    private val toolName: String,
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient,
    private val objectMapper: ObjectMapper,
    override val scopes: Set<String>,
) : ToolCallback {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun call(argumentsJson: String, context: Map<String, Any>): String {
        return try {
            val argsNode = objectMapper.readTree(argumentsJson)
            val params = objectMapper.createObjectNode().apply {
                set("name", objectMapper.valueToTree(toolName))
                set("arguments", argsNode)
            }

            val response = protocolClient.sendRequest("tools/call", params)

            if (response.error != null) {
                log.error("Tool call failed: serverId=$serverId tool=$toolName error=${response.error.message}")
                return "Error: ${response.error.message}"
            }

            val result = response.result ?: return "Tool execution returned no result"
            result.toString()
        } catch (e: Exception) {
            log.error("Tool callback execution failed: serverId=$serverId tool=$toolName", e)
            "Error executing tool: ${e.message}"
        }
    }
}
