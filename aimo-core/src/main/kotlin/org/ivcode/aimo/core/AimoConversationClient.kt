package org.ivcode.aimo.core

import java.util.UUID

/**
 * Represents a client-scoped conversation for interacting with the AIMO chat/assistant service.
 *
 * This API exposes two metadata domains:
 * - Chat metadata: durable conversation metadata persisted in DAO storage and read from that source.
 * - Runtime metadata: cache-lifetime metadata that is never persisted to DAO storage.
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
     * Append chat messages to this conversation's history.
     *
     * Implementations should persist the messages to the conversation backing store and update
     * any cache layer used for faster history access.
     *
     * @param messages messages to append, in the order they should appear in the conversation
     */
    fun addMessages(messages: List<AimoChatMessage>)

    /**
     * Retrieve cached message history if available.
     *
     * Returns the most recently cached conversation history, or null if not cached.
     * Intended for read/performance optimization; does not fetch from durable storage.
     *
     * @return cached messages, or null if not in cache
     */
    fun getCachedMessages(): List<AimoChatMessage>?

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

    /**
     * Return runtime-only metadata that lives only as long as cache state.
     */
    fun getRuntimeMetadata(): Map<String, Any>

    /**
     * Get a runtime-only property.
     */
    fun getRuntimeProperty(property: String): Any?

    /**
     * Write a runtime-only property (cache-only, not DAO persisted).
     */
    fun writeRuntimeProperty(property: String, value: Any)

    /**
     * Delete a runtime-only property.
     */
    fun deleteRuntimeProperty(property: String): Boolean
}
