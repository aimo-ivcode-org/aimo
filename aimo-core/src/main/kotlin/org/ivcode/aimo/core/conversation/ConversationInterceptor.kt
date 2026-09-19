package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Interceptor for conversation factory operations.
 *
 * Interceptors can add cross-cutting concerns such as:
 * - **Auditing**: Log conversation creation, access, and deletion
 * - **Caching**: Memoize conversation instances
 * - **Metadata enrichment**: Add, modify, or validate metadata before operations
 * - **Encryption**: Encrypt/decrypt conversation data transparently
 *
 * ## Usage Pattern
 *
 * Interceptors intercept all four lifecycle operations on [ConversationFactory]:
 * - `interceptCreate`: Intercepts [ConversationFactory.createConversation] calls
 * - `interceptGet`: Intercepts [ConversationFactory.getConversation] calls
 * - `interceptList`: Intercepts [ConversationFactory.getConversations] calls
 * - `interceptDelete`: Intercepts [ConversationFactory.deleteConversation] calls
 *
 * ```kotlin
 * factory
 *     .withInterceptor(MyCustomInterceptor())
 *     .createConversation(mapOf("title" to "New Chat"))
 * ```
 *
 * Interceptors are applied in the order they were added via [ConversationFactory.withInterceptor].
 */
interface ConversationInterceptor {
    /**
     * Intercept a [ConversationFactory.createConversation] call.
     *
     * @param chain The interceptor chain to proceed with
     * @param metadata Mutable metadata map for the new conversation; interceptors may add or modify entries
     * @return The newly created Conversation instance
     */
    fun interceptCreate(chain: CreateChain, metadata: MutableMap<String, Any>): Conversation

    /**
     * Intercept a [ConversationFactory.getConversation] call.
     *
     * @param chain The interceptor chain to proceed with
     * @param chatId The chat identifier for this operation
     * @param metadata Mutable metadata map for scope validation; interceptors may add entries
     * @return The Conversation instance, or null if not found or access denied
     */
    fun interceptGet(chain: GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation?

    /**
     * Intercept a [ConversationFactory.getConversations] call.
     *
     * @param chain The interceptor chain to proceed with
     * @param metadata Mutable metadata map for scope filtering; interceptors may modify entries
     * @return list of Conversation instances matching scope, or empty list if none found
     */
    fun interceptList(chain: ListChain, metadata: MutableMap<String, Any>): List<Conversation>

    /**
     * Intercept a [ConversationFactory.deleteConversation] call.
     *
     * @param chain The interceptor chain to proceed with
     * @param chatId The chat identifier for this operation
     * @param metadata Mutable metadata map for scope validation; interceptors may add entries
     * @return true if the conversation was successfully deleted, false if not found or access denied
     */
    fun interceptDelete(chain: DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean

    /**
     * Chain for [createConversation] operation.
     */
    interface CreateChain {
        /**
         * Proceed to the next interceptor or final [ConversationFactory.createConversation] operation.
         *
         * @param metadata The metadata for this operation (may have been enriched by interceptors)
         * @return The newly created Conversation instance
         */
        fun proceed(metadata: MutableMap<String, Any>): Conversation
    }

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
     * Chain for [getConversations] operation.
     */
    interface ListChain {
        /**
         * Proceed to the next interceptor or final [ConversationFactory.getConversations] operation.
         *
         * @param metadata The metadata for this operation (may have been modified by interceptors)
         * @return list of Conversation instances matching scope, or empty list if none found
         */
        fun proceed(metadata: MutableMap<String, Any>): List<Conversation>
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
