package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Represents a discovered MCP prompt with its metadata and argument schema.
 */
data class PromptDefinition(
    val name: String,
    val description: String?,
    val argumentSchema: JsonNode?, // JSON Schema for arguments
)

/**
 * Discovers MCP prompts and converts them to AIMO system message definitions.
 */
class PromptDiscovery(
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun discoverPrompts(): List<PromptDefinition> {
        try {
            val response = protocolClient.sendRequest("prompts/list", null)
            if (response.error != null) {
                log.error("Prompt discovery failed: ${response.error.message}")
                throw DiscoveryException("Prompt discovery failed: ${response.error.message}")
            }

            val result = response.result ?: throw DiscoveryException("prompts/list response missing result")
            val promptsNode = result.get("prompts") ?: throw DiscoveryException("prompts/list response missing 'prompts' field")

            if (!promptsNode.isArray) throw DiscoveryException("prompts field is not an array")

            return promptsNode.map { promptNode ->
                try {
                    convertToPromptDefinition(promptNode)
                } catch (e: Exception) {
                    val promptName = promptNode.get("name")?.let {
                        try {
                            objectMapper.treeToValue(it, String::class.java)
                        } catch (_: Exception) {
                            null
                        }
                    } ?: "<unknown>"
                    throw DiscoveryException("Failed to convert discovered MCP prompt '$promptName'", e)
                }
            }
        } catch (e: DiscoveryException) {
            throw e
        } catch (e: Exception) {
            log.error("Prompt discovery error", e)
            throw DiscoveryException("Prompt discovery failed", e)
        }
    }

    private fun convertToPromptDefinition(promptNode: JsonNode): PromptDefinition {
        val name = try {
            val node = promptNode.get("name") ?: throw DiscoveryException("Prompt missing 'name'")
            objectMapper.treeToValue(node, String::class.java)
        } catch (e: Exception) {
            throw DiscoveryException("Prompt missing or invalid 'name'", e)
        }
        val description = promptNode.get("description")?.let {
            try {
                objectMapper.treeToValue(it, String::class.java)
            } catch (_: Exception) {
                null
            }
        }
        val argumentSchema = promptNode.get("arguments")

        return PromptDefinition(
            name = name,
            description = description,
            argumentSchema = argumentSchema,
        )
    }
}

