package org.ivcode.aimo.bedrock.model

import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.bedrock.client.InferenceConfiguration

internal fun Map<String, Any>.toAimoChatOptions(): AimoChatOptions {
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
        when (key.normalizedOptionKey()) {
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

private fun Any.asInt(): Int = when (this) {
    is Number -> toInt()
    else -> toString().toInt()
}

private fun Any.asDouble(): Double = when (this) {
    is Number -> toDouble()
    else -> toString().toDouble()
}

private fun Any.asStringList(): List<String> = when (this) {
    is List<*> -> this.mapNotNull { item -> item?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
    else -> toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

internal fun AimoChatOptions.toInferenceConfiguration(): InferenceConfiguration? {
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

internal fun AimoChatOptions.additionalModelRequestFields(): Map<String, Any?>? {
    val merged = LinkedHashMap<String, Any?>()
    providerOptions.additionalModelRequestFieldsSources().firstNotNullOfOrNull { it }?.let {
        merged.putAll(it)
    }

    if (frequencyPenalty != null && merged.keys.none { it.normalizedOptionKey() == "frequencypenalty" }) {
        merged["frequency_penalty"] = frequencyPenalty
    }
    if (presencePenalty != null && merged.keys.none { it.normalizedOptionKey() == "presencepenalty" }) {
        merged["presence_penalty"] = presencePenalty
    }

    return merged.takeIf { it.isNotEmpty() }
}

private fun Map<String, Any>.additionalModelRequestFieldsSources(): List<Map<String, Any?>?> =
    listOf(
        this["additionalModelRequestFields"].asStringKeyedMap(),
        this["additional-model-request-fields"].asStringKeyedMap(),
        this["additional_model_request_fields"].asStringKeyedMap(),
    )

private fun Any?.asStringKeyedMap(): Map<String, Any?>? = when (this) {
    is Map<*, *> -> this.entries.associate { it.key.toString() to it.value }
    else -> null
}

internal fun org.ivcode.aimo.bedrock.client.Usage.hasMeaningfulValues(): Boolean =
    inputTokens != null ||
        outputTokens != null ||
        cacheReadInputTokens != 0 ||
        cacheWriteInputTokens != 0

private fun String.normalizedOptionKey(): String =
    lowercase().replace("-", "").replace("_", "")
