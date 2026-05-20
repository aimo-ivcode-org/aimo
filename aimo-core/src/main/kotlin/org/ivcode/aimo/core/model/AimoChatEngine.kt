package org.ivcode.aimo.core.model

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.controller.SystemMessageCallback
import tools.jackson.databind.JsonNode

/**
 * Aimo-owned abstraction for chat model execution.
 *
 * This forms the provider-agnostic execution seam between Aimo core and concrete
 * model integrations. Callers should depend only on these Aimo-owned contracts.
 *
 * ## Token Usage in Responses
 * Both `call` methods return an [AimoChatResponse] where [AimoChatResponse.usage]
 * represents the total token usage across the entire exchange:
 * - The `usage` field contains the sum of all tokens consumed across every model invocation
 * - This includes all tool call invocations in multi-turn scenarios
 * - For single-turn calls, usage reflects just that single invocation
 * - The [AimoChatResponse.messages] field contains all messages generated during the complete exchange
 */
interface AimoChatEngine {
    val options: AimoChatOptions

    /**
     * Non-streaming chat execution.
     *
     * Blocks until the complete exchange is finished and returns a single response.
     * Any multi-turn exchanges (e.g., tool calls) occur internally and are not
     * visible to the caller until the final response is returned.
     *
     * @param prompt The chat prompt with messages, tools, and options
     * @return Response with all messages generated and total token usage across the entire exchange
     */
    fun call(prompt: AimoPrompt): AimoChatResponse

    /**
     * Streaming chat execution with incremental callback updates.
     *
     * Invokes the callback incrementally as the response is generated. Callbacks receive
     * streaming updates (not accumulated state); they are useful for displaying progress
     * to the caller.
     *
     * The final returned [AimoChatResponse] is a complete accumulation of the entire exchange:
     * - [AimoChatResponse.messages] contains all generated messages
     * - [AimoChatResponse.usage] contains the total tokens consumed (sum of all tokens
     *   across every model invocation, including tool calls in multi-turn scenarios)
     *
     * @param prompt The chat prompt with messages, tools, and options
     * @param callback Invoked for each incremental streaming update; use for progress/display only
     * @return Final accumulated response with all messages and total token usage
     */
    fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse
}

/**
 * Provider-agnostic chat tuning options.
 *
 * Expressed as a Kotlin data class so it is easy to serialize, copy, merge,
 * and document.
 *
 * Notes for future adapters and parsing code:
 * - Every property is nullable so an option may be omitted and resolved by the
 *   backing provider or model default.
 * - [model] identifies the target model name when a provider supports runtime
 *   model selection.
 * - [stopSequences] should be forwarded in order, preserving duplicates only if
 *   the downstream provider explicitly supports them.
 * - [providerOptions] is an escape hatch for provider-specific flags while the
 *   portable surface area is still evolving.
 */
data class AimoChatOptions (
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stopSequences: List<String> = emptyList(),
    val providerOptions: Map<String, Any> = emptyMap(),
)

/**
 * Provider-agnostic prompt contract used by Aimo chat engines.
 *
 * Ordered chat messages are the single required input.
 *
 * [options] allows per-request overrides of model tuning parameters. When it is
 * `null`, callers are deferring entirely to the model's configured defaults.
 */
data class AimoPrompt (
    val tools: List<AimoToolDefinition> = emptyList(),
    val systemMessages: List<SystemMessageCallback> = emptyList(),
    val options: AimoChatOptions? = null,
    val messages: List<AimoChatMessage>,
)

/**
 * Aimo-owned tool callback contract for runtime tool invocation.
 *
 * Current parsing contract for `argumentsJson`:
 * 1) Parse the raw JSON string into a JSON tree/object.
 * 2) Bind/coerce values into typed arguments for the callback implementation.
 * 3) Fail fast for binding/runtime issues (for example missing required arguments,
 *    nullability violations, or type conversion errors).
 *
 * Note: JSON Schema validation against [AimoToolDefinition.inputSchema] and
 * [AimoToolDefinition.schemaDialect] is not currently enforced by this interface.
 * Implementations may add validation, but callers must not assume schema validation
 * occurs unless explicitly documented by the concrete implementation.
 */
interface AimoToolCallback {
    val toolDefinition: AimoToolDefinition
    fun call(argumentsJson: String, context: Map<String, Any>): String
}

/**
 * Default JSON Schema dialect used when a tool definition does not specify a more
 * specific `$schema` URI in its schema document.
 */
const val DEFAULT_JSON_SCHEMA_DIALECT: String = "https://json-schema.org/draft/2020-12/schema"

/**
 * Immutable tool metadata used for model-side tool declarations and runtime validation.
 *
 * [inputSchema] uses Jackson's [JsonNode] tree type (`tools.jackson.databind.JsonNode`).
 * `JsonNode` is a structured in-memory JSON document model (object/array/value nodes),
 * not a Kotlin map wrapper, so the schema is preserved exactly as JSON.
 *
 * Storing schemas as [JsonNode] keeps this portable across providers and stable across
 * conversations, because downstream code can serialize/validate the same schema document
 * without lossy type conversions.
 */
data class AimoToolDefinition (
    val name: String,
    val description: String? = null,
    val inputSchema: JsonNode,
    val schemaDialect: String = DEFAULT_JSON_SCHEMA_DIALECT
)
