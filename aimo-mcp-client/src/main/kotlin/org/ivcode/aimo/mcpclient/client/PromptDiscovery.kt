package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.mcpclient.protocol.ProtocolClient
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode

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
    private val protocolClient: ProtocolClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun discoverPrompts(): List<PromptDefinition> {
        return runCatching {
            val response = protocolClient.sendRequest("prompts/list", null)
            response.error?.let { error ->
                log.error("Prompt discovery failed: ${error.message}")
                discoveryFailure("Prompt discovery failed: ${error.message}")
            }

            val result = requireNotNull(response.result) { "prompts/list response missing result" }
            val promptsNode = requireNotNull(result.get("prompts")) { "prompts/list response missing 'prompts' field" }

            require(promptsNode.isArray) { "prompts field is not an array" }

            promptsNode.map { promptNode -> convertToPromptDefinition(promptNode) }
        }.getOrElse { error ->
            when (error) {
                is DiscoveryException -> discoveryFailure(error.message ?: "Prompt discovery failed", error)
                else -> {
                    log.error("Prompt discovery error", error)
                    discoveryFailure("Prompt discovery failed", error)
                }
            }
        }
    }

    private fun convertToPromptDefinition(promptNode: JsonNode): PromptDefinition {
        val nameNode = requireNotNull(promptNode.get("name")) { "Prompt missing 'name'" }
        val name = textValue(nameNode) ?: discoveryFailure("Prompt 'name' is not a text node")

        val description = textValue(promptNode.get("description"))
        val argumentSchema = promptNode.get("arguments")

        return PromptDefinition(
            name = name,
            description = description,
            argumentSchema = argumentSchema,
        )
    }
}

private fun discoveryFailure(message: String, cause: Throwable? = null): Nothing {
    throw DiscoveryException(message, cause)
}

private fun textValue(node: JsonNode?): String? {
    return node?.takeUnless { it.isNull }?.toString()?.unquote()
}

private fun String.unquote(): String {
    return if (length >= 2 && startsWith('"') && endsWith('"')) {
        substring(1, length - 1)
    } else {
        this
    }
}

