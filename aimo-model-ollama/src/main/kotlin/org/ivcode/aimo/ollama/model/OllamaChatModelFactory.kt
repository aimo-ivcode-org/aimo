package org.ivcode.aimo.ollama.model

import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.ollama.OllamaModelProperties
import org.ivcode.aimo.ollama.client.OllamaChatClient

/**
 * [AimoChatModelProviderFactory] backed by Ollama's native HTTP API.
 *
 * One [OllamaChatClient] instance is created per distinct [OllamaModelProperties.baseUrl]
 * and shared across all models on that host to reuse the underlying [java.net.http.HttpClient].
 *
 * @param properties Map of model-name → [OllamaModelProperties] sourced from
 *                   `aimo.model.ollama.*` configuration.
 */
class OllamaChatModelFactory (
    private val properties: Map<String, OllamaModelProperties>,
) : AimoChatModelProviderFactory {

    override val provider: String = "ollama"

    /** One HTTP client per base URL. */
    private val clients: Map<String, OllamaChatClient> =
        properties.values
            .map { it.baseUrl }
            .distinct()
            .associateWith { baseUrl -> OllamaChatClient(baseUrl) }

    // -------------------------------------------------------------------------
    // AimoChatModelProviderFactory
    // -------------------------------------------------------------------------

    override fun createAimoChatModel(): AimoChatModel? {
        val name = getPrimaryName() ?: properties.keys.firstOrNull() ?: return null
        return createAimoChatModel(name)
    }

    override fun createAimoChatModel(name: String): AimoChatModel? {
        val props  = properties[name]
            ?: return null
        val client = clients[props.baseUrl]
            ?: OllamaChatClient(props.baseUrl)
        val rawOptions = resolveOptions(name, props.options)
        val aimoOptions  = rawOptions.toAimoChatOptions()
        val engine = OllamaChatEngineImpl(client, name, aimoOptions)
        return AimoChatModel(
            name        = name,
            chatEngine  = engine,
            options     = aimoOptions,
            isPrimary   = props.primary,
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
            "Only one Ollama model can be marked primary=true. Found: $primaryNames"
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
 * Converts a raw Ollama options map (as stored in [OllamaModelProperties.options])
 * to the provider-agnostic [AimoChatOptions].
 *
 * Keys that have no standard counterpart are collected into [AimoChatOptions.providerOptions].
 */
private fun Map<String, Any>.toAimoChatOptions(): AimoChatOptions {
    var model: String?            = null
    var temperature: Double?      = null
    var maxTokens: Int?           = null
    var topP: Double?             = null
    var topK: Int?                = null
    var frequencyPenalty: Double? = null
    var presencePenalty: Double?  = null
    var stopSequences             = emptyList<String>()
    val providerOptions           = mutableMapOf<String, Any>()

    forEach { (key, value) ->
        when (key.lowercase().replace("-", "").replace("_", "")) {
            "model"            -> model            = value.toString()
            "temperature"      -> temperature      = value.asDouble()
            "numpredict",
            "maxtokens"        -> maxTokens        = value.asInt()
            "topp"             -> topP             = value.asDouble()
            "topk"             -> topK             = value.asInt()
            "frequencypenalty" -> frequencyPenalty = value.asDouble()
            "presencepenalty"  -> presencePenalty  = value.asDouble()
            "stop"             -> stopSequences    = value.asStringList()
            else               -> providerOptions[key] = value
        }
    }

    return AimoChatOptions(
        model            = model,
        temperature      = temperature,
        maxTokens        = maxTokens,
        topP             = topP,
        topK             = topK,
        frequencyPenalty = frequencyPenalty,
        presencePenalty  = presencePenalty,
        stopSequences    = stopSequences,
        providerOptions  = providerOptions,
    )
}

// Small type-coercion helpers
private fun Any.asInt(): Int = when (this) {
    is Number -> toInt()
    else      -> toString().toInt()
}
private fun Any.asDouble(): Double = when (this) {
    is Number -> toDouble()
    else      -> toString().toDouble()
}
@Suppress("UNCHECKED_CAST")
private fun Any.asStringList(): List<String> = when (this) {
    is List<*> -> this as List<String>
    else       -> toString().split(",").map { it.trim() }
}




