package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Interceptor for conversation factory operations.
 *
 * Interceptors can add cross-cutting concerns such as:
 * - **Auditing**: Log conversation access and deletion
 * - **Caching**: Memoize conversation instances
 * - **Metadata enrichment**: Add or modify metadata before DAO access
 *
 * ## Usage Pattern
 *
 * Interceptors intercept specific operations on [ConversationFactory]:
 * - `interceptGet`: Intercepts [ConversationFactory.getConversation] calls
 * - `interceptDelete`: Intercepts [ConversationFactory.deleteConversation] calls
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
    fun interceptGet(chain: GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation?

    /**
     * Intercept a [ConversationFactory.deleteConversation] call.
     *
     * @param chain The interceptor chain to proceed with
     * @param chatId The chat identifier for this operation
     * @param metadata Mutable metadata map for DAO scoping; interceptors may add entries to enrich the metadata
     * @return true if the conversation was successfully deleted, false if not found or access denied
     */
    fun interceptDelete(chain: DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean

    /**
     * Chain for [getConversation] operation.
     */
    interface GetChain {
        /**
         * Proceed to the next interceptor or final [ConversationFactory.getConversation] operation.
         *
         * @param chatId The chat identifier
         * @param metadata The metadata for this operation (may have been enriched by interceptors)
         * @return The Conversation instance, or null if not found or access denied
         */
        fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Conversation?
    }

    /**
     * Chain for [deleteConversation] operation.
     */
    interface DeleteChain {
        /**
         * Proceed to the next interceptor or final [ConversationFactory.deleteConversation] operation.
         *
         * @param chatId The chat identifier
         * @param metadata The metadata for this operation (may have been enriched by interceptors)
         * @return true if the conversation was successfully deleted, false if not found or access denied
         */
        fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Boolean
    }
}
