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
 * ## User Isolation Model
 *
 * All conversations are **owned by a specific user**. Authorization is enforced as follows:
 *
 * ### Creation
 * - `createChatConversation(userId, ...)`: **Requires non-null userId** - every conversation must belong to a user
 * - Attempting to create without a user is rejected (null userId not allowed)
 *
 * ### Retrieval & Modification
 * - Operations accept optional `userId` parameter:
 *   - **Non-null userId**: Must match conversation owner; denied if mismatch
 *   - **Null userId**: Denied (no admin override) - caller must provide their userId
 *
 * ### Example
 * ```kotlin
 * // Create conversation for user "alice"
 * val conv = dao.createChatConversation("alice")  // ✅ succeeds
 * dao.createChatConversation(null)                 // ❌ fails - null userId not allowed
 *
 * // Retrieve conversation (must provide matching userId)
 * dao.getChatConversation(conv.chatId, "alice")   // ✅ succeeds - owner accessing own conversation
 * dao.getChatConversation(conv.chatId, "bob")     // ❌ denied - bob doesn't own this conversation
 * dao.getChatConversation(conv.chatId, null)      // ❌ denied - null userId not authorized
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

    /**
     * Get all conversations visible to the userId.
     *
     * **Authorization**: If userId is provided, returns only conversations owned by that user.
     * If userId is null, no authorization check is performed; returns all conversations.
     *
     * @param userId the user ID to filter conversations by; if null, returns all conversations (no auth check)
     * @return list of conversations owned by the userId, or all conversations if userId is null
     */
    fun getChatConversations(userId: String? = null): List<ChatConversationEntity>

    /**
     * Get a specific conversation (unscoped - internal use only).
     *
     * **Warning**: This bypasses user isolation checks. Use [getChatConversation] with userId instead.
     *
     * @param chatId the conversation to retrieve
     * @return the conversation or null
     */
    fun getChatConversation(chatId: UUID): ChatConversationEntity?

     /**
      * Retrieve a conversation by its [chatId], scoped by userId.
      *
      * **Authorization**: If userId is provided, verifies the user owns the conversation.
      * If userId is null, no authorization check is performed.
      *
      * @param chatId the unique identifier of the conversation to retrieve
      * @param userId optional user ID requesting access; if provided, must match conversation owner
      * @return the conversation if found and (userId is null or userId matches owner), null otherwise
      */
     fun getChatConversation(chatId: UUID, userId: String? = null): ChatConversationEntity?

      /**
       * Delete a conversation.
       *
       * **Authorization**: If userId is provided, verifies the user owns the conversation.
       * If userId is null, no authorization check is performed.
       *
       * @param chatId the conversation to delete
       * @param userId optional user ID requesting deletion; if provided, must match conversation owner
       * @return true if the conversation was successfully deleted
       *         false if:
       *         - conversation not found
       *         - userId is provided but doesn't match conversation owner
       */
      fun deleteChatConversation(chatId: UUID, userId: String? = null): Boolean


       /**
        * Add a chat request to a conversation.
        *
        * **Authorization**: If userId is provided, verifies the user owns the conversation.
        * If userId is null, no authorization check is performed.
        *
        * @param userId optional user ID adding the request; if provided, must match conversation owner
        * @param request the request to add
        * @return true if the request was successfully added
        *         false if:
        *         - conversation not found
        *         - userId is provided but doesn't match conversation owner
        */
       fun addChatRequest(userId: String? = null, request: ChatRequestEntity): Boolean

       /**
        * Add a chat request (unscoped - internal use only).
        *
        * **Warning**: This bypasses user isolation checks. Use [addChatRequest] with userId instead.
        */
       fun addChatRequest(request: ChatRequestEntity) = addChatRequest(null, request)

      /**
       * Get chat requests for a conversation.
       *
       * **Authorization**: If userId is provided, returns requests only if userId owns the conversation.
       * If userId is null, no authorization check is performed.
       *
       * @param userId optional user ID requesting the data; if provided, must own the conversation
       * @param chatId the conversation id
       * @return list of all requests for the conversation, or empty if userId is provided but doesn't match owner
       */
      fun getChatRequests(userId: String? = null, chatId: UUID): List<ChatRequestEntity>

      /**
       * Retrieve recent chat requests for a conversation, bounded by character count.
       *
       * Implements strict character budgeting: returns the most recent requests that fit within the budget.
       * If adding the next request would exceed maxRequestCharacters, that request and all older requests
       * are excluded.
       *
       * **Authorization**: If userId is provided, returns requests only if userId owns the conversation.
       * If userId is null, no authorization check is performed.
       *
       * **Typical Use Case**: [AimoChatClientImpl] uses this to stay within LLM context window limits
       * when building prompts from conversation history.
       *
       * @param userId optional user ID requesting the data; if provided, must own the conversation
       * @param chatId the conversation to fetch requests from
       * @param maxRequestCharacters maximum cumulative character count; must be positive.
       *                               Zero or negative returns an empty list
       * @return requests in chronological order that fit within the budget, or empty if:
       *         - userId is provided but doesn't match owner
       *         - no requests fit within the budget
       */
      fun getChatRequests(userId: String? = null, chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity>

      /**
       * Get all messages for a conversation.
       *
       * **Authorization**: If userId is provided, returns messages only if userId owns the conversation.
       * If userId is null, no authorization check is performed.
       *
       * @param userId optional user ID requesting the data; if provided, must own the conversation
       * @param chatId the conversation id
       * @return list of all messages, or empty if userId is provided but doesn't match owner
       */
      fun getMessages(userId: String? = null, chatId: UUID): List<ChatMessageEntity>

      /**
       * Upsert metadata for an existing conversation.
       *
       * **Authorization**: If userId is provided, verifies the user owns the conversation.
       * If userId is null, no authorization check is performed.
       *
       * @param chatId the conversation to update
       * @param userId optional user ID requesting the update; if provided, must match conversation owner
       * @param metadata key/value pairs to insert or update
       * @return true if metadata was successfully updated
       *         false if:
       *         - conversation not found
       *         - userId is provided but doesn't match conversation owner
       */
      fun upsertConversationMetadata(chatId: UUID, userId: String? = null, metadata: Map<String, Any>): Boolean

      /**
       * Delete metadata keys from a conversation.
       *
       * **Authorization**: If userId is provided, verifies the user owns the conversation.
       * If userId is null, no authorization check is performed.
       *
       * @param chatId the conversation to update
       * @param userId optional user ID requesting the deletion; if provided, must match conversation owner
       * @param keys metadata keys to delete
       * @return true if deletion was successful
       *         false if:
       *         - conversation not found
       *         - userId is provided but doesn't match conversation owner
       */
      fun deleteConversationMetadata(chatId: UUID, userId: String? = null, keys: List<String>): Boolean
}
