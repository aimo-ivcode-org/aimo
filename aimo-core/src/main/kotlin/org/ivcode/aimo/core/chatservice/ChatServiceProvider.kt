package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.ToolCallback

/**
 * Provider abstraction for chat services that contribute tools and system messages.
 *
 * A provider exposes an identity, a provider-level scope restriction, and callbacks
 * for tools and system messages. Each callback carries its own scope restrictions,
 * allowing fine-grained control over which scopes can access which tools/messages.
 *
 * An empty provider-level [scopes] set means the provider is unrestricted (global):
 * callbacks are then gated only by their own scope restrictions. A non-empty
 * provider-level [scopes] set means the provider only contributes callbacks when
 * building one of those specific scopes.
 *
 * ## Scope Inheritance Rules
 *
 * When a provider has non-empty scopes (e.g., ["admin", "research"]):
 * - The provider can only contribute to those specific scopes
 * - All callbacks MUST have scopes that are subsets of the provider's scopes
 * - A callback with scope ["forbidden"] would be invalid (not in provider.scopes)
 * - Valid callback scopes: [], ["admin"], ["research"], ["admin", "research"]
 * - Empty callback scopes [] means "inherit and use all of provider's scopes"
 *
 * @property id Unique identifier for this provider (e.g., "annotated", "mcp-server-1")
 * @property scopes Set of scope IDs where this provider is restricted; empty = unrestricted/global
 */
interface ChatServiceProvider {
    val id: String
    val scopes: Set<String>

    /**
     * Get all tool callbacks provided by this provider.
     * Each tool callback carries its own scope restrictions embedded.
     * Filtering by [scopes] is the responsibility of the caller.
     *
     * @return List of tool callbacks, potentially empty
     */
    fun getTools(): List<ToolCallback>

    /**
     * Get all system message callbacks provided by this provider.
     * Each system message callback carries its own scope restrictions embedded.
     * Filtering by [scopes] is the responsibility of the caller.
     *
     * @return List of system message callbacks, potentially empty
     */
    fun getSystemMessages(): List<SystemMessageCallback>

    /**
     * Validate that all callback scopes are subsets of provider scopes.
     * Used by providers to ensure scope constraints at initialization time.
     *
     * Rules:
     * - If provider.scopes is empty (unrestricted), all callback scopes are valid
     * - If provider.scopes is non-empty, each callback scope must be a subset:
     *   - Callback scope [] (empty) is always valid → inherits provider scopes
     *   - Callback scope ["a", "b"] is valid only if provider.scopes contains all of ["a", "b"]
     *   - Callback scope ["c"] where "c" not in provider.scopes is invalid
     *
     * @throws IllegalArgumentException if any callback scope violates provider scope constraints
     */
    fun validateCallbackScopes() {
        if (this.scopes.isEmpty()) {
            // Provider is unrestricted, any callback scopes are valid
            return
        }

        // Validate tool scopes
        getTools().forEach { tool ->
            if (tool.scopes.isNotEmpty()) {
                val invalidScopes = tool.scopes - this.scopes
                require(invalidScopes.isEmpty()) {
                    "Provider '$id' has scopes ${this.scopes}, but tool '${tool.toolDefinition.name}' has invalid scopes: $invalidScopes. " +
                    "Callback scopes must be a subset of provider scopes."
                }
            }
        }

        // Validate system message scopes
        getSystemMessages().forEach { message ->
            if (message.scopes.isNotEmpty()) {
                val invalidScopes = message.scopes - this.scopes
                require(invalidScopes.isEmpty()) {
                    "Provider '$id' has scopes ${this.scopes}, but system message '${message.name}' has invalid scopes: $invalidScopes. " +
                    "Callback scopes must be a subset of provider scopes."
                }
            }
        }
    }
}
