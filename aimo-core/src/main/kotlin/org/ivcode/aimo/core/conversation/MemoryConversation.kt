package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoHistoryRequest
import java.time.Instant
import java.util.UUID

/**
 * In-memory implementation of Conversation.
 *
 * Stores message history and metadata in mutable collections. All data is lost when
 * the application terminates. Suitable for development, testing, and single-session
 * use cases where persistence is not required.
 *
 * Message history is grouped per request (requestId, createdAt) internally to support
 * both flat message access via [getMessages] and request-grouped access via [getHistory].
 *
 * Metadata is stored in a mutable Map<String, Any> and persists across method calls
 * within the same application session.
 *
 * @param chatId the unique identifier for the chat
 */
class MemoryConversation(
    override val chatId: UUID
) : Conversation {
    
    // Request grouping: maintain insertion order and per-request message grouping
    private data class RequestGroup(
        val requestId: UUID,
        val messages: MutableList<AimoChatMessage>,
        val createdAt: Instant
    )
    
    private val requestGroups = mutableListOf<RequestGroup>()
    private val metadata = mutableMapOf<String, Any>()
    private val lock = Object()

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? = synchronized(lock) {
        if (requestGroups.isEmpty()) return emptyList()
        
        return if (maxCacheCharacters == null) {
            // Return all messages in order
            requestGroups.flatMap { it.messages }
        } else {
            // Return most recent messages up to character budget, working backward
            val result = mutableListOf<AimoChatMessage>()
            var charCount = 0L
            
            for (group in requestGroups.asReversed()) {
                for (msg in group.messages.asReversed()) {
                    val msgSize = (msg.content?.length ?: 0) + (msg.thinking?.length ?: 0)
                    if (charCount + msgSize > maxCacheCharacters) {
                        return result.asReversed()
                    }
                    result.add(msg)
                    charCount += msgSize
                }
            }
            result.asReversed()
        }
    }

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>) {
        if (messages.isEmpty()) return
        
        synchronized(lock) {
            // Group messages by request
            requestGroups.add(RequestGroup(
                requestId = requestId,
                messages = messages.toMutableList(),
                createdAt = Instant.now()
            ))
        }
    }

    override fun getHistory(maxCacheCharacters: Long?): List<AimoHistoryRequest> = synchronized(lock) {
        if (requestGroups.isEmpty()) return emptyList()
        
        return if (maxCacheCharacters == null) {
            // Return all request groups
            requestGroups.map { group ->
                AimoHistoryRequest(
                    chatId = chatId,
                    requestId = group.requestId,
                    messages = group.messages,
                    createdAt = group.createdAt
                )
            }
        } else {
            // Return most recent request groups up to character budget
            val result = mutableListOf<AimoHistoryRequest>()
            var charCount = 0L
            
            for (group in requestGroups.asReversed()) {
                val groupSize = group.messages.sumOf { msg ->
                    (msg.content?.length ?: 0) + (msg.thinking?.length ?: 0)
                }
                
                if (charCount + groupSize > maxCacheCharacters) {
                    return result.asReversed()
                }
                
                result.add(AimoHistoryRequest(
                    chatId = chatId,
                    requestId = group.requestId,
                    messages = group.messages,
                    createdAt = group.createdAt
                ))
                charCount += groupSize
            }
            result.asReversed()
        }
    }

    override fun getChatMetadata(): Map<String, Any> = synchronized(lock) {
        metadata.toMap()
    }

    override fun getChatProperty(property: String): Any? = synchronized(lock) {
        metadata[property]
    }

    override fun writeChatProperty(property: String, value: Any) {
        synchronized(lock) {
            metadata[property] = value
        }
    }

    override fun deleteChatProperty(property: String): Boolean = synchronized(lock) {
        metadata.remove(property) != null
    }

    override fun writeChatProperties(properties: Map<String, Any>) {
        synchronized(lock) {
            metadata.putAll(properties)
        }
    }

    override fun deleteChatProperties(keys: List<String>) {
        synchronized(lock) {
            keys.forEach { metadata.remove(it) }
        }
    }
}
