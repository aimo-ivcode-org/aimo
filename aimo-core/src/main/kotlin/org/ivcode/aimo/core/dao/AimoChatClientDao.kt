package org.ivcode.aimo.core.dao

import java.util.UUID

/**
 * Data Access Object (DAO) for managing chat conversations, requests, and messages in aimo.
 *
 * This interface provides the abstraction for persisting and retrieving conversation data.
 * Implementations must handle:
 * - **User Isolation**: Each conversation is owned by a specific user; operations are scoped by userId
 * - **Character Budgeting**: Track and enforce character limits for context window management
 * - **Thread Safety**: Handle concurrent access safely (especially important for file-based impls)
 *
 * ## Authorization Model
 *
 * The DAO provides two sets of methods:
 *
 * 1. **User-Scoped Methods** (require userId):
 *    - Accept a non-null `userId: String` parameter
 *    - Enforce strict user ownership: operations only succeed if userId matches conversation owner
 *    - Used by [Aimo] user-facing methods
 *
 * 2. **Admin Methods** (bypass authorization):
 *    - Named with "Admin" suffix (e.g., `getChatConversationAdmin`)
 *    - No userId parameter; operate on any conversation regardless of owner
 *    - **SECURITY WARNING**: Only expose through authenticated admin-only endpoints
 *    - Used by [Aimo] admin methods
 *
 * ### Examples: User-Scoped
 * ```kotlin
 * // Create conversation for user "alice"
 * val conv = dao.createChatConversation("alice")  // ✅ succeeds
 *
 * // Retrieve conversation (must provide matching userId)
 * dao.getChatConversation(conv.chatId, "alice")   // ✅ succeeds - owner access
 * dao.getChatConversation(conv.chatId, "bob")     // ❌ denied - bob doesn't own this conversation
 * ```
 *
 * ### Examples: Admin
 * ```kotlin
 * // Admin: access any conversation without user check (SECURITY WARNING: admin only)
 * val conv = dao.getChatConversationAdmin(anyConversationId)  // ✅ succeeds without auth check
 * val allConversations = dao.getChatConversationsAdmin()      // ✅ returns all conversations
 * ```
 *
 * ## Implementations
 *
 * - **AimoChatClientDaoMemory**: In-memory storage; conversations lost on restart. Useful for testing.
 * - **AimoChatClientDaoFile**: Durable file-based storage in JSON format; survives restarts.
 *
 * ## Character Budgeting
 *
 * The `getChatRequests(userId, chatId, maxRequestCharacters)` method implements strict character budgeting:
 * - Returns the newest requests that fit within the budget
 * - Budget is **strictly respected**: if adding the next request would exceed it, the request is excluded
 * - Used by [AimoChatClientImpl] to enforce context window limits
 *
 * @see ChatConversationEntity
 * @see ChatRequestEntity
 * @see ChatMessageEntity
 */
interface AimoChatClientDao {

    /**
     * Create a new conversation.
     *
     * @param userId the user ID that owns this conversation
     * @return the newly created [ChatConversationEntity]
     */
    fun createChatConversation(userId: String): ChatConversationEntity

    /**
     * Create a new conversation and persist the provided [metadata] with it.
     *
     * The [metadata] map contains initial or additional conversation information that
     * implementations should store with the newly created conversation.
     *
     * @param userId the user ID that owns this conversation
     * @param metadata initial key/value pairs to persist with the new conversation
     * @return the newly created and persisted [ChatConversationEntity]
     */
    fun createChatConversation(userId: String, metadata: Map<String, String> = emptyMap()): ChatConversationEntity

    // ============================================================================
    // User-Scoped Methods (require userId - strict ownership enforcement)
    // ============================================================================

    /**
     * Get all conversations owned by a user.
     *
     * Returns only conversations where the specified user is the owner.
     *
     * @param userId the user ID to filter conversations by
     * @return list of conversations owned by the userId, or empty list if user has no conversations
     */
    fun getChatConversations(userId: String): List<ChatConversationEntity>

    /**
     * Retrieve a conversation by its [chatId], scoped by userId.
     *
     * **Authorization**: Verifies the user owns the conversation; denied if mismatch.
     *
     * @param chatId the unique identifier of the conversation to retrieve
     * @param userId user ID requesting access; must match conversation owner
     * @return the conversation if found and user is owner, null otherwise
     */
    fun getChatConversation(chatId: UUID, userId: String): ChatConversationEntity?

    /**
     * Delete a conversation.
     *
     * **Authorization**: Verifies the user owns the conversation before deletion.
     *
     * @param chatId the conversation to delete
     * @param userId user ID requesting deletion; must match conversation owner
     * @return true if the conversation was successfully deleted
     *         false if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     */
    fun deleteChatConversation(chatId: UUID, userId: String): Boolean

    /**
     * Add a chat request to a conversation.
     *
     * **Authorization**: Verifies the user owns the conversation before adding.
     *
     * @param userId user ID adding the request; must match conversation owner
     * @param request the request to add
     * @return true if the request was successfully added
     *         false if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     */
    fun addChatRequest(userId: String, request: ChatRequestEntity): Boolean

    /**
     * Get chat requests for a conversation.
     *
     * **Authorization**: Returns requests only if user owns the conversation.
     *
     * @param userId user ID requesting the data; must own the conversation
     * @param chatId the conversation id
     * @return list of all requests for the conversation, or empty if user doesn't own it
     */
    fun getChatRequests(userId: String, chatId: UUID): List<ChatRequestEntity>

    /**
     * Retrieve recent chat requests for a conversation, bounded by character count.
     *
     * Implements strict character budgeting: returns the most recent requests that fit within the budget.
     * If adding the next request would exceed maxRequestCharacters, that request and all older requests
     * are excluded.
     *
     * **Authorization**: Returns requests only if user owns the conversation.
     *
     * **Typical Use Case**: [AimoChatClientImpl] uses this to stay within LLM context window limits
     * when building prompts from conversation history.
     *
     * @param userId user ID requesting the data; must own the conversation
     * @param chatId the conversation to fetch requests from
     * @param maxRequestCharacters maximum cumulative character count; must be positive.
     *                               Zero or negative returns an empty list
     * @return requests in chronological order that fit within the budget, or empty if:
     *         - userId doesn't own the conversation
     *         - no requests fit within the budget
     */
    fun getChatRequests(userId: String, chatId: UUID, maxRequestCharacters: Long): List<ChatRequestEntity>

    /**
     * Get all messages for a conversation.
     *
     * **Authorization**: Returns messages only if user owns the conversation.
     *
     * @param userId user ID requesting the data; must own the conversation
     * @param chatId the conversation id
     * @return list of all messages, or empty if user doesn't own the conversation
     */
    fun getMessages(userId: String, chatId: UUID): List<ChatMessageEntity>

    /**
     * Upsert metadata for an existing conversation.
     *
     * **Authorization**: Verifies the user owns the conversation before updating.
     *
     * @param chatId the conversation to update
     * @param userId user ID requesting the update; must match conversation owner
     * @param metadata key/value pairs to insert or update
     * @return true if metadata was successfully updated
     *         false if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     */
    fun upsertConversationMetadata(chatId: UUID, userId: String, metadata: Map<String, Any>): Boolean

    /**
     * Delete metadata keys from a conversation.
     *
     * **Authorization**: Verifies the user owns the conversation before deleting.
     *
     * @param chatId the conversation to update
     * @param userId user ID requesting the deletion; must match conversation owner
     * @param keys metadata keys to delete
     * @return true if deletion was successful
     *         false if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     */
    fun deleteConversationMetadata(chatId: UUID, userId: String, keys: List<String>): Boolean

    // ============================================================================
    // Admin Methods (bypass authorization - internal/admin use only)
    // ============================================================================

    /**
     * Get a specific conversation without authorization checks.
     *
     * This is an internal method for admin operations.
     * **SECURITY WARNING**: This bypasses user isolation. Use [getChatConversation] with userId instead.
     *
     * @param chatId the conversation to retrieve
     * @return the conversation or null if not found
     */
    fun getChatConversation(chatId: UUID): ChatConversationEntity?

    /**
     * Get all conversations regardless of owner.
     *
     * **SECURITY WARNING**: This method bypasses all user authorization. Only use for admin operations.
     *
     * @return list of all conversations
     */
    fun getChatConversationsAdmin(): List<ChatConversationEntity>

    /**
     * Retrieve a conversation by its [chatId] without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user isolation. Only use for admin operations.
     *
     * @param chatId the conversation to retrieve
     * @return the conversation or null if not found
     */
    fun getChatConversationAdmin(chatId: UUID): ChatConversationEntity?

    /**
     * Delete a conversation without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user authorization. Only use for admin operations.
     *
     * @param chatId the conversation to delete
     * @return true if the conversation was successfully deleted, false if not found
     */
    fun deleteChatConversationAdmin(chatId: UUID): Boolean

    /**
     * Add a chat request without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user isolation. Only use for admin operations.
     *
     * @param request the request to add
     * @return true if the request was successfully added, false if conversation not found
     */
    fun addChatRequestAdmin(request: ChatRequestEntity): Boolean

    /**
     * Get chat requests for a conversation without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user authorization. Only use for admin operations.
     *
     * @param chatId the conversation id
     * @return list of all requests for the conversation, or empty if not found
     */
    fun getChatRequestsAdmin(chatId: UUID): List<ChatRequestEntity>

    /**
     * Retrieve recent chat requests for a conversation without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user authorization. Only use for admin operations.
     *
     * @param chatId the conversation to fetch requests from
     * @param maxRequestCharacters maximum cumulative character count; must be positive.
     *                               Zero or negative returns an empty list
     * @return requests in chronological order that fit within the budget, or empty if:
     *         - conversation not found
     *         - no requests fit within the budget
     */
    fun getChatRequestsAdmin(chatId: UUID, maxRequestCharacters: Long): List<ChatRequestEntity>

    /**
     * Get all messages for a conversation without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user authorization. Only use for admin operations.
     *
     * @param chatId the conversation id
     * @return list of all messages, or empty if not found
     */
    fun getMessagesAdmin(chatId: UUID): List<ChatMessageEntity>

    /**
     * Upsert metadata for an existing conversation without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user authorization. Only use for admin operations.
     *
     * @param chatId the conversation to update
     * @param metadata key/value pairs to insert or update
     * @return true if metadata was successfully updated, false if conversation not found
     */
    fun upsertConversationMetadataAdmin(chatId: UUID, metadata: Map<String, Any>): Boolean

    /**
     * Delete metadata keys from a conversation without authorization checks.
     *
     * **SECURITY WARNING**: This method bypasses user authorization. Only use for admin operations.
     *
     * @param chatId the conversation to update
     * @param keys metadata keys to delete
     * @return true if deletion was successful, false if conversation not found
     */
    fun deleteConversationMetadataAdmin(chatId: UUID, keys: List<String>): Boolean
}
