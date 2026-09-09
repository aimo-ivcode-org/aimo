package org.ivcode.aimo.bedrock.model

import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatContext
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.bedrock.BedrockModelProperties
import org.ivcode.aimo.bedrock.PromptCachingStrategy
import org.ivcode.aimo.bedrock.client.BedrockChatClient
import org.ivcode.aimo.bedrock.client.ContentBlock
import org.ivcode.aimo.bedrock.client.ConverseMessage
import org.ivcode.aimo.bedrock.client.ConverseRequest
import org.ivcode.aimo.bedrock.client.Tool
import org.ivcode.aimo.bedrock.client.ToolConfiguration
import org.ivcode.aimo.bedrock.client.ToolSpec
import org.ivcode.aimo.bedrock.client.ToolUse
import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoChatResponse
import org.ivcode.aimo.core.model.AimoPromptCacheUsage
import org.ivcode.aimo.core.model.AimoToolCall
import org.ivcode.aimo.core.model.AimoUsage
import org.ivcode.aimo.core.model.ToolDefinition
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

private val schemaMapper = jacksonObjectMapper()

/**
 * [AimoChatModelProviderFactory] backed by AWS Bedrock API.
 *
 * One [BedrockChatClient] instance is created per distinct (region, credential-key) tuple.
 * - If explicit credentials (awsAccessKeyId/awsSecretAccessKey) are provided, they must
 *   both be present or both be absent (null).
 * - If credentials are omitted, the AWS SDK default credential chain is used.
 * - Models sharing the same region and credential configuration reuse the same client.
 *
 * @param properties Map of model-name → [BedrockModelProperties] sourced from
 *                   `aimo.model.bedrock.*` configuration.
 * @throws IllegalArgumentException if credentials are partially specified (only one of
 *                                  awsAccessKeyId or awsSecretAccessKey is set).
 */
class BedrockChatModelFactory(
    private val properties: Map<String, BedrockModelProperties>,
) : AimoChatModelProviderFactory {

    override val provider: String = "bedrock"

    /**
     * One client per (region, normalized credential identity) pair.
     *
     * Credential identity is:
     * - `default` when both values are blank/null and AWS SDK default chain is used.
     * - `(accessKeyId, secretFingerprint)` when explicit static credentials are configured.
     */
    private val clients: Map<ClientPoolKey, BedrockChatClient> =
        properties.values
            .map { props -> props to normalizeCredentials(props) }
            .distinctBy { (props, creds) -> clientPoolKey(props.region, creds) }
            .associate { (props, creds) ->
                clientPoolKey(props.region, creds) to BedrockChatClient(
                    region = props.region,
                    awsAccessKeyId = creds.awsAccessKeyId,
                    awsSecretAccessKey = creds.awsSecretAccessKey,
                )
            }

    // -------------------------------------------------------------------------
    // AimoChatModelProviderFactory
    // -------------------------------------------------------------------------

    override fun getDefaultModel(): AimoChatModelConfig? {
        val name = getPrimaryName() ?: properties.keys.firstOrNull() ?: return null
        return getModel(name)
    }

    override fun getModel(name: String): AimoChatModelConfig? {
        val props = properties[name]
            ?: return null
        val normalizedCredentials = normalizeCredentials(props)
        val clientKey = clientPoolKey(props.region, normalizedCredentials)
        val client = clients[clientKey]
            ?: BedrockChatClient(
                region = props.region,
                awsAccessKeyId = normalizedCredentials.awsAccessKeyId,
                awsSecretAccessKey = normalizedCredentials.awsSecretAccessKey,
            )
        val rawOptions = resolveOptions(name, props.options)
        val aimoOptions = rawOptions.toAimoChatOptions()
        val engine = BedrockChatEngineImpl(
            client = client,
            modelName = name,
            options = aimoOptions,
            promptCaching = props.context.promptCaching,
            promptCachingStrategy = props.context.promptCachingStrategy,
        )
        return AimoChatModelConfig(
            name = name,
            chatEngine = engine,
            isPrimary = props.primary,
            context = AimoChatContext(
                size = props.context.size,
                excludeThinking = props.context.excludeThinking,
            ),
        )
    }

    override fun getNames(): List<String> = properties.keys.toList()

    override fun getModels(): List<AimoChatModelConfig> {
        return getNames().mapNotNull { name ->
            getModel(name) ?: throw IllegalStateException(
                "Bedrock provider reported model name '$name' via getNames() " +
                    "but getModel('$name') returned null. This indicates a configuration inconsistency."
            )
        }
    }

    override fun getPrimaryName(): String? {
        val primaryNames = properties
            .filterValues { it.primary }
            .keys
            .toList()
        require(primaryNames.size <= 1) {
            "Only one Bedrock model can be marked primary=true. Found: $primaryNames"
        }
        return primaryNames.firstOrNull()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Trims credential values, normalizes blanks to null, and validates all-or-none semantics.
     */
    private fun normalizeCredentials(props: BedrockModelProperties): NormalizedCredentials {
        val accessKeyId = props.awsAccessKeyId?.trim().orEmpty().ifBlank { null }
        val secretAccessKey = props.awsSecretAccessKey?.trim().orEmpty().ifBlank { null }

        val hasKeyId = accessKeyId != null
        val hasSecret = secretAccessKey != null
        if (hasKeyId != hasSecret) {
            throw IllegalArgumentException(
                "Bedrock model credentials must be fully specified or omitted entirely. " +
                    "Region: ${props.region}, " +
                    "awsAccessKeyId present: $hasKeyId, " +
                    "awsSecretAccessKey present: $hasSecret. " +
                    "Either provide both or neither to use the AWS SDK default credential chain."
            )
        }

        return NormalizedCredentials(
            awsAccessKeyId = accessKeyId,
            awsSecretAccessKey = secretAccessKey,
        )
    }

    private fun clientPoolKey(region: String, credentials: NormalizedCredentials): ClientPoolKey {
        val secretFingerprint = credentials.awsSecretAccessKey?.sha256HexPrefix()
        return ClientPoolKey(
            region = region,
            accessKeyId = credentials.awsAccessKeyId,
            secretFingerprint = secretFingerprint,
        )
    }

    private fun String.sha256HexPrefix(length: Int = 12): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.take(length)
    }

    private data class NormalizedCredentials(
        val awsAccessKeyId: String?,
        val awsSecretAccessKey: String?,
    )

    private data class ClientPoolKey(
        val region: String,
        val accessKeyId: String?,
        val secretFingerprint: String?,
    )

    /** Ensures `model` is always present in the options map. */
    private fun resolveOptions(name: String, raw: Map<String, Any>): Map<String, Any> {
        if (raw.containsKey("model")) return raw
        return LinkedHashMap(raw).also { it["model"] = name }
    }
}

/**
 * [AimoChatEngine] implementation that delegates to [BedrockChatClient].
 */
internal class BedrockChatEngineImpl(
    private val client: BedrockChatClient,
    private val modelName: String,
    override val options: AimoChatOptions,
    private val promptCaching: Boolean = false,
    private val promptCachingStrategy: PromptCachingStrategy = PromptCachingStrategy.SYSTEM,
) : AimoChatEngine {

    private val mapper = jacksonObjectMapper()

    // -------------------------------------------------------------------------
    // AimoChatEngine
    // -------------------------------------------------------------------------

    override fun call(prompt: AimoPrompt): AimoChatResponse {
        val request = buildRequest(prompt)
        val response = client.converse(options.model ?: modelName, request)
        return toAimoChatResponse(response, done = true)
    }

    override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
        val request = buildRequest(prompt)
        var messageId = 0
        val response = client.converseStream(options.model ?: modelName, request) { chunk ->
            callback(toAimoChatResponse(chunk, done = false, messageId = messageId++))
        }
        return toAimoChatResponse(response, done = true)
    }


    // -------------------------------------------------------------------------
    // Request building
    // -------------------------------------------------------------------------

    private fun buildRequest(prompt: AimoPrompt): ConverseRequest {
        val merged = merge(options, prompt.options)
        val cacheAfterSystem = promptCaching && systemCacheEnabled()
        val cacheAfterTools = promptCaching && toolSchemaCacheEnabled()
        val systemBlocks = prompt.messages
            .asSequence()
            .filter { it.type == AimoChatMessageType.SYSTEM }
            .mapNotNull { msg ->
                msg.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { org.ivcode.aimo.bedrock.client.SystemContentBlock(text = it) }
            }
            .toList()

        val conversationMessages = prompt.messages
            .asSequence()
            .filter { it.type != AimoChatMessageType.SYSTEM }
            .mapNotNull { it.toConverseMessageOrNull() }
            .toList()

        return ConverseRequest(
            model = merged.model ?: modelName,
            messages = conversationMessages,
            system = systemBlocks.ifEmpty { null },
            inferenceConfig = merged.toInferenceConfiguration(),
            toolConfig = prompt.tools.takeIf { it.isNotEmpty() }?.let { tools ->
                ToolConfiguration(tools = tools.map { it.toTool() })
            },
            additionalModelRequestFields = merged.additionalModelRequestFields(),
            cachePointAfterSystem = cacheAfterSystem && systemBlocks.isNotEmpty(),
            cachePointAfterTools = cacheAfterTools && prompt.tools.isNotEmpty(),
        )
    }

    private fun systemCacheEnabled(): Boolean {
        return promptCachingStrategy == PromptCachingStrategy.SYSTEM ||
            promptCachingStrategy == PromptCachingStrategy.SYSTEM_AND_TOOLS
    }

    private fun toolSchemaCacheEnabled(): Boolean {
        return promptCachingStrategy == PromptCachingStrategy.SYSTEM_AND_TOOLS
    }

    private fun merge(base: AimoChatOptions, override: AimoChatOptions?): AimoChatOptions {
        override ?: return base
        return base.copy(
            model = override.model ?: base.model,
            temperature = override.temperature ?: base.temperature,
            maxTokens = override.maxTokens ?: base.maxTokens,
            topP = override.topP ?: base.topP,
            topK = override.topK ?: base.topK,
            frequencyPenalty = override.frequencyPenalty ?: base.frequencyPenalty,
            presencePenalty = override.presencePenalty ?: base.presencePenalty,
            stopSequences = override.stopSequences.ifEmpty { base.stopSequences },
            providerOptions = base.providerOptions + override.providerOptions,
        )
    }

    // -------------------------------------------------------------------------
    // Response mapping
    // -------------------------------------------------------------------------

    private fun toAimoChatResponse(
        response: org.ivcode.aimo.bedrock.client.ConverseResponse,
        done: Boolean,
        messageId: Int = 0,
    ): AimoChatResponse {
        val msg = response.output.message

        val toolCalls = msg.content
            .mapNotNull { cb ->
                cb.toolUse?.let { tu ->
                    AimoToolCall(
                        id = tu.toolUseId,
                        name = tu.name,
                        arguments = mapper.writeValueAsString(tu.input),
                    )
                }
            }
            .takeIf { it.isNotEmpty() }

        val textContent = msg.content
            .mapNotNull { it.text }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        val thinkingContent = msg.content
            .mapNotNull { it.reasoning }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

        val aimoMessage = AimoChatMessage(
            messageId = messageId,
            type = AimoChatMessageType.ASSISTANT,
            content = textContent,
            thinking = thinkingContent,
            toolName = null,
            toolCallId = null,
            toolCalls = toolCalls,
            done = done,
        )

        return AimoChatResponse(
            chatId = UUID.randomUUID(),
            responseId = UUID.randomUUID(),
            messages = listOf(aimoMessage),
            createdAt = Instant.now(),
            usage = if (done) buildUsage(response.usage) else null,
        )
    }

    private fun buildUsage(usage: org.ivcode.aimo.bedrock.client.Usage?): AimoUsage? =
        usage?.takeIf { it.hasMeaningfulValues() }?.let { resolvedUsage ->
            val cacheRead = resolvedUsage.cacheReadInputTokens
            val cacheWrite = resolvedUsage.cacheWriteInputTokens

            AimoUsage(
                inputTokens = resolvedUsage.inputTokens,
                outputTokens = resolvedUsage.outputTokens,
                promptCache = if (cacheRead > 0 || cacheWrite > 0) {
                    AimoPromptCacheUsage(
                        cacheReadInputTokens = cacheRead,
                        cacheWriteInputTokens = cacheWrite,
                    )
                } else null,
            )
        }
}

private fun AimoChatMessage.toConverseMessageOrNull(): ConverseMessage? {
    val role = when (type) {
        AimoChatMessageType.SYSTEM -> throw IllegalArgumentException(
            "SYSTEM messages must be mapped to ConverseRequest.system and not request.messages"
        )
        AimoChatMessageType.USER -> "user"
        AimoChatMessageType.ASSISTANT -> "assistant"
        AimoChatMessageType.TOOL -> "user"
    }

    val blocks = when (type) {
        AimoChatMessageType.TOOL -> toolResultBlocks()
        AimoChatMessageType.ASSISTANT -> assistantBlocks()
        else -> userTextBlocks()
    }

    return if (blocks.isEmpty()) null else ConverseMessage(role = role, content = blocks)
}

private fun AimoChatMessage.toolResultBlocks(): List<ContentBlock> =
    toolCallId?.takeIf { it.isNotBlank() }?.let { toolUseId ->
        val resultContent = content?.takeIf { it.isNotBlank() }
            ?.let { listOf(ContentBlock(text = it)) }
            ?: emptyList()

        listOf(
            ContentBlock(
                toolResult = org.ivcode.aimo.bedrock.client.ToolResult(
                    toolUseId = toolUseId,
                    content = resultContent,
                ),
            ),
        )
    } ?: emptyList()

private fun AimoChatMessage.assistantBlocks(): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    content?.takeIf { it.isNotBlank() }?.let { blocks += ContentBlock(text = it) }
    toolCalls.orEmpty().forEach { call ->
        blocks += ContentBlock(
            toolUse = ToolUse(
                toolUseId = call.id,
                name = call.name,
                input = call.arguments.toJsonMap(),
            ),
        )
    }
    return blocks
}

private fun AimoChatMessage.userTextBlocks(): List<ContentBlock> =
    content?.takeIf { it.isNotBlank() }
        ?.let { listOf(ContentBlock(text = it)) }
        ?: emptyList()

private fun String.toJsonMap(): Map<String, Any?> =
    try {
        schemaMapper.readValue(this, object : TypeReference<Map<String, Any?>>() {})
    } catch (_: IOException) {
        mapOf("raw" to this)
    }

private fun ToolDefinition.toTool(): Tool {
    return Tool(
        toolSpec = ToolSpec(
            name = name,
            description = description,
            inputSchema = org.ivcode.aimo.bedrock.client.InputSchema(json = inputSchema.treeToMap()),
        )
    )
}

private fun JsonNode.treeToMap(): Map<String, Any?> =
    runCatching {
        schemaMapper.treeToValue(this, object : TypeReference<Map<String, Any?>>() {})
    }.getOrElse { emptyMap() }
