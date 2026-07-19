package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.ChatServiceProvider

/**
 * Defines autonomous decision-making capabilities for a conversation.
 *
 * ChatScope contains tools and system messages available in this scope. The scope
 * can be constructed either with:
 * 1. Static lists (tools, systemMessages) for backwards compatibility with existing tests
 * 2. References to contributing providers for dynamic resolution at runtime
 *
 * When both providers and static lists are provided, both sources are included.
 * When tools or systemMessages are requested, they are resolved by combining the
 * current provider outputs with the static lists.
 *
 * @property id Unique identifier (e.g., "global", "admin", "research")
 * @property displayName Human-readable name for UI display
 * @property description What this scope provides and is used for
 * @property providers List of contributing chat service providers (may be empty)
 * @property tools Static list of tool callbacks available in this scope (for backwards compatibility)
 * @property systemMessages Static list of system message callbacks available in this scope (for backwards compatibility)
 */
data class ChatScope(
    val id: String,
    val displayName: String,
    val description: String,
    val providers: List<ChatServiceProvider>? = null,
    val tools: List<ToolCallback> = emptyList(),
    val systemMessages: List<SystemMessageCallback> = emptyList()
) {
    /**
     * Get all available tools in this scope.
     * Combines tools from all contributing providers (filtered by scope) with static tools.
     *
     * @return List of all available tool callbacks
     */
    fun getAllTools(): List<ToolCallback> {
        // Combine provider-sourced tools with static tools
        val providerTools = if (providers != null) {
            providers.flatMap { provider ->
                // Apply two-condition AND filtering:
                // 1. Provider's scope set must allow this scope id
                // 2. Tool's scope set must allow this scope id
                val providerAllowsScope = provider.scopes.isEmpty() || provider.scopes.contains(id)
                
                if (providerAllowsScope) {
                    provider.getTools().filter { tool ->
                        // Tool's scope set allows this scope
                        tool.scopes.isEmpty() || tool.scopes.contains(id)
                    }
                } else {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
        
        return (providerTools + tools).distinctBy { it.toolDefinition.name }
    }

    /**
     * Get all available system messages in this scope.
     * Combines messages from all contributing providers (filtered by scope) with static messages.
     *
     * @return List of all available system message callbacks
     */
    fun getAllSystemMessages(): List<SystemMessageCallback> {
        // Combine provider-sourced messages with static messages
        val providerMessages = if (providers != null) {
            providers.flatMap { provider ->
                // Apply two-condition AND filtering:
                // 1. Provider's scope set must allow this scope id
                // 2. Message's scope set must allow this scope id
                val providerAllowsScope = provider.scopes.isEmpty() || provider.scopes.contains(id)
                
                if (providerAllowsScope) {
                    provider.getSystemMessages().filter { message ->
                        // Message's scope set allows this scope
                        message.scopes.isEmpty() || message.scopes.contains(id)
                    }
                } else {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
        
        return (providerMessages + systemMessages).distinctBy { it.name }
    }
}

