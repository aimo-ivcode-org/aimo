package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Factory for creating, accessing, and deleting Conversation instances with optional interceptors.
 *
 * The ConversationFactory manages the full lifecycle of conversations:
 * - **Creation**: `createConversation()` creates and registers a new conversation
 * - **Access**: `getConversation()` retrieves an existing conversation
 * - **Listing**: `getConversations()` lists conversations matching optional interceptor metadata
 * - **Deletion**: `deleteConversation()` removes a conversation
 *
 * Interceptor metadata passed to all methods flows through the interceptor chain, allowing
 * interceptors to enrich, validate, or deny operations based on access control, tenant context,
 * or other cross-cutting concerns (auditing, caching, encryption, etc.).
 *
 * Implementations are responsible for:
 * - Managing underlying storage (in-memory, filesystem, database, etc.)
 * - Enforcing access control based on interceptor metadata
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
     * initial interceptor metadata. Interceptors can enrich or validate this metadata.
     *
     * @param metadata optional initial interceptor metadata for the conversation; may be
     *                 enriched or validated by interceptors
     * @return a new Conversation instance
     * @throws IllegalStateException if creation fails
     */
    fun createConversation(metadata: Map<String, Any> = emptyMap()): Conversation

    /**
     * Get a conversation by chat ID with optional interceptor metadata.
     *
     * The metadata flows through the interceptor chain for validation and enrichment
     * before the conversation is returned.
     *
     * @param chatId The chat identifier
     * @param metadata optional interceptor metadata for access control and filtering;
     *                 interceptors may validate or enrich this metadata
     * @return The conversation instance, or null if not found or access denied by interceptors
     */
    fun getConversation(chatId: UUID, metadata: Map<String, Any> = emptyMap()): Conversation?

    /**
     * List conversations matching optional interceptor metadata.
     *
     * Returns all conversations filtered through the interceptor chain based on the
     * provided metadata.
     *
     * @param metadata optional interceptor metadata for filtering; only conversations
     *                 matching this metadata are returned (after interceptor processing)
     * @return list of Conversation instances, or empty list if none found
     */
    fun getConversations(metadata: Map<String, Any> = emptyMap()): List<Conversation>

    /**
     * Delete a conversation by chat ID with optional interceptor metadata.
     *
     * The metadata flows through the interceptor chain, which may validate or deny
     * the delete operation.
     *
     * @param chatId The chat identifier
     * @param metadata optional interceptor metadata for access control; interceptors may
     *                 validate this metadata or deny the operation
     * @return true if the conversation was successfully deleted, false if not found or
     *         access denied by interceptors
     */
    fun deleteConversation(chatId: UUID, metadata: Map<String, Any> = emptyMap()): Boolean
}
