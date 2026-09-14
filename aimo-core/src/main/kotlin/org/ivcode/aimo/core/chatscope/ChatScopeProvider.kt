package org.ivcode.aimo.core.chatscope

/**
 * Provider for retrieving available ChatScopes.
 *
 * The provider always offers a built-in global scope that includes tools and system
 * messages with no scope restrictions (available to all scopes).
 *
 * Access control and request-scoped visibility checks belong in server/host modules
 * where security context is available, not in core.
 */
interface ChatScopeProvider {
    companion object {
        // Internal scope id for the built-in global scope (not exposed as a user-selectable scope name)
        const val GLOBAL_SCOPE_ID = ""
    }
    /**
     * Get all available scopes after applying any interceptors.
     *
     * @param context Optional context for interceptor filtering (user info, permissions, etc.)
     * @return List of scopes the caller can access
     */
     fun getScopes(context: Map<String, Any> = emptyMap()): List<ChatScope>

    /**
     * Get a specific scope by ID after applying any interceptors.
     *
     * @param id The scope ID to retrieve
     * @param context Optional context for interceptor filtering
     * @return ChatScope if found and accessible, null otherwise
     */
     fun getScope(id: String, context: Map<String, Any> = emptyMap()): ChatScope?

    /**
     * Get the built-in global scope (always available, no interception).
     *
     * The global scope includes tools and system messages with no scope restrictions
     * (those declared with empty scope arrays in @Tool, @SystemMessage, or @ChatService
     * annotations). These are available to all scopes by design.
     * It is used as the default when no explicit scope is selected.
     *
     * @return The global scope (never null)
     */
     fun getGlobalScope(): ChatScope
}
