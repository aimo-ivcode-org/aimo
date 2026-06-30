package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Interceptor for conversation factory operations.
 *
 * Interceptors can add cross-cutting concerns such as:
 * - **Auditing**: Log conversation access
 * - **Caching**: Memoize conversation instances
 * - **Metadata enrichment**: Add or modify metadata before DAO access
 *
 * ## Usage Pattern
 *
 * Interceptors intercept [ConversationFactory.getConversation] calls and receive:
 * - `chatId`: The conversation identifier
 * - `metadata`: Mutable metadata map for DAO scoping; interceptors may enrich it
 *
 * ```kotlin
 * factory
 *     .withInterceptor(MyCustomInterceptor())
 *     .getConversation(chatId, mapOf("tenant" to "acme"))
 * ```
 */
interface ConversationInterceptor {
    /**
     * Intercept a [ConversationFactory.getConversation] call.
     *
     * @param chain The interceptor chain to proceed with
     * @param chatId The chat identifier for this operation
     * @param metadata Mutable metadata map for DAO scoping; interceptors may add entries to enrich the metadata
     * @return The Conversation instance, or null if not found or access denied
     */
    fun intercept(chain: Chain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation?

    interface Chain {
        /**
         * Proceed to the next interceptor or final [ConversationFactory.getConversation] operation.
         *
         * @param chatId The chat identifier
         * @param metadata The metadata for this operation (may have been enriched by interceptors)
         * @return The Conversation instance, or null if not found or access denied
         */
        fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Conversation?
    }
}
