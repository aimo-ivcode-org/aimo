package org.ivcode.aimo.ollama

import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.ollama.model.OllamaChatModelFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OllamaModelsProperties::class)
class OllamaConfig {

    @Bean("ollama")
    fun createOllamaChatModelFactory(
        properties: OllamaModelsProperties,
    ): AimoChatModelProviderFactory = OllamaChatModelFactory(properties.ollama)
}
