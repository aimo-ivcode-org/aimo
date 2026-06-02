package org.ivcode.aimo.core

import java.util.UUID

/**
 * Core orchestration interface for the aimo chat system.
 *
 * Aimo is the primary entry point for managing AI-powered conversations. It provides two sets of methods:
 *
 * 1. **User-scoped methods** (require userId): Enforce strict user isolation and authorization
 * 2. **Admin methods** (no userId): Bypass user isolation for administrative operations
 *
 * Aimo coordinates:
 * - **Conversation Lifecycle**: Create, retrieve, delete conversations
 * - **Chat Operations**: Access conversation clients for sending messages and handling chat
 * - **Message History**: Track and retrieve conversation history
 * - **User Isolation**: Enforce user ownership on all user-scoped operations
 * - **LLM Integration**: Route requests to configured language models via [AimoChatModel]
 *
 * ## Architecture
 *
 * Aimo delegates to two main components:
 *
 * 1. **[AimoChatClientDao]**: Persistence layer
 *    - Stores conversations, requests, and messages
 *    - Enforces user isolation at the storage layer
 *    - Two implementations: in-memory (testing) and file-based (durability)
 *
 * 2. **[AimoChatModel]**: LLM integration
 *    - Executes chat requests against the configured language model
 *    - Supports streaming and tool calling
 *    - Provider-specific implementations: Ollama, Bedrock, etc.
 *
 * ## User-Scoped Operations
 *
 * All user-scoped operations strictly enforce user ownership:
 * - **Conversation Creation**: Requires a userId; each conversation belongs to one user
 * - **Conversation Access**: userId parameter must match conversation owner
 * - **Message History**: Scoped to the requesting user
 *
 * Example:
 * ```kotlin
 * // Create conversation for "alice"
 * val convInfo = aimo.createConversation("alice")  // ✅ succeeds
 *
 * // Access conversation (must provide matching userId)
 * val client = aimo.getConversationClient(convInfo.chatId, "alice")   // ✅ owner access
 * val denied = aimo.getConversationClient(convInfo.chatId, "bob")     // ❌ denied
 * ```
 *
 * ## Typical Usage (User-Scoped)
 *
 * ```kotlin
 * // 1. Create conversation
 * val conv = aimo.createConversation(userId = "user123")
 *
 * // 2. Get conversation client
 * val client = aimo.getConversationClient(conv.chatId, userId = "user123")
 *    ?: throw IllegalStateException("Conversation not found")
 *
 * // 3. Send chat request
 * val response = client.chat(
 *     AimoChatRequest(
 *         prompt = "What is 2+2?",
 *         context = emptyMap()
 *     )
 * )
 *
 * // 4. Retrieve history
 * val history = aimo.getChatHistory(conv.chatId, userId = "user123")
 * ```
 *
 * ## Admin Operations
 *
 * Admin methods (suffixed with "Admin") bypass user isolation for administrative tasks.
 * These should only be exposed through authenticated admin-only endpoints.
 *
 * **SECURITY WARNING**: Admin operations completely bypass user isolation. Never expose these
 * methods to untrusted callers.
 *
 * Example:
 * ```kotlin
 * // Admin bypass: access any conversation without user check
 * val adminClient = aimo.getConversationClientAdmin(anyConversationId)
 * val allConversations = aimo.getConversationsAdmin()
 * val deleted = aimo.deleteConversationAdmin(anyConversationId)
 * ```
 *
 * ## Implementation
 *
 * Aimo is typically constructed via [org.ivcode.aimo.core.conf.AimoConfig.createAimo] with:
 * - A configured [AimoChatModel]
 * - A [AimoChatClientDao] implementation
 * - Optional [org.ivcode.aimo.core.model.AimoToolCallback] tools
 * - Optional [org.ivcode.aimo.core.controller.SystemMessageCallback] system messages
 *
 * @see AimoConversationClient Chat client for individual conversations
 * @see AimoChatClientDao Persistence layer
 * @see AimoChatModel LLM integration
 */
interface Aimo {
    /**
     * Get a client for an existing conversation to send and receive messages.
     *
     * The client provides methods to:
     * - Send chat requests ([AimoConversationClient.chat])
     * - Stream chat responses ([AimoConversationClient.chatStream])
     * - Manage conversation metadata ([AimoConversationClient.writeChatProperty], [AimoConversationClient.getChatMetadata])
     *
     * **Authorization**: This method verifies the user owns the conversation before granting access.
     *
     * @param chatId the unique identifier of the conversation
     * @param userId user ID requesting access; must match conversation owner
     * @return a [AimoConversationClient] for this conversation, or null if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     */
    fun getConversationClient(chatId: UUID, userId: String): AimoConversationClient?

    /**
     * Create a new conversation.
     *
     * Each conversation is owned by a specific user and cannot be transferred.
     * Conversations persist until explicitly deleted.
     *
     * @param userId the user ID that owns this conversation (required, non-null)
     * @return information about the newly created conversation including its unique chatId.
     *         Use the chatId with [getConversationClient] to start chatting.
     * @throws IllegalArgumentException if userId is null or empty (implementation-specific)
     */
    fun createConversation(userId: String): AimoConversationInfo

    /**
     * Get all conversations owned by a user.
     *
     * Returns only conversations where the user is the owner.
     *
     * @param userId the user ID to list conversations for (required)
     * @return list of conversations owned by the userId, or empty list if user has no conversations
     */
    fun getConversations(userId: String): List<AimoConversationInfo>

    /**
     * Delete a conversation and all of its message history.
     *
     * This operation is permanent and cannot be undone.
     *
     * **Authorization**: This method verifies the user owns the conversation before deletion.
     *
     * @param chatId the conversation to delete
     * @param userId user ID requesting deletion; must match conversation owner
     * @return true if the conversation was successfully deleted
     *         false if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     */
    fun deleteConversation(chatId: UUID, userId: String): Boolean

    /**
     * Get the message history for a conversation.
     *
     * Returns all requests (user questions) and their responses (assistant messages, tool results)
     * for the specified conversation in chronological order.
     *
     * **Authorization**: This method verifies the user owns the conversation before returning history.
     *
     * @param chatId the conversation to retrieve history for
     * @param userId user ID requesting the history; must match conversation owner
     * @return list of all requests and responses in chronological order, or empty list if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     *         - conversation has no history
     */
    fun getChatHistory(chatId: UUID, userId: String): List<AimoHistoryRequest>

    /**
     * Update metadata for an existing conversation identified by [chatId].
     *
     * Metadata allows storing arbitrary key-value pairs associated with a conversation
     * (e.g., custom title, tags, user-defined properties).
     *
     * This does not create a new conversation when [chatId] is missing.
     *
     * **Authorization**: This method verifies the user owns the conversation before updating metadata.
     *
     * @param chatId the conversation to update
     * @param metadata key/value pairs to insert or update on the conversation
     * @param userId user ID requesting the update; must match conversation owner
     * @return true if metadata was successfully updated
     *         false if:
     *         - conversation not found
     *         - userId doesn't match conversation owner
     */
    fun upsertConversation(chatId: UUID, metadata: Map<String, String>, userId: String): Boolean

    // ============================================
    // Admin methods (no user isolation)
    // ============================================

    /**
     * Get a client for any conversation without user authorization checks.
     *
     * This is an admin-only operation that bypasses user isolation.
     * The returned client will not enforce user ownership validation.
     *
     * **SECURITY WARNING**: This method bypasses all user authorization. Only expose through admin endpoints.
     *
     * @param chatId the unique identifier of the conversation
     * @return an [AimoConversationClient] for this conversation, or null if conversation not found
     *
     * @see getConversationClient User version requiring userId
     */
    fun getConversationClientAdmin(chatId: UUID): AimoConversationClient?

    /**
     * Get all conversations regardless of owner.
     *
     * This is an admin-only operation that returns every conversation in the system.
     *
     * **SECURITY WARNING**: This method bypasses all user authorization. Only expose through admin endpoints.
     *
     * @return list of all conversations
     *
     * @see getConversations User version limited to specific userId
     */
    fun getConversationsAdmin(): List<AimoConversationInfo>

    /**
     * Delete any conversation without user authorization checks.
     *
     * This is an admin-only operation that can delete any conversation regardless of owner.
     * This operation is permanent and cannot be undone.
     *
     * **SECURITY WARNING**: This method bypasses all user authorization. Only expose through admin endpoints.
     *
     * @param chatId the conversation to delete
     * @return true if the conversation was successfully deleted, false if not found
     *
     * @see deleteConversation User version requiring userId match
     */
    fun deleteConversationAdmin(chatId: UUID): Boolean

    /**
     * Get the message history for any conversation without user authorization checks.
     *
     * This is an admin-only operation that returns all requests and responses regardless of conversation owner.
     *
     * **SECURITY WARNING**: This method bypasses all user authorization. Only expose through admin endpoints.
     *
     * @param chatId the conversation to retrieve history for
     * @return list of all requests and responses in chronological order, or empty list if conversation not found
     *
     * @see getChatHistory User version requiring userId match
     */
    fun getChatHistoryAdmin(chatId: UUID): List<AimoHistoryRequest>

    /**
     * Update metadata for any conversation without user authorization checks.
     *
     * This is an admin-only operation that can modify metadata of any conversation.
     *
     * **SECURITY WARNING**: This method bypasses all user authorization. Only expose through admin endpoints.
     *
     * @param chatId the conversation to update
     * @param metadata key/value pairs to insert or update on the conversation
     * @return true if metadata was successfully updated, false if conversation not found
     *
     * @see upsertConversation User version requiring userId match
     */
    fun upsertConversationAdmin(chatId: UUID, metadata: Map<String, String>): Boolean
}