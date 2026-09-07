package org.ivcode.aimo.mcpclient.client

import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.JsonNode

/**
 * Wraps MCP prompt definitions as AIMO system message callbacks.
 */
class McpSystemMessageCallbackFactory(
    private val serverId: String,
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient?,
    private val objectMapper: ObjectMapper,
    private val scopes: Set<String> = emptySet(),
) {
    fun createCallback(promptDefinition: PromptDefinition): SystemMessageCallback {
        val namespacedName = "$serverId:${promptDefinition.name}"
        return McpSystemMessageCallback(
            namespacedName = namespacedName,
            serverId = serverId,
            promptName = promptDefinition.name,
            protocolClient = protocolClient,
            objectMapper = objectMapper,
            scopes = scopes,
        )
    }
}

/**
 * System message callback that fetches content from an MCP server's prompt.
 *
 * Calls `prompts/get` to retrieve the prompt text, optionally with arguments
 * if the context provides them. Prompt arguments can be passed via the context
 * map using the key "{serverId}:{promptName}:args".
 */
class McpSystemMessageCallback(
    namespacedName: String,
    private val serverId: String,
    private val promptName: String,
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient?,
    private val objectMapper: ObjectMapper,
    override val scopes: Set<String>,
) : SystemMessageCallback {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = namespacedName

    override fun call(context: SystemMessageContext): String? =
        when (val client = protocolClient) {
            null -> {
                log.warn("System message callback skipped: Server '$serverId' is not yet initialized")
                null
            }

            else -> runCatching {
                // Build the prompts/get request.
                val params = objectMapper.createObjectNode().apply {
                    put("name", promptName)

                    // Check if caller provided arguments for this prompt.
                    val argsKey = "$serverId:$promptName:args"
                    val args = context.context[argsKey]
                    if (args != null) {
                        set("arguments", objectMapper.valueToTree(args))
                    }
                }

                val response = client.sendRequest("prompts/get", params)

                when {
                    response.error != null -> {
                        log.error(
                            "Prompt call failed: serverId=$serverId prompt=$promptName " +
                                "error=${response.error.message}",
                        )
                        null
                    }

                    response.result == null -> {
                        log.warn("Prompt execution returned no result: serverId=$serverId prompt=$promptName")
                        null
                    }

                    else -> renderPromptResult(response.result)
                }
            }.onFailure { error ->
                log.error("System message callback execution failed: serverId=$serverId prompt=$promptName", error)
            }.getOrNull()
        }

    private fun renderPromptResult(result: JsonNode): String {
        // MCP prompts/get returns { messages: [...] } where each message has text content.
        val messages = result.get("messages")
        return if (messages != null && messages.isArray) {
            messages.mapNotNull { msg ->
                textValue(msg.get("content")?.get("text"))
            }.joinToString("\n")
        } else {
            textValue(result) ?: result.toString()
        }
    }
}

private fun textValue(node: JsonNode?): String? {
    return node?.takeUnless { it.isNull }?.toString()?.stripQuotes()
}

private fun String.stripQuotes(): String {
    return if (length >= 2 && startsWith('"') && endsWith('"')) {
        substring(1, length - 1)
    } else {
        this
    }
}

