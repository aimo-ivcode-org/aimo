package org.ivcode.aimo.ollama.model

import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoChatResponse
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoToolCall
import org.ivcode.aimo.core.model.AimoUsage
import org.ivcode.aimo.core.model.ToolDefinition
import org.ivcode.aimo.ollama.client.ChatRequest
import org.ivcode.aimo.ollama.client.ChatResponse
import org.ivcode.aimo.ollama.client.Function
import org.ivcode.aimo.ollama.client.Items
import org.ivcode.aimo.ollama.client.Message
import org.ivcode.aimo.ollama.client.OllamaChatClient
import org.ivcode.aimo.ollama.client.Options
import org.ivcode.aimo.ollama.client.Parameters
import org.ivcode.aimo.ollama.client.Property
import org.ivcode.aimo.ollama.client.Tool
import org.ivcode.aimo.ollama.client.ToolCall
import org.ivcode.aimo.ollama.client.ToolCallFunction
import org.ivcode.aimo.ollama.client.Type
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.util.UUID

private val mapper = jacksonObjectMapper()
private val schemaMapper = jacksonObjectMapper()

/**
 * [AimoChatEngine] implementation that delegates to [OllamaChatClient].
 *
 * @param client   Pre-configured Ollama HTTP client for the model's host.
 * @param modelName Default Ollama model identifier (overridable via [AimoChatOptions.model]).
 * @param options  Default options applied to every request; per-request options from
 *                 [AimoPrompt.options] are merged on top.
 */
internal class OllamaChatEngineImpl(
    private val client: OllamaChatClient,
    private val modelName: String,
    override val options: AimoChatOptions,
) : AimoChatEngine {

    // -------------------------------------------------------------------------
    // AimoChatEngine
    // -------------------------------------------------------------------------

    override fun call(prompt: AimoPrompt): AimoChatResponse {
        val request = buildRequest(prompt)
        val response = client.chat(request)
        return toAimoChatResponse(response, done = true)
    }

    override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
        val request = buildRequest(prompt, stream = true)
        val response = client.chat(request) { chunk ->
            callback(toAimoChatResponse(chunk, done = chunk.done, messageId = messageId++))
        }
        // Return the final merged response which includes all accumulated tool calls from streaming chunks
        return toAimoChatResponse(response, done = true, messageId = 0)
    }

    // -------------------------------------------------------------------------
    // Request building
    // -------------------------------------------------------------------------

    private fun buildRequest(prompt: AimoPrompt, stream: Boolean? = null): ChatRequest {
        val merged = merge(options, prompt.options)
        return ChatRequest(
            model = merged.model ?: modelName,
            messages = prompt.messages.map { it.toMessage() },
            stream = stream,
            tools = prompt.tools.map { it.toTool() }.takeIf { it.isNotEmpty() },
            options = merged.toOllamaOptions(),
        )
    }

    private fun merge(base: AimoChatOptions, override: AimoChatOptions?): AimoChatOptions {
        override ?: return base
        return base.copy(
            model             = override.model ?: base.model,
            temperature       = override.temperature ?: base.temperature,
            maxTokens         = override.maxTokens ?: base.maxTokens,
            topP              = override.topP ?: base.topP,
            topK              = override.topK ?: base.topK,
            frequencyPenalty  = override.frequencyPenalty ?: base.frequencyPenalty,
            presencePenalty   = override.presencePenalty ?: base.presencePenalty,
            stopSequences     = override.stopSequences.ifEmpty { base.stopSequences },
            providerOptions   = base.providerOptions + override.providerOptions,
        )
    }

    // -------------------------------------------------------------------------
    // Response mapping
    // -------------------------------------------------------------------------

    private fun toAimoChatResponse(
        response: ChatResponse,
        done: Boolean,
        messageId: Int = 0,
    ): AimoChatResponse {
        val msg = response.message

        val toolCalls = msg.toolCalls
            ?.map { tc ->
                AimoToolCall(
                    id = tc.id?.takeIf { it.isNotBlank() }
                        ?: stableToolCallId(tc.function.name, tc.function.arguments),
                    name = tc.function.name,
                    arguments = mapper.writeValueAsString(tc.function.arguments),
                )
            }
            ?.takeIf { it.isNotEmpty() }

        val aimoMessage = AimoChatMessage(
            messageId = messageId,
            type = AimoChatMessageType.ASSISTANT,
            content = msg.content,
            thinking = msg.thinking?.takeIf { it.isNotBlank() },
            toolName = msg.toolName,
            toolCallId = null,
            toolCalls = toolCalls,
            done = done,
        )

        val promptEvalCount = response.promptEvalCount
        val evalCount = response.evalCount

        val usage = if(promptEvalCount != null || evalCount != null) {
            AimoUsage(
                inputTokens = promptEvalCount,
                outputTokens = evalCount,
            )
        } else {
            null
        }

        return AimoChatResponse(
            chatId      = UUID.randomUUID(),
            responseId  = UUID.randomUUID(),
            messages    = listOf(aimoMessage),
            createdAt   = response.createdAt,
            usage       = usage,
        )
    }
}

private fun stableToolCallId(name: String, arguments: Map<String, Any?>): String {
    val canonicalArgs = schemaMapper.writeValueAsString(canonicalizeForHash(arguments))
    val fingerprint = "$name|$canonicalArgs"
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(fingerprint.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "ollama-$hash"
}

private fun canonicalizeForHash(value: Any?): Any? {
    return when (value) {
        is Map<*, *> -> value.entries
            .sortedBy { it.key.toString() }
            .associate { (key, mapValue) -> key.toString() to canonicalizeForHash(mapValue) }
        is List<*> -> value.map { canonicalizeForHash(it) }
        is Array<*> -> value.map { canonicalizeForHash(it) }
        else -> value
    }
}

// =============================================================================
// Extension helpers (file-private)
// =============================================================================

private fun AimoChatMessage.toMessage(): Message {
    val role = when (type) {
        AimoChatMessageType.SYSTEM    -> "system"
        AimoChatMessageType.USER      -> "user"
        AimoChatMessageType.ASSISTANT -> "assistant"
        AimoChatMessageType.TOOL      -> "tool"
    }
    val ollamaToolCalls = toolCalls?.map { tc ->
        ToolCall(
            id = tc.id,
            function = ToolCallFunction(
                name = tc.name,
                arguments = mapper.readValue(tc.arguments, MutableMap::class.java) as Map<String, Any?>
            )
        )
    }
    return Message(
        role      = role,
        content   = content.orEmpty(),
        thinking  = thinking,
        toolCalls = ollamaToolCalls,
        toolName  = toolName,
    )
}

private fun ToolDefinition.toTool(): Tool =
    Tool(function = Function(
        name        = name,
        description = description,
        parameters  = inputSchema.toParameters(),
    ))

/**
 * Convert a `tools.jackson.databind.JsonNode` representing a JSON Schema object
 * into an Ollama [Parameters] instance.
 *
 * We round-trip through `treeToValue` → plain `Map` to avoid fighting with
 * Jackson 3's iterator API at the Kotlin type-inference level.
 */
@Suppress("UNCHECKED_CAST")
private fun JsonNode.toParameters(): Parameters {
    val raw = schemaMapper.treeToValue(this, MutableMap::class.java) as Map<String, Any?>
    val required = (raw["required"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val propertiesRaw = raw["properties"] as? Map<String, Any?> ?: emptyMap()
    val properties = propertiesRaw.mapValues { (_, v) ->
        @Suppress("UNCHECKED_CAST")
        (v as? Map<String, Any?>)?.toProperty() ?: Property(type = Type.STRING)
    }
    return Parameters(type = Type.OBJECT, required = required, properties = properties)
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toProperty(): Property {
    val type     = Type.fromText(this["type"] as? String ?: "string")
    val desc     = this["description"] as? String
    val enumList = (this["enum"] as? List<*>)?.filterIsInstance<String>()
    val itemsMap = this["items"] as? Map<String, Any?>
    val items    = itemsMap?.let {
        Items(
            type = Type.fromText(it["type"] as? String ?: "string"),
            enum = (it["enum"] as? List<*>)?.filterIsInstance<String>(),
        )
    }
    return Property(type = type, description = desc, enum = enumList, items = items)
}

private fun AimoChatOptions.toOllamaOptions(): Options? {
    val hasValues = temperature != null || maxTokens != null || topP != null ||
        topK != null || frequencyPenalty != null || presencePenalty != null ||
        stopSequences.isNotEmpty() || providerOptions.isNotEmpty()
    if (!hasValues) return null
    return Options(
        temperature      = temperature,
        numPredict       = maxTokens,
        topP             = topP,
        topK             = topK,
        frequencyPenalty = frequencyPenalty,
        presencePenalty  = presencePenalty,
        stop             = stopSequences.ifEmpty { null },
        providerOptions  = providerOptions,
    )
}




