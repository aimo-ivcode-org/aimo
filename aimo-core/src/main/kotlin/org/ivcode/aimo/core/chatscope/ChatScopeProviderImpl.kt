package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback

/**
 * Default implementation of ChatScopeProvider.
 *
 * Provides access to predefined scopes with actual tools and system messages filtered.
 * Constructs the global scope with only unrestricted tools and system messages.
 * Supports optional interceptor chain for access control (Phase 3).
 *
 * @property allTools All registered tools globally
 * @property allSystemMessages All registered system messages globally
 * @property predefinedScopes Map of scope ID → ChatScope for configured scopes
 * @property systemMessageScopeMap Map of system message name → set of scope IDs it's restricted to
 */
class ChatScopeProviderImpl(
    private val allTools: List<AimoToolCallback>,
    private val allSystemMessages: List<SystemMessageCallback>,
    private val predefinedScopes: Map<String, ChatScope> = emptyMap(),
    private val toolScopeMap: Map<String, Set<String>>,
    private val systemMessageScopeMap: Map<String, Set<String>>,
    private val interceptors: List<ChatScopeProviderInterceptor> = emptyList()
) : ChatScopeProvider {

    private val globalScope: ChatScope = run {
        // Include only tools with no scope restrictions
        val globalTools = allTools.filter { tool ->
            toolScopeMap[tool.toolDefinition.name]?.isEmpty() != false
        }

        // Include only system messages with no scope restrictions
        val restrictedMessageIndices = systemMessageScopeMap
            .filterValues { it.isNotEmpty() }
            .keys
        val globalMessages = allSystemMessages.filterIndexed { index, _ ->
            !restrictedMessageIndices.contains(index.toString())
        }

        ChatScope(
            id = "global",
            displayName = "Global",
            description = "Default scope with unrestricted tools and system messages",
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
        val scope = if (id == "global") globalScope else predefinedScopes[id]
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

