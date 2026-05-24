package org.ivcode.aimo.core

import java.util.UUID

/**
 * Represents a client-scoped conversation for interacting with the AIMO chat/assistant service.
 *
 * This API exposes chat metadata: durable conversation metadata persisted in DAO storage and read
 * from that source.
 */
interface AimoConversationClient {
    /**
     * Unique identifier for the chat associated with this conversation.
     */
    val chatId: UUID

    /**
     * Create a chat client bound to this conversation.
     * @return a new or pooled [AimoChatClient] for performing chat interactions.
     */
     fun createChatClient(): AimoChatClient

     /**
      * Return conversation history from durable storage.
      *
      * Loads most recent history up to the specified character limit, or all history if limit is null.
      *
      * @param maxCacheCharacters optional maximum characters to load from durable storage; null means no limit
      * @return conversation messages, or null if no history exists
      */
      fun getMessages(maxCacheCharacters: Long? = null): List<AimoChatMessage>?
      /**
       * Append chat messages to this conversation's history.
       *
       * Implementations should persist the messages to the conversation backing store.
       *
       * The provided requestId is used as the durable request identifier for persistence. This allows
       * callers (especially [AimoChatClient]) to maintain correlation between the response ID
       * returned to the caller and the request ID stored in history, so the UI can reliably map
       * history requests back to live responses.
       *
       * @param requestId The unique request identifier to use for history persistence (typically the responseId from the chat response)
       * @param messages messages to append, in the order they should appear in the conversation
       * @param maxCacheCharacters optional character-budget hint associated with the caller's
       * history/cache management strategy; implementations may use it when applying bounded-history
       * persistence behavior, or ignore it if no such limit is needed
       */
      fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long? = null)

    /**
     * Return persisted chat metadata from DAO storage.
     *
     * Durable chat metadata does not keep a local snapshot in the conversation client.
     */
    fun getChatMetadata(): Map<String, Any>

    /**
     * Read chat metadata from DAO storage (authoritative).
     *
     * This is equivalent to [getChatMetadata] and exists to keep the durable/read naming explicit.
     */
    fun readChatMetadata(): Map<String, Any>

    /**
     * Get a persisted chat property from DAO storage.
     * @param property the property name to retrieve
     * @return the property value from durable storage, or `null` if not present
     */
    fun getChatProperty(property: String): Any?

    /**
     * Read a persisted chat property from DAO storage.
     * @param property the property name to read
     * @return the property value from the datastore, or `null` if it does not exist
     */
    fun readChatProperty(property: String): Any?

    /**
     * Write or update a persisted chat property in DAO storage.
     */
    fun writeChatProperty(property: String, value: Any)

     /**
      * Delete a persisted chat property from DAO storage.
      */
     fun deleteChatProperty(property: String): Boolean
}
