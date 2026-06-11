package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.AimoChatMessage
import java.util.UUID

/**
 * Conversation history storage abstraction scoped to a specific chat.
 *
 * This interface represents persistent conversation storage for a single chat (identified by `chatId`).
 * All operations are scoped to the conversation's `chatId`.
 *
 * Can be wrapped with `ConversationInterceptor` for cross-cutting concerns:
 * - **Security/Access Control**: Enforce user ownership before granting DAO access
 * - **Data Filtering**: Filter or redact conversation data based on user role/permissions
 * - **Caching**: Memoize `getMessages()` calls
 * - **Auditing**: Log all conversation writes
 * - **Data Transformation**: Encryption, schema migration on read/write
 *
 * Implementation note: `ConversationImpl` is the concrete implementation backed by
 * a `ConversationStore` and scoped to a specific `chatId`.
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
     */
    fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long? = null)

    /**
     * Return persisted chat metadata from DAO storage.
     *
     * Durable chat metadata does not keep a local snapshot.
     */
    fun getChatMetadata(): Map<String, Any>

    /**
     * Get a persisted chat property from DAO storage.
     * @param property the property name to retrieve
     * @return the property value from durable storage, or `null` if not present
     */
    fun getChatProperty(property: String): Any?


    /**
     * Write or update a persisted chat property in DAO storage.
     */
    fun writeChatProperty(property: String, value: Any)

    /**
     * Delete a persisted chat property from DAO storage.
     */
    fun deleteChatProperty(property: String): Boolean
}

