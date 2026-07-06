package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.model.ToolDefinition
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Discovers MCP tools and converts them to AIMO tool definitions.
 */
class ToolDiscovery(
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun discoverTools(): List<ToolDefinition> {
        try {
            val response = protocolClient.sendRequest("tools/list", null)
            if (response.error != null) {
                log.error("Tool discovery failed: ${response.error.message}")
                throw DiscoveryException("Tool discovery failed: ${response.error.message}")
            }

            val result = response.result ?: throw DiscoveryException("tools/list response missing result")
            val toolsNode = result.get("tools") ?: throw DiscoveryException("tools/list response missing 'tools' field")
            
            if (!toolsNode.isArray) throw DiscoveryException("tools field is not an array")

            return toolsNode.mapNotNull { toolNode ->
                try {
                    convertToToolDefinition(toolNode)
                } catch (e: Exception) {
                    log.warn("Failed to convert tool: ${e.message}", e)
                    null
                }
            }
        } catch (e: DiscoveryException) {
            throw e
        } catch (e: Exception) {
            log.error("Tool discovery error", e)
            throw DiscoveryException("Tool discovery failed", e)
        }
    }

    private fun convertToToolDefinition(toolNode: JsonNode): ToolDefinition {
        val name = toolNode.get("name")?.asText() ?: throw DiscoveryException("Tool missing 'name'")
        val description = toolNode.get("description")?.asText()
        val inputSchema = toolNode.get("inputSchema") ?: throw DiscoveryException("Tool '$name' missing 'inputSchema'")

        validateJsonSchema(inputSchema, name)

        return ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema,
            schemaDialect = "https://json-schema.org/draft/2020-12/schema"
        )
    }

    private fun validateJsonSchema(schema: JsonNode, toolName: String) {
        if (!schema.isObject) {
            throw DiscoveryException("Tool '$toolName' inputSchema is not an object")
        }
        // Basic validation: ensure it's a valid JSON Schema structure
        // Full JSON Schema validation would be more complex
    }
}

class DiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)
