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
     * Default: "global" (matches GlobalUserProvider default).
     */
    var globalUserId: String? = "global",

    /**
     * ChatScope configurations (Phase 2 feature).
     * Maps scope ID → scope configuration.
     */
    var scope: Map<String, AimoChatScopeProperties> = emptyMap(),

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
 * ChatScope configuration properties (Phase 2 feature).
 *
 * Semantics:
 * - A ChatService without scope restrictions is available to all scopes
 * - A ChatService with @ChatService(scope=["admin"]) is only in admin scope
 * - Empty tool-refs: this scope has NO tools (no default inheritance)
 * - Non-empty tool-refs: this scope includes only those tools (subject to annotation scopes)
 *
 * System Messages:
 *  * - system-messages: a Map<String, String> of custom system messages defined inline for this scope
 *                    Keys are identifiers, values are the prompt text
 *  * - These inline messages are always included in the scope
 *  * - system-message-refs: references to pre-defined @SystemMessage beans by name
 *
 * The built-in 'global' scope always exists and includes all tools/system messages.
 *
 * Structure:
 * ```yaml
 * aimo.scope:
 *   research:
 *     display-name: "Research Assistant"
 *     description: "Research and analysis tools"
 *     tool-refs: ["search", "summarize"]
 *     system-messages:
 *       research_guide: |
 *         You are a research expert specializing in data analysis.
 *         Focus on accuracy and citations.
 *     system-message-refs: ["research_prompt", "data_analysis"]
 *   public:
 *     display-name: "Public Assistant"
 *     description: "General purpose"
 *     tool-refs: ["help"]
 *     system-messages:
 *       public_guide: "You are a helpful assistant for the public."
 * ```
 */
data class AimoChatScopeProperties(
    /**
     * Human-readable scope name for display.
     */
    var displayName: String = "",

    /**
     * Scope description explaining its purpose.
     */
    var description: String = "",

     /**
      * Tool name references (exact match, no wildcards).
      * Empty list: this scope has NO tools.
      * Non-empty: scope includes only these tool names (must also match annotation scopes).
      */
     var toolRefs: List<String> = emptyList(),

    /**
     * System messages defined inline for this scope.
     * Map of message ID → prompt text.
     * These are custom messages created specifically for this scope.
     * Example:
     *   research_guide: "You are a research expert..."
     *   code_style: "Follow PEP 8 standards..."
     */
    var systemMessages: Map<String, String> = emptyMap(),

      /**
       * System message references to pre-defined @SystemMessage beans by name.
       * References pre-defined @SystemMessage beans from ChatService classes by their name property.
       * Empty list: only inline system-messages are used.
       * Non-empty: include pre-defined system messages with these names.
       *
       * Names come from @SystemMessage(name="...") or auto-generated from method/field name.
       * Example: ["research_guide", "code_analysis"]
       */
      var systemMessageRefs: List<String> = emptyList()
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
