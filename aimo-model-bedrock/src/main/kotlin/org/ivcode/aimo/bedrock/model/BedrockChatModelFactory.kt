package org.ivcode.aimo.bedrock.model

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoToolCall
import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoToolDefinition
import org.ivcode.aimo.bedrock.BedrockModelProperties
import org.ivcode.aimo.bedrock.client.BedrockChatClient
import org.ivcode.aimo.bedrock.client.ContentBlock
import org.ivcode.aimo.bedrock.client.ConverseMessage
import org.ivcode.aimo.bedrock.client.ConverseRequest
import org.ivcode.aimo.bedrock.client.InferenceConfiguration
import org.ivcode.aimo.bedrock.client.InputSchema
import org.ivcode.aimo.bedrock.client.SystemContentBlock
import org.ivcode.aimo.bedrock.client.Tool
import org.ivcode.aimo.bedrock.client.ToolConfiguration
import org.ivcode.aimo.bedrock.client.ToolSpec
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * [AimoChatModelProviderFactory] backed by AWS Bedrock API.
 *
 * One [BedrockChatClient] instance is created per distinct [BedrockModelProperties.region]
 * and shared across all models in that region to reuse the underlying AWS SDK client.
 *
 * @param properties Map of model-name → [BedrockModelProperties] sourced from
 *                   `aimo.model.bedrock.*` configuration.
 */
class BedrockChatModelFactory(
    private val properties: Map<String, BedrockModelProperties>,
) : AimoChatModelProviderFactory {

    override val provider: String = "bedrock"

    /** One client per region. */
    private val clients: Map<String, BedrockChatClient> =
        properties.values
            .map { it.region }
            .distinct()
            .associateWith { region -> BedrockChatClient(region) }

    // -------------------------------------------------------------------------
    // AimoChatModelProviderFactory
    // -------------------------------------------------------------------------

    override fun createAimoChatModel(): AimoChatModel? {
        val name = getPrimaryName() ?: properties.keys.firstOrNull() ?: return null
        return createAimoChatModel(name)
    }

    override fun createAimoChatModel(name: String): AimoChatModel? {
        val props = properties[name]
            ?: return null
        val client = clients[props.region]
            ?: BedrockChatClient(props.region)
        val rawOptions = resolveOptions(name, props.options)
        val aimoOptions = rawOptions.toAimoChatOptions()
        val engine = BedrockChatEngineImpl(client, name, aimoOptions)
        return AimoChatModel(
            name = name,
            chatEngine = engine,
            options = aimoOptions,
            isPrimary = props.primary,
            contextSize = props.contextSize,
        )
    }

    override fun getNames(): List<String> = properties.keys.toList()

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

    /** Ensures `model` is always present in the options map. */
    private fun resolveOptions(name: String, raw: Map<String, Any>): Map<String, Any> {
        if (raw.containsKey("model")) return raw
        return LinkedHashMap(raw).also { it["model"] = name }
    }
}

// =============================================================================
// Option-map → typed-options conversions (file-private)
// =============================================================================

/**
 * Converts a raw Bedrock options map to the provider-agnostic [AimoChatOptions].
 *
 * Keys that have no standard counterpart are collected into [AimoChatOptions.providerOptions].
 */
private fun Map<String, Any>.toAimoChatOptions(): AimoChatOptions {
    var model: String? = null
    var temperature: Double? = null
    var maxTokens: Int? = null
    var topP: Double? = null
    var topK: Int? = null
    var frequencyPenalty: Double? = null
    var presencePenalty: Double? = null
    var stopSequences = emptyList<String>()
    val providerOptions = mutableMapOf<String, Any>()

    forEach { (key, value) ->
        when (key.lowercase().replace("-", "").replace("_", "")) {
            "model" -> model = value.toString()
            "temperature" -> temperature = value.asDouble()
            "maxtokens" -> maxTokens = value.asInt()
            "topp" -> topP = value.asDouble()
            "topk" -> topK = value.asInt()
            "frequencypenalty" -> frequencyPenalty = value.asDouble()
            "presencepenalty" -> presencePenalty = value.asDouble()
            "stop" -> stopSequences = value.asStringList()
            else -> providerOptions[key] = value
        }
    }

    return AimoChatOptions(
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        topK = topK,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        stopSequences = stopSequences,
        providerOptions = providerOptions,
    )
}

// Small type-coercion helpers
private fun Any.asInt(): Int = when (this) {
    is Number -> toInt()
    else -> toString().toInt()
}

private fun Any.asDouble(): Double = when (this) {
    is Number -> toDouble()
    else -> toString().toDouble()
}

@Suppress("UNCHECKED_CAST")
private fun Any.asStringList(): List<String> = when (this) {
    is List<*> -> this as List<String>
    else -> toString().split(",").map { it.trim() }
}

// =============================================================================
// BedrockChatEngineImpl
// =============================================================================

/**
 * [AimoChatEngine] implementation that delegates to [BedrockChatClient].
 */
internal class BedrockChatEngineImpl(
    private val client: BedrockChatClient,
    private val modelName: String,
    override val options: AimoChatOptions,
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
        val response = client.converse(options.model ?: modelName, request)
        callback(toAimoChatResponse(response, done = true))
        return toAimoChatResponse(response, done = true)
    }

    // -------------------------------------------------------------------------
    // Request building
    // -------------------------------------------------------------------------

    private fun buildRequest(prompt: AimoPrompt): ConverseRequest {
        val merged = merge(options, prompt.options)
        return ConverseRequest(
            model = merged.model ?: modelName,
            messages = prompt.messages.map { it.toConverseMessage() },
            system = listOf(SystemContentBlock(text = "You are a helpful assistant.")),
            inferenceConfig = merged.toInferenceConfiguration(),
            toolConfig = prompt.tools.takeIf { it.isNotEmpty() }?.let { tools ->
                ToolConfiguration(tools = tools.map { it.toTool() })
            },
        )
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

        val aimoMessage = AimoChatMessage(
            messageId = messageId,
            type = AimoChatMessageType.ASSISTANT,
            content = textContent,
            thinking = null,
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
        )
    }
}

// =============================================================================
// Extension helpers (file-private)
// =============================================================================

private fun AimoChatMessage.toConverseMessage(): ConverseMessage {
    val role = when (type) {
        AimoChatMessageType.SYSTEM -> "system"
        AimoChatMessageType.USER -> "user"
        AimoChatMessageType.ASSISTANT -> "assistant"
        AimoChatMessageType.TOOL -> "user"
    }
    return ConverseMessage(
        role = role,
        content = listOf(
            ContentBlock(text = content.orEmpty())
        )
    )
}

private fun AimoToolDefinition.toTool(): Tool {
    return Tool(
        toolSpec = ToolSpec(
            name = name,
            description = description,
            inputSchema = InputSchema(json = inputSchema.treeToMap()),
        )
    )
}

private val schemaMapper = jacksonObjectMapper()

@Suppress("UNCHECKED_CAST")
private fun JsonNode.treeToMap(): Map<String, Any?> {
    return schemaMapper.treeToValue(this, MutableMap::class.java) as Map<String, Any?>
}

private fun AimoChatOptions.toInferenceConfiguration(): InferenceConfiguration? {
    val hasValues = temperature != null || maxTokens != null || topP != null ||
        topK != null || stopSequences.isNotEmpty()
    if (!hasValues) return null
    return InferenceConfiguration(
        maxTokens = maxTokens,
        temperature = temperature,
        topP = topP,
        topK = topK,
        stopSequences = stopSequences.ifEmpty { null },
    )
}

