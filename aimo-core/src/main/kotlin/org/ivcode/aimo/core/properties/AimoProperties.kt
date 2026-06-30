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
 * Scopes Auto-Discovered from Annotations:
 * - All @ChatService(scope=[...]) annotations are discovered automatically
 * - Each unique scope ID creates a ChatScope automatically
 *
 * Config-Based Scope Customization:
 * - displayName: Human-readable scope name for display
 * - description: Scope description explaining its purpose
 * - inheritGlobal: Whether to include tools without scope restrictions (default: true)
 *                  If false, only tools explicitly in toolRefs are included (plus annotations)
 * - toolRefs: Explicitly include specific tools in this scope
 * - systemMessages: Custom inline system messages for this scope (YAML-defined prompts)
 * - systemMessageRefs: Include pre-defined @SystemMessage beans by name in this scope
 *
 * Semantics:
 * - Tools without scope restrictions (@ChatService with no scope arg) are "global" tools
 * - inheritGlobal=true (default): scope includes global tools + annotation-scoped tools + toolRefs
 * - inheritGlobal=false: scope includes ONLY annotation-scoped tools + toolRefs (no global tools)
 *
 * Example:
 * ```yaml
 * aimo.scope:
 *   restricted_admin:
 *     display-name: "Restricted Admin"
 *     inherit-global: false  # Don't include global tools
 *     tool-refs: ["delete_user", "ban_ip"]  # Only these admin tools
 *     system-messages:
 *       admin_warning: "You have admin-only access. Be careful."
 *
 *   research:
 *     display-name: "Research Assistant"
 *     inherit-global: true   # Include global tools (help, status, etc)
 *     tool-refs: ["search", "analyze"]  # Plus these research tools
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
     * Whether to include "global" tools (tools with no scope restriction).
     * true (default): Scope includes both global tools and scoped tools
     * false: Scope includes ONLY tools explicitly declared for it (via annotations or toolRefs)
     */
    var inheritGlobal: Boolean = true,

    /**
     * Tool name references to explicitly include in this scope.
     * These are in addition to tools discovered through annotations.
     * Empty list: no tool-refs specified (uses all annotation-discovered tools + global if inheritGlobal=true)
     * Non-empty: explicitly include these tool names in the scope
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
