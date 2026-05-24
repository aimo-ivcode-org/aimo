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
    /**
     * Enable AWS Bedrock prompt caching for this model.
     *
     * When `true`, a cache-point marker is injected after the system messages on every
     * request. Bedrock will cache the KV state up to that marker and reuse it on
     * subsequent calls, reducing both latency and per-token cost.
     *
     * Do not enable this for models that do not support prompt caching. Requests that
     * include cache-point markers may be rejected with a validation error rather than
     * silently ignoring the feature, so verify model support in the AWS Bedrock
     * documentation before enabling it in production.
     */
    val promptCaching: Boolean = false,

    /**
     * Controls where prompt-cache checkpoints are inserted when [promptCaching] is enabled.
     *
     * - [PromptCachingStrategy.SYSTEM]: add a checkpoint after system prompt blocks.
     * - [PromptCachingStrategy.SYSTEM_AND_TOOLS]: add checkpoints after system blocks and
     *   at the end of tool definitions so stable tool schemas can also be cached.
     */
    val promptCachingStrategy: PromptCachingStrategy = PromptCachingStrategy.SYSTEM,
)

enum class PromptCachingStrategy {
    SYSTEM,
    SYSTEM_AND_TOOLS,
}

