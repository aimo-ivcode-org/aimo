package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatResponse

/**
 * Interceptor for chat client (request-level) operations.
 *
 * Handles cross-cutting concerns at the chat request layer such as:
 * - Guard-rails (validate user input, filter responses)
 * - Security filtering (redact sensitive data)
 * - Logging and tracing (observe chat operations)
 * - Error handling (retry, fallback, exception mapping)
 * - Caching (cache chat responses)
 *
 * **NOT interchangeable with ConversationInterceptor** — different operations, different signatures.
 *
 * Interceptors form a chain where each interceptor can read/modify the context before
 * calling `chain.proceed(context)` to pass control to the next interceptor.
 */
interface ChatClientInterceptor {
    /**
     * Intercepts a chat client operation.
     *
     * @param chain The next link in the interceptor chain.
     * @param context Mutable operation context containing parameters like `chatId`, `requestId`,
     *                `userId`, `message`, `requestMetadata`, etc. Modifications propagate downstream only.
     * @return The chat response from the operation.
     */
    fun intercept(chain: Chain, context: MutableMap<String, Any>): AimoChatResponse

    /**
     * Chain link for chat operation execution.
     */
    interface Chain {
        /**
         * Proceeds to the next interceptor in the chain, or executes the base operation
         * if this is the last link.
         *
         * @param context The operation context with potentially modified parameters.
         * @return The chat response.
         */
        fun proceed(context: MutableMap<String, Any>): AimoChatResponse
    }
}