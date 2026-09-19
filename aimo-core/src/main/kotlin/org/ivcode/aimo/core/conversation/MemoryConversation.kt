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
 * @param scopeMetadata optional metadata used to validate access; if provided, all
 *                      read operations must match this scope
 */
class MemoryConversation(
    override val chatId: UUID,
    private val scopeMetadata: Map<String, Any> = emptyMap()
) : Conversation {
    
    // Request grouping: maintain insertion order and per-request message grouping
    private data class RequestGroup(
        val requestId: UUID,
        val messages: MutableList<AimoChatMessage>,
        val createdAt: Instant
    )
    
    private val requestGroups = mutableListOf<RequestGroup>()
    private val metadata = mutableMapOf<String, Any>()

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
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
                    if (charCount + msgSize > maxCacheCharacters && result.isNotEmpty()) {
                        return result.asReversed()
                    }
                    result.add(msg)
                    charCount += msgSize
                }
            }
            result.asReversed()
        }
    }

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) {
        if (messages.isEmpty()) return
        
        // Group messages by request
        requestGroups.add(RequestGroup(
            requestId = requestId,
            messages = messages.toMutableList(),
            createdAt = Instant.now()
        ))
    }

    override fun getHistory(maxCacheCharacters: Long?): List<AimoHistoryRequest> {
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
                
                if (charCount + groupSize > maxCacheCharacters && result.isNotEmpty()) {
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

    override fun getChatMetadata(): Map<String, Any> = metadata.toMap()

    override fun getChatProperty(property: String): Any? = metadata[property]

    override fun writeChatProperty(property: String, value: Any) {
        metadata[property] = value
    }

    override fun deleteChatProperty(property: String): Boolean {
        return metadata.remove(property) != null
    }

    override fun writeChatProperties(properties: Map<String, Any>) {
        metadata.putAll(properties)
    }

    override fun deleteChatProperties(keys: List<String>) {
        keys.forEach { metadata.remove(it) }
    }
}
