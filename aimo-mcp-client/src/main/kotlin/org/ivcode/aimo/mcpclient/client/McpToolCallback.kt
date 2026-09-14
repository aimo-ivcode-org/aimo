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
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient?,
    private val objectMapper: ObjectMapper,
    private val scopes: Set<String> = emptySet(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getScopes(): Set<String> = scopes

    fun createCallback(toolDefinition: ToolDefinition): ToolCallback {
        val namespacedName = "$serverId:${toolDefinition.name}"
        return McpToolCallback(
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
    override val toolDefinition: ToolDefinition,
    private val serverId: String,
    private val toolName: String,
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient?,
    private val objectMapper: ObjectMapper,
    override val scopes: Set<String>,
) : ToolCallback {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun call(argumentsJson: String, context: Map<String, Any>): String =
        when (val client = protocolClient) {
            null -> {
                log.warn("Tool callback skipped: Server '$serverId' is not yet initialized")
                "Error: Server '$serverId' is not yet initialized"
            }

            else -> runCatching {
                val argsNode = objectMapper.readTree(argumentsJson)
                val params = objectMapper.createObjectNode().apply {
                    set("name", objectMapper.valueToTree(toolName))
                    set("arguments", argsNode)
                }

                val response = client.sendRequest("tools/call", params)
                when {
                    response.error != null -> {
                        log.error("Tool call failed: serverId=$serverId tool=$toolName error=${response.error.message}")
                        "Error: ${response.error.message}"
                    }

                    response.result == null -> "Tool execution returned no result"
                    else -> response.result.toString()
                }
            }.getOrElse { error ->
                log.error("Tool callback execution failed: serverId=$serverId tool=$toolName", error)
                "Error executing tool: ${error.message}"
            }
        }
}
