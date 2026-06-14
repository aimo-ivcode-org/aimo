package org.ivcode.aimo.core.chatscope

/**
 * Interceptor for ChatScopeProvider operations enabling access control.
 *
 * Follows the same chain-of-responsibility pattern as ChatClientInterceptor.
 * Interceptors can filter available scopes or deny access to specific scopes
 * based on user context (permissions, role, organization, etc.).
 *
 * Concrete security interceptor implementations are provided by Phase 3
 * Spring Security integration.
 *
 * Example usage:
 * ```kotlin
 * class AdminScopeInterceptor : ChatScopeProviderInterceptor {
 *     override fun intercept(chain: Chain, context: MutableMap<String, Any>): Any? {
 *         val user = context["user"] as? CurrentUser ?: return chain.proceed(context)
 *         if (!user.hasRole("ADMIN")) {
 *             // Filter out admin scope for non-admins
 *             val scopes = (context["scopes"] as? List<*>)?.filterNot { it is ChatScope && it.id == "admin" }
 *             context["scopes"] = scopes ?: emptyList<ChatScope>()
 *         }
 *         return chain.proceed(context)
 *     }
 * }
 * ```
 */
interface ChatScopeProviderInterceptor {
    /**
     * Intercept a ChatScopeProvider operation (getScopes, getScope, etc.).
     *
     * @param chain The interceptor chain to proceed through
     * @param context Mutable context containing operation details:
     *        - "operation": String (e.g., "getScopes", "getScope")
     *        - "scopeId": String (for getScope operation)
     *        - "scope" or "scopes": ChatScope(s) being retrieved
     *        - Plus any additional context passed by caller (user, permissions, etc.)
     * @return The result (scope(s), modified or unmodified)
     */
    fun intercept(chain: Chain, context: MutableMap<String, Any>): Any?

    /**
     * Chain for proceeding to the next interceptor or final handler.
     */
    interface Chain {
        fun proceed(context: MutableMap<String, Any>): Any?
    }
}

