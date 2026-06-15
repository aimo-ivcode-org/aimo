package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback

/**
 * Default implementation of ChatScopeProvider.
 *
 * Builds a global scope from all registered tools/system messages.
 * Loads predefined scopes from configuration.
 * Supports optional interceptor chain for access control (Phase 3).
 *
 * @property allTools All registered tools globally
 * @property allSystemMessages All registered system messages globally
 * @property predefinedScopes Map of scope ID → ChatScope for configured scopes
 * @property interceptors Interceptors to apply during scope retrieval (empty by default)
 */
class ChatScopeProviderImpl(
    private val allTools: List<AimoToolCallback>,
    private val allSystemMessages: List<SystemMessageCallback>,
    private val predefinedScopes: Map<String, ChatScope> = emptyMap(),
    private val interceptors: List<ChatScopeProviderInterceptor> = emptyList()
) : ChatScopeProvider {

    private val globalScope: ChatScope = ChatScope(
        id = "global",
        displayName = "Global",
        description = "All available tools and system messages",
        toolNames = allTools.map { it.toolDefinition.name }.toSet(),
        systemMessageNames = allSystemMessages.indices.map { it.toString() }.toSet()
    )

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

