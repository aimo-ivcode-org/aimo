package org.ivcode.aimo.core.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Root configuration properties for Aimo under the `aimo.*` prefix.
 *
 * Properties structure:
 * ```yaml
 * aimo:
 *   data-dir: ./data/conversations
 *   global-user-id: default-user
 *   model:
 *     # Model configurations (handled by provider-specific properties in adapter modules)
 *     # e.g., aimo.model.ollama.*, aimo.model.bedrock.*
 *   agents:
 *     # Agent configurations (Phase 2)
 *   guard-rails:
 *     # Guard-rail configurations (Phase 7)
 *   interceptors:
 *     # Interceptor configurations
 * ```
 */
@ConfigurationProperties(prefix = "aimo")
@Validated
data class AimoProperties(
    /**
     * Directory for conversation storage (file-based DAO).
     * Default: ./data/conversations
     */
    var dataDir: String = "./data/conversations",

    /**
     * Global user ID for single-user mode.
     * When set, all conversations are owned by this user.
     * Default: "default-user"
     */
    var globalUserId: String? = "default-user",

    /**
     * Agent configurations (Phase 2 feature).
     * Maps agent ID → agent configuration.
     */
    var agents: Map<String, AimoAgentProperties> = emptyMap(),

    /**
     * Guard-rail configurations (Phase 7 feature).
     * Maps guard-rail ID → guard-rail configuration.
     */
    var guardRails: Map<String, AimoGuardRailProperties> = emptyMap(),

    /**
     * Interceptor configurations.
     */
    var interceptors: AimoInterceptorProperties = AimoInterceptorProperties()
)

/**
 * Agent configuration properties (Phase 2 feature).
 *
 * Structure:
 * ```yaml
 * aimo.agents:
 *   default:
 *     name: "Default Agent"
 *     description: "General-purpose assistant"
 *     system-message: "You are a helpful assistant."
 *     tools: ["*"]  # All tools
 *   calculator:
 *     name: "Calculator"
 *     description: "Math-focused agent"
 *     system-message: "You are a math expert."
 *     tools: ["calculate", "convert_units"]
 * ```
 */
data class AimoAgentProperties(
    /**
     * Human-readable agent name.
     */
    var name: String = "",

    /**
     * Agent description.
     */
    var description: String = "",

    /**
     * System message for this agent.
     */
    var systemMessage: String? = null,

    /**
     * Tool scoping: list of tool names available to this agent.
     * Special value "*" means all tools.
     * Empty list means no tools.
     */
    var tools: List<String> = listOf("*"),

    /**
     * System message scoping: list of system message names available to this agent.
     * Special value "*" means all system messages.
     * Empty list means no system messages (except agent's own systemMessage).
     */
    var systemMessages: List<String> = listOf("*")
)

/**
 * Guard-rail configuration properties (Phase 7 feature).
 *
 * Structure:
 * ```yaml
 * aimo.guard-rails:
 *   content-filter:
 *     enabled: true
 *     type: "content-filter"
 *     model: "guard-rail-model"  # Lightweight model for guard-rails
 *     rules:
 *       - "no-profanity"
 *       - "no-pii"
 * ```
 */
data class AimoGuardRailProperties(
    /**
     * Whether this guard-rail is enabled.
     */
    var enabled: Boolean = true,

    /**
     * Guard-rail type (e.g., "content-filter", "prompt-injection-detector").
     */
    var type: String = "",

    /**
     * Optional model to use for guard-rail validation.
     * Typically a lightweight/fast model.
     */
    var model: String? = null,

    /**
     * Guard-rail specific configuration.
     */
    var rules: Map<String, Any> = emptyMap()
)

/**
 * Interceptor configuration properties.
 *
 * Structure:
 * ```yaml
 * aimo.interceptors:
 *   logging:
 *     enabled: false  # disabled by default
 *     level: INFO
 *   tracing:
 *     enabled: false  # disabled by default
 *     service-name: aimo-chat
 *   error-handling:
 *     enabled: false  # disabled by default
 *     max-retries: 3
 *     retry-backoff-ms: 100
 * ```
 */
data class AimoInterceptorProperties(
    var logging: LoggingInterceptorProperties = LoggingInterceptorProperties(),
    var tracing: TracingInterceptorProperties = TracingInterceptorProperties(),
    var errorHandling: ErrorHandlingInterceptorProperties = ErrorHandlingInterceptorProperties()
)

data class LoggingInterceptorProperties(
    var enabled: Boolean = false,
    var level: String = "INFO"
)

data class TracingInterceptorProperties(
    var enabled: Boolean = false,
    var serviceName: String = "aimo-chat"
)

data class ErrorHandlingInterceptorProperties(
    var enabled: Boolean = false,
    var maxRetries: Int = 3,
    var retryBackoffMs: Long = 100
)
