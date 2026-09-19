package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoHistoryRequest
import java.util.UUID

/**
 * Conversation history storage abstraction scoped to a specific chat.
 *
 * This interface is the **primary contract** for all conversation storage backends in aimo-core.
 * Every storage implementation must satisfy this interface directly; all consumers (including
 * [AimoChatClient][org.ivcode.aimo.core.client.chat.AimoChatClient]) work with Conversation
 * instances rather than storage-specific types.
 *
 * This interface represents persistent conversation storage for a single chat (identified by `chatId`).
 * All operations are scoped to the conversation's `chatId`. Implementations persist messages
 * with request-level grouping (requestId, createdAt) to support both flat message history access
 * and request-grouped history access.
 *
 * Implementations may be wrapped with [ConversationInterceptor] for cross-cutting concerns:
 * - **Security/Access Control**: Enforce scope metadata or user ownership before granting access
 * - **Data Filtering**: Filter or redact conversation data based on user role/permissions
 * - **Caching**: Memoize `getMessages()` calls
 * - **Auditing**: Log all conversation writes
 * - **Data Transformation**: Encryption, schema migration on read/write
 *
 * All implementations are created and managed by [ConversationFactory], which handles scope
 * validation and interceptor composition. Direct implementations may include:
 * - MemoryConversation: In-memory storage (development/testing)
 * - FileConversation: File-backed persistent storage
 * - Future: SQL/NoSQL implementations as needed
 */
interface Conversation {
    /**
     * Unique identifier for the chat associated with this conversation.
     */
    val chatId: UUID

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
     * callers (especially chat clients) to maintain correlation between the response ID returned to
     * the caller and the request ID stored in history, so the UI can reliably map history requests
     * back to live responses.
     *
     * @param requestId The unique request identifier to use for history persistence
     * @param messages messages to append, in the order they should appear in the conversation
     * @param maxCacheCharacters optional character-budget hint for bounded-history persistence
     * @throws IllegalStateException if the messages cannot be persisted
     */
    fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long? = null)

    /**
     * Return persisted chat metadata.
     *
     * Durable chat metadata does not keep a local snapshot.
     * @return map of all metadata properties for this conversation
     */
    fun getChatMetadata(): Map<String, Any>

    /**
     * Get a persisted chat property.
     * @param property the property name to retrieve
     * @return the property value from durable storage, or `null` if not present
     */
    fun getChatProperty(property: String): Any?

    /**
     * Write or update a persisted chat property.
     * @param property the property name
     * @param value the value to store
     */
    fun writeChatProperty(property: String, value: Any)

    /**
     * Delete a persisted chat property.
     * @param property the property name to delete
     * @return true if the property existed and was deleted, false if not found
     */
    fun deleteChatProperty(property: String): Boolean

    /**
     * Write multiple chat properties in a single atomic storage operation.
     *
     * All properties in the map are persisted together. If the implementation
     * fails, no partial writes should occur.
     *
     * @param properties map of property names to values
     */
    fun writeChatProperties(properties: Map<String, Any>)

    /**
     * Delete multiple chat properties in a single atomic storage operation.
     *
     * All listed properties are removed together. If the implementation fails,
     * no partial deletes should occur.
     *
     * @param keys list of property names to delete
     */
    fun deleteChatProperties(keys: List<String>)

    /**
     * Return request-grouped conversation history.
     *
     * Unlike [getMessages] which returns a flat list of all messages, this method
     * returns history grouped by request. Each entry contains the requestId,
     * createdAt timestamp, and the messages persisted together via a single
     * addMessages() call. This is used by HistoryService to reconstruct per-request
     * message grouping and stream-accumulation keys for the frontend.
     *
     * Loads most recent request-groups up to the specified character limit, or all
     * history if limit is null. Character counting includes the full message content.
     *
     * @param maxCacheCharacters optional maximum characters to load from durable storage;
     *                          null means no limit
     * @return list of [AimoHistoryRequest] entries in chronological order (ascending createdAt),
     *         or empty list if no history exists
     */
    fun getHistory(maxCacheCharacters: Long? = null): List<AimoHistoryRequest>
}

