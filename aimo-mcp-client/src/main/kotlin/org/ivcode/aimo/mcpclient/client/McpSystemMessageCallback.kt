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
    private val log = LoggerFactory.getLogger(javaClass)

    fun getScopes(): Set<String> = scopes

    fun createCallback(promptDefinition: PromptDefinition): SystemMessageCallback {
        val namespacedName = "$serverId:${promptDefinition.name}"
        return McpSystemMessageCallback(
            namespacedName = namespacedName,
            serverId = serverId,
            promptName = promptDefinition.name,
            description = promptDefinition.description,
            argumentSchema = promptDefinition.argumentSchema,
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
    private val namespacedName: String,
    private val serverId: String,
    private val promptName: String,
    private val description: String?,
    private val argumentSchema: JsonNode?,
    private val protocolClient: org.ivcode.aimo.mcpclient.protocol.ProtocolClient?,
    private val objectMapper: ObjectMapper,
    override val scopes: Set<String>,
) : SystemMessageCallback {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = namespacedName

    override fun call(context: SystemMessageContext): String? {
        return try {
            if (protocolClient == null) {
                log.warn("System message callback skipped: Server '$serverId' is not yet initialized")
                return null
            }

            // Build the prompts/get request
            val params = objectMapper.createObjectNode().apply {
                put("name", promptName)

                // Check if caller provided arguments for this prompt
                val argsKey = "$serverId:$promptName:args"
                val args = context.context[argsKey]
                if (args != null) {
                    set("arguments", objectMapper.valueToTree(args))
                }
            }

            val response = protocolClient.sendRequest("prompts/get", params)

            if (response.error != null) {
                log.error("Prompt call failed: serverId=$serverId prompt=$promptName error=${response.error.message}")
                return null
            }

            val result = response.result ?: run {
                log.warn("Prompt execution returned no result: serverId=$serverId prompt=$promptName")
                return null
            }

            // MCP prompts/get returns { messages: [...] } where each message has text content
            val messages = result.get("messages")
            if (messages != null && messages.isArray) {
                messages.mapNotNull { msg ->
                    msg.get("content")?.get("text")?.asText()
                }.joinToString("\n")
            } else {
                result.asText()
            }
        } catch (e: Exception) {
            log.error("System message callback execution failed: serverId=$serverId prompt=$promptName", e)
            null
        }
    }
}

