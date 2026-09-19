package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Factory for creating, accessing, and deleting Conversation instances with optional interceptors.
 *
 * The ConversationFactory manages the full lifecycle of conversations:
 * - **Creation**: `createConversation()` creates and registers a new conversation
 * - **Access**: `getConversation()` retrieves an existing conversation
 * - **Listing**: `getConversations()` lists conversations matching optional scope metadata
 * - **Deletion**: `deleteConversation()` removes a conversation
 *
 * Metadata passed to all methods is used for scope-based access control and filtering.
 * Interceptors can enrich metadata or implement cross-cutting concerns (auditing, caching,
 * encryption, etc.) for all four lifecycle operations.
 *
 * Implementations are responsible for:
 * - Managing underlying storage (in-memory, filesystem, database, etc.)
 * - Enforcing scope metadata constraints
 * - Composing interceptors into the conversation's access chain
 * - Atomic operations for all lifecycle methods
 */
interface ConversationFactory {
    /**
     * Add an interceptor to be applied to all future conversations created by this factory.
     *
     * @param interceptor the interceptor to add
     * @return this factory instance for chaining
     */
    fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory

    /**
     * Create a new conversation.
     *
     * Creates and registers a fresh conversation in the underlying storage with optional
     * initial metadata. All subsequent factory operations (get, list, delete) will see
     * this newly created conversation.
     *
     * @param metadata optional initial metadata for the conversation; may be enriched or
     *                 validated by interceptors
     * @return a new Conversation instance
     * @throws IllegalStateException if creation fails or scope metadata is invalid
     */
    fun createConversation(metadata: Map<String, Any> = emptyMap()): Conversation

    /**
     * Get a conversation by chat ID with optional scope metadata.
     *
     * Scope metadata is used for access control and filtering. Interceptors may validate
     * or enrich the metadata before returning the conversation.
     *
     * @param chatId The chat identifier
     * @param metadata optional scope metadata for access control (e.g., tenant, user scope);
     *                 interceptors may add or validate entries
     * @return The conversation instance, or null if not found or access denied by scope/interceptors
     */
    fun getConversation(chatId: UUID, metadata: Map<String, Any> = emptyMap()): Conversation?

    /**
     * List conversations matching optional scope metadata.
     *
     * Returns all conversations that match the provided scope metadata. Useful for listing
     * user chats, tenant chats, or other scoped conversation collections.
     *
     * @param metadata optional scope metadata for filtering (e.g., tenant, user scope);
     *                 only conversations matching this scope are returned
     * @return list of Conversation instances matching the scope, or empty list if none found
     */
    fun getConversations(metadata: Map<String, Any> = emptyMap()): List<Conversation>

    /**
     * Delete a conversation by chat ID with optional scope metadata.
     *
     * Scope metadata is used for access control. Interceptors may validate the delete
     * operation before the conversation is removed from storage.
     *
     * @param chatId The chat identifier
     * @param metadata optional scope metadata for access control (e.g., tenant, user scope);
     *                 interceptors may add entries or deny the operation
     * @return true if the conversation was successfully deleted, false if not found or
     *         access denied by scope/interceptors
     */
    fun deleteConversation(chatId: UUID, metadata: Map<String, Any> = emptyMap()): Boolean
}
