package org.ivcode.aimo.ollama.client

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import java.time.Instant
import kotlin.time.Duration

/* References:
 * - https://github.com/ollama/ollama-js/blob/main/src/interfaces.ts
 * - https://json-schema.org/understanding-json-schema/reference
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ChatRequest (
    val model: String,
    val messages: List<Message>,
    val stream: Boolean? = null,
    @field:JsonProperty("keep_alive")
    @field:JsonSerialize(using = DurationSerializer::class)
    @field:JsonDeserialize(using = DurationDeserializer::class)
    val keepAlive: Duration? = null,
    val tools: List<Tool>? = null,
    @field:JsonProperty("logprobs")
    val logProbs : Boolean? = null,
    @field:JsonProperty("top_logprobs")
    val topLogProbs: Int? = null,
    val options: Options? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Message(
    val role: String,
    val content: String,
    val thinking: String? = null,
    @field:JsonProperty("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @field:JsonProperty("tool_name")
    val toolName: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ToolCall (
    val function: ToolCallFunction
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ToolCallFunction (
    val name: String,
    val arguments: Map<String, Any?>,
)


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Options(
    @field:JsonProperty("numa") val numa: Boolean? = null,
    @field:JsonProperty("num_ctx") val numCtx: Int? = null,
    @field:JsonProperty("num_batch") val numBatch: Int? = null,
    @field:JsonProperty("num_gpu") val numGpu: Int? = null,
    @field:JsonProperty("main_gpu") val mainGpu: Int? = null,
    @field:JsonProperty("low_vram") val lowVram: Boolean? = null,
    @field:JsonProperty("f16_kv") val f16Kv: Boolean? = null,
    @field:JsonProperty("logits_all") val logitsAll: Boolean? = null,
    @field:JsonProperty("vocab_only") val vocabOnly: Boolean? = null,
    @field:JsonProperty("use_mmap") val useMmap: Boolean? = null,
    @field:JsonProperty("use_mlock") val useMlock: Boolean? = null,
    @field:JsonProperty("embedding_only") val embeddingOnly: Boolean? = null,
    @field:JsonProperty("num_thread") val numThread: Int? = null,

    // Runtime options
    @field:JsonProperty("num_keep") val numKeep: Int? = null,
    @field:JsonProperty("seed") val seed: Int? = null,
    @field:JsonProperty("num_predict") val numPredict: Int? = null,
    @field:JsonProperty("top_k") val topK: Int? = null,
    @field:JsonProperty("top_p") val topP: Double? = null,
    @field:JsonProperty("min_p") val minP: Double? = null,
    @field:JsonProperty("tfs_z") val tfsZ: Double? = null,
    @field:JsonProperty("typical_p") val typicalP: Double? = null,
    @field:JsonProperty("repeat_last_n") val repeatLastN: Int? = null,
    @field:JsonProperty("temperature") val temperature: Double? = null,
    @field:JsonProperty("repeat_penalty") val repeatPenalty: Double? = null,
    @field:JsonProperty("presence_penalty") val presencePenalty: Double? = null,
    @field:JsonProperty("frequency_penalty") val frequencyPenalty: Double? = null,
    @field:JsonProperty("mirostat") val mirostat: Int? = null,
    @field:JsonProperty("mirostat_tau") val mirostatTau: Double? = null,
    @field:JsonProperty("mirostat_eta") val mirostatEta: Double? = null,
    @field:JsonProperty("penalize_newline") val penalizeNewline: Boolean? = null,
    @field:JsonProperty("stop") val stop: List<String>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL) // don't serialize nulls
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ChatResponse (
    @field:JsonProperty("model") val model: String,

    @field:JsonProperty("created_at")
    @field:JsonSerialize(using = InstantSerializer::class)
    @field:JsonDeserialize(using = InstantDeserializer::class)
    val createdAt: Instant, // expects an ISO timestamp; register JavaTimeModule with Jackson

    @field:JsonProperty("message") val message: Message,
    @field:JsonProperty("done") val done: Boolean,
    @field:JsonProperty("done_reason") val doneReason: String?,
    @field:JsonProperty("total_duration") val totalDuration: Double?,
    @field:JsonProperty("load_duration") val loadDuration: Double?,
    @field:JsonProperty("prompt_eval_count") val promptEvalCount: Int?,
    @field:JsonProperty("prompt_eval_duration") val promptEvalDuration: Double?,
    @field:JsonProperty("eval_count") val evalCount: Int?,
    @field:JsonProperty("eval_duration") val evalDuration: Double?,
    @field:JsonProperty("logprobs") val logProbs: List<LogProb>?
)

internal data class LogProb (
    val token: String,
    @field:JsonProperty("logprob") val logProb: Int,
    val bytes: List<Int>,
    @field:JsonProperty("top_logprobs") val topLogProbs: List<LogProb>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Tool (
    val type: String = "function",
    val function: Function
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Function (
    val name: String,
    val description: String?,
    val parameters: Parameters
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Parameters (
    val type: Type? = Type.OBJECT,
    val required: List<String> = emptyList(),
    val properties: Map<String, Property>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Property (
    val type: Type,
    val items: Items? = null, // only for arrays
    val description: String? = null,
    val enum: List<String>? = null, // only for strings
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Items (
    val type: Type,
    val enum: List<String>? = null, // only for strings
)

internal enum class Type (@get:JsonValue val text: String) {
    ARRAY ("array"),
    BOOLEAN ("boolean"),
    NULL ("null"),
    INTEGER ("integer"),
    NUMBER ("number"),
    OBJECT ("object"), // only supported for parameters
    STRING ("string"),
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromText(value: String): Type =
            entries.firstOrNull { it.text.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown type: $value")
    }
}