package org.ivcode.aimo.core

import java.util.UUID

/**
 * Core orchestration interface for the aimo chat system.
 *
 * Aimo is the main entry point for managing AI-powered conversations. It coordinates:
 * - **Conversation Lifecycle**: Create, retrieve, delete conversations
 * - **Chat Operations**: Access conversation clients for sending messages and handling chat
 * - **Message History**: Track and retrieve conversation history
 * - **User Isolation**: Enforce user ownership and access control on all operations
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
 * ## User Isolation
 *
 * All Aimo operations respect user ownership:
 * - **Conversation Creation**: Requires a userId; each conversation belongs to one user
 * - **Conversation Access**: userId must match conversation owner (null userId denied)
 * - **Message History**: Scoped to the requesting user
 *
 * Example:
 * ```kotlin
 * // Create conversation for "alice"
 * val convInfo = aimo.createConversation("alice")  // ✅ succeeds
 * aimo.createConversation(null)                     // ❌ fails - null userId rejected
 *
 * // Access conversation (must provide matching userId)
 * val client = aimo.getConversationClient(convInfo.chatId, "alice")   // ✅ owner access
 * val denied = aimo.getConversationClient(convInfo.chatId, "bob")     // ❌ denied
 * val denied = aimo.getConversationClient(convInfo.chatId, null)      // ❌ denied
 * ```
 *
 * ## Typical Usage
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
 * ## Implementation
 *
 * Aimo is typically constructed via [org.ivcode.aimo.core.AimoFactory.create] with:
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
     * **Authorization**: If userId is provided, this method verifies the user owns the conversation.
     * If userId is null, no authorization check is performed.
     *
     * @param chatId the unique identifier of the conversation
     * @param userId optional user ID requesting access; if provided, must match conversation owner
     * @return a [AimoConversationClient] for this conversation, or null if:
     *         - conversation not found
     *         - userId is provided but doesn't match conversation owner
     */
    fun getConversationClient(chatId: UUID, userId: String? = null): AimoConversationClient?

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
     * @param userId the user ID to list conversations for; if null, returns empty list (no admin access)
     * @return list of conversations owned by the userId, or empty list if userId is null or has no conversations
     */
    fun getConversations(userId: String? = null): List<AimoConversationInfo>

    /**
     * Delete a conversation and all of its message history.
     *
     * This operation is permanent and cannot be undone.
     *
     * **Authorization**: If userId is provided, this method verifies the user owns the conversation.
     * If userId is null, no authorization check is performed and any conversation can be deleted.
     *
     * @param chatId the conversation to delete
     * @param userId optional user ID requesting deletion; if provided, must match conversation owner
     * @return true if the conversation was successfully deleted
     *         false if:
     *         - conversation not found
     *         - userId is provided but doesn't match conversation owner
     */
    fun deleteConversation(chatId: UUID, userId: String? = null): Boolean

    /**
     * Get the message history for a conversation.
     *
     * Returns all requests (user questions) and their responses (assistant messages, tool results)
     * for the specified conversation in chronological order.
     *
     * **Authorization**: If userId is provided, this method verifies the user owns the conversation.
     * If userId is null, no authorization check is performed and history is returned regardless.
     *
     * @param chatId the conversation to retrieve history for
     * @param userId optional user ID requesting the history; if provided, must match conversation owner
     * @return list of all requests and responses in chronological order, or empty list if:
     *         - conversation not found
     *         - userId is provided but doesn't match conversation owner
     *         - conversation has no history
     */
    fun getChatHistory(chatId: UUID, userId: String? = null): List<AimoHistoryRequest>

    /**
     * Update metadata for an existing conversation identified by [chatId].
     *
     * Metadata allows storing arbitrary key-value pairs associated with a conversation
     * (e.g., custom title, tags, user-defined properties).
     *
     * This does not create a new conversation when [chatId] is missing.
     *
     * **Authorization**: If userId is provided, this method verifies the user owns the conversation.
     * If userId is null, no authorization check is performed.
     *
     * @param chatId the conversation to update
     * @param metadata key/value pairs to insert or update on the conversation
     * @param userId optional user ID requesting the update; if provided, must match conversation owner
     * @return true if metadata was successfully updated
     *         false if:
     *         - conversation not found
     *         - userId is provided but doesn't match conversation owner
     */
    fun upsertConversation(chatId: UUID, metadata: Map<String, String>, userId: String? = null): Boolean
}