package org.ivcode.aimo.core.builder.interceptor

/**
 * Interceptor for conversation (DAO-level) operations.
 *
 * Handles cross-cutting concerns at the conversation storage layer such as:
 * - Security and access control (verify user ownership before DAO access)
 * - Auditing (log all conversation reads/writes)
 * - Caching (memoize getMessages calls)
 * - Data transformation (encryption, schema migration)
 *
 * **NOT interchangeable with ChatClientInterceptor** — different operations, different signatures.
 *
 * Interceptors form a chain where each interceptor can read/modify the context before
 * calling `chain.proceed(context)` to pass control to the next interceptor.
 */
interface ConversationInterceptor {
    /**
     * Intercepts a conversation operation.
     *
     * @param chain The next link in the interceptor chain.
     * @param context Mutable operation context containing parameters like `chatId`, `userId`,
     *                `messages`, `property`, `value`, etc. Modifications propagate downstream only.
     * @return The operation result (type depends on the operation being intercepted).
     */
    fun intercept(chain: Chain, context: MutableMap<String, Any>): Any?

    /**
     * Chain link for conversation operation execution.
     */
    interface Chain {
        /**
         * Proceeds to the next interceptor in the chain, or executes the base operation
         * if this is the last link.
         *
         * @param context The operation context with potentially modified parameters.
         * @return The operation result.
         */
        fun proceed(context: MutableMap<String, Any>): Any?
    }
}

