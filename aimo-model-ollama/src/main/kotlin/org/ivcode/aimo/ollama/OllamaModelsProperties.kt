package org.ivcode.aimo.ollama

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "aimo.model")
data class OllamaModelsProperties(
    val ollama: Map<String, OllamaModelProperties> = emptyMap(),
)

data class OllamaModelProperties (
    val baseUrl: String = "http://localhost:11434",
    val primary: Boolean = false,
    val context: OllamaContextProperties = OllamaContextProperties(),
    val options: Map<String, Any> = emptyMap(),
)

data class OllamaContextProperties(
    val size: Int = 8192,
    val excludeThinking: Boolean = false,
)

