package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.model.ToolDefinition
import org.ivcode.aimo.mcpclient.protocol.ProtocolClient
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode

/**
 * Discovers MCP tools and converts them to AIMO tool definitions.
 */
class ToolDiscovery(
    private val protocolClient: ProtocolClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun discoverTools(): List<ToolDefinition> {
        return runCatching {
            val response = protocolClient.sendRequest("tools/list", null)
            response.error?.let { error ->
                log.error("Tool discovery failed: ${error.message}")
                discoveryFailure("Tool discovery failed: ${error.message}")
            }

            val result = requireNotNull(response.result) { "tools/list response missing result" }
            val toolsNode = requireNotNull(result.get("tools")) { "tools/list response missing 'tools' field" }

            require(toolsNode.isArray) { "tools field is not an array" }

            toolsNode.map { toolNode -> convertToToolDefinition(toolNode) }
        }.getOrElse { error ->
            when (error) {
                is DiscoveryException -> discoveryFailure(error.message ?: "Tool discovery failed", error)
                else -> {
                    log.error("Tool discovery error", error)
                    discoveryFailure("Tool discovery failed", error)
                }
            }
        }
    }

    private fun convertToToolDefinition(toolNode: JsonNode): ToolDefinition {
        val nameNode = requireNotNull(toolNode.get("name")) { "Tool missing 'name'" }
        val name = textValue(nameNode) ?: discoveryFailure("Tool 'name' is not a text node")

        val description = textValue(toolNode.get("description"))
        val inputSchema = requireNotNull(toolNode.get("inputSchema")) { "Tool '$name' missing 'inputSchema'" }

        validateJsonSchema(inputSchema, name)

        return ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema,
            schemaDialect = "https://json-schema.org/draft/2020-12/schema",
        )
    }

    private fun validateJsonSchema(schema: JsonNode, toolName: String) {
        require(schema.isObject) { "Tool '$toolName' inputSchema is not an object" }
        // Basic validation: ensure it's a valid JSON Schema structure
        // Full JSON Schema validation would be more complex
    }
}

private fun discoveryFailure(message: String, cause: Throwable? = null): Nothing {
    throw DiscoveryException(message, cause)
}

private fun textValue(node: JsonNode?): String? {
    return node?.takeUnless { it.isNull }?.toString()?.unquote()
}

class DiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun String.unquote(): String {
    return if (length >= 2 && startsWith('"') && endsWith('"')) {
        substring(1, length - 1)
    } else {
        this
    }
}

