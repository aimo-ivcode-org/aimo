package org.ivcode.aimo.bedrock

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "aimo.model")
data class BedrockModelsProperties(
    val bedrock: Map<String, BedrockModelProperties> = emptyMap(),
)

data class BedrockModelProperties(
    val region: String = "us-east-1",
    val primary: Boolean = false,
    val context: BedrockContextProperties = BedrockContextProperties(),
    val options: Map<String, Any> = emptyMap(),
    val awsAccessKeyId: String? = null,
    val awsSecretAccessKey: String? = null,
)

data class BedrockContextProperties(
    val size: Int = 8192,
    val excludeThinking: Boolean = false,
)

