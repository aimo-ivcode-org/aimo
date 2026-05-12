package org.ivcode.aimo.bedrock

import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.bedrock.model.BedrockChatModelFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(BedrockModelsProperties::class)
class BedrockConfig {

    @Bean("bedrock")
    fun createBedrockChatModelFactory(
        properties: BedrockModelsProperties,
    ): AimoChatModelProviderFactory = BedrockChatModelFactory(properties.bedrock)
}

