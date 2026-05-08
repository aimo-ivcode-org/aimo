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
 */
interface AimoChatEngine {
    val options: AimoChatOptions
    fun call(prompt: AimoPrompt): AimoChatResponse
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
 * Parsing contract for `argumentsJson`:
 * 1) Parse the raw JSON string into a JSON tree/object.
 * 2) Validate it against [AimoToolDefinition.inputSchema] using [AimoToolDefinition.schemaDialect]
 *    (or `$schema` inside the schema when present).
 * 3) Only after successful validation, bind/coerce into typed arguments.
 *
 * Keep this contract strict so future implementations in other modules or chat contexts
 * can safely reuse the same schema metadata and avoid divergent parsing behavior.
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
 * chat sessions, because downstream code can serialize/validate the same schema document
 * without lossy type conversions.
 */
data class AimoToolDefinition (
    val name: String,
    val description: String? = null,
    val inputSchema: JsonNode,
    val schemaDialect: String = DEFAULT_JSON_SCHEMA_DIALECT
)
