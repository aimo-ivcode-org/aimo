package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManager

/**
 * Default implementation of ChatScopeProvider.
 *
 * Provides access to predefined scopes with actual tools and system messages filtered.
 * Constructs the global scope with only unrestricted tools and system messages.
 * Supports optional interceptor chain for access control (Phase 3).
 *
 * Now builds scopes dynamically from a provider manager, allowing runtime discovery
 * of tools and system messages. Filtering applies a two-condition AND:
 * - The provider's own scope set must allow the requested scope id (empty = allows all)
 * - AND the callback's own scope set must allow the requested scope id (empty = allows all)
 *
 * @property allTools All registered tools globally (for backwards compatibility)
 * @property allSystemMessages All registered system messages globally (for backwards compatibility)
 * @property predefinedScopes Map of scope ID → ChatScope for configured scopes
 * @property providerManager Manager for accessing current providers
 */
class ChatScopeProviderImpl(
    private val allTools: List<ToolCallback>,
    private val allSystemMessages: List<SystemMessageCallback>,
    private val predefinedScopes: Map<String, ChatScope> = emptyMap(),
    private val providerManager: ChatServiceProviderManager,
    private val interceptors: List<ChatScopeProviderInterceptor> = emptyList()
) : ChatScopeProvider {

    private val globalScope: ChatScope = run {
        // Include only tools with no scope restrictions
        val globalTools = allTools.filter { tool ->
            tool.scopes.isEmpty()
        }

        // Include only system messages with no scope restrictions
        val globalMessages = allSystemMessages
            .filter { message ->
                // Include if message has empty scope set (available to all scopes)
                message.scopes.isEmpty()
            }

        ChatScope(
            id = ChatScopeProvider.GLOBAL_SCOPE_ID,
            displayName = "Global",
            description = "Default scope with unrestricted tools and system messages",
            // Include ALL registered providers (annotated, MCP, etc). ChatScope.getAllTools()/
            // getAllSystemMessages() already apply per-provider and per-callback scope filtering,
            // so passing every provider here is safe and ensures dynamically-registered providers
            // (e.g. MCP servers) are not silently excluded from the global scope.
            providers = providerManager.getProviders(),
            tools = globalTools,
            systemMessages = globalMessages
        )
    }

    override fun getScopes(context: Map<String, Any>): List<ChatScope> {
        val allScopes = listOf(globalScope) + predefinedScopes.values

        if (interceptors.isEmpty()) return allScopes

        val mutableContext = mutableMapOf<String, Any>(
            "operation" to "getScopes",
            "scopes" to allScopes
        )
        mutableContext.putAll(context)

        val chain = buildChain(interceptors, 0) { ctx ->
            ctx["scopes"] as List<ChatScope>
        }

        @Suppress("UNCHECKED_CAST")
        return chain.proceed(mutableContext) as List<ChatScope>
    }

    override fun getScope(id: String, context: Map<String, Any>): ChatScope? {
        val scope = if (id == ChatScopeProvider.GLOBAL_SCOPE_ID) globalScope else predefinedScopes[id]
        if (scope == null) return null

        if (interceptors.isEmpty()) return scope

        val mutableContext = mutableMapOf<String, Any>(
            "operation" to "getScope",
            "scopeId" to id,
            "scope" to scope
        )
        mutableContext.putAll(context)

        val chain = buildChain(interceptors, 0) { ctx ->
            ctx["scope"]
        }

        return chain.proceed(mutableContext) as? ChatScope
    }

    override fun getGlobalScope(): ChatScope = globalScope

    private fun buildChain(
        interceptors: List<ChatScopeProviderInterceptor>,
        index: Int,
        finalAction: (MutableMap<String, Any>) -> Any?
    ): ChatScopeProviderInterceptor.Chain {
        return object : ChatScopeProviderInterceptor.Chain {
            override fun proceed(context: MutableMap<String, Any>): Any? {
                return if (index < interceptors.size) {
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, context)
                } else {
                    finalAction(context)
                }
            }
        }
    }
}

