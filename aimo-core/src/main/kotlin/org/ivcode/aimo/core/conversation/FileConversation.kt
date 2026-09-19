package org.ivcode.aimo.core.conversation

import tools.jackson.databind.ObjectMapper
import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoHistoryRequest
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * File-backed persistent implementation of Conversation.
 *
 * Stores message history and metadata as JSON files on the filesystem.
 * Each conversation is stored in a dedicated directory containing:
 * - `messages.json`: Array of request groups (each with requestId, createdAt, messages)
 * - `metadata.json`: Object containing chat metadata properties
 *
 * Suitable for single-process deployments and testing scenarios requiring persistence.
 *
 * @param chatId the unique identifier for the chat
 * @param storageDir the root directory where conversation data is stored (one subdirectory per chatId)
 * @param objectMapper the Jackson ObjectMapper to use for JSON serialization (defaults to standard configuration)
 * @param scopeMetadata optional metadata used to validate access; if provided, all
 *                      read operations must match this scope
 */
class FileConversation(
    override val chatId: UUID,
    private val storageDir: File,
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val scopeMetadata: Map<String, Any> = emptyMap()
) : Conversation {
    
    private data class RequestGroup(
        val requestId: UUID,
        val messages: List<AimoChatMessage>,
        val createdAt: Instant
    )
    
    private val conversationDir = File(storageDir, chatId.toString()).apply { mkdirs() }
    private val messagesFile = File(conversationDir, "messages.json")
    private val metadataFile = File(conversationDir, "metadata.json")
    
    // Lock for thread-safe file operations
    private val lock = Object()

    private fun loadRequestGroups(): List<RequestGroup> = synchronized(lock) {
        if (!messagesFile.exists()) return emptyList()
        return try {
            objectMapper.readValue(messagesFile, Array<RequestGroup>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRequestGroups(groups: List<RequestGroup>) = synchronized(lock) {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(messagesFile, groups)
    }

    private fun loadMetadata(): Map<String, Any> = synchronized(lock) {
        if (!metadataFile.exists()) return emptyMap()
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(metadataFile, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveMetadata(metadata: Map<String, Any>) = synchronized(lock) {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataFile, metadata)
    }

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
        val groups = loadRequestGroups()
        if (groups.isEmpty()) return emptyList()
        
        return if (maxCacheCharacters == null) {
            groups.flatMap { it.messages }
        } else {
            val result = mutableListOf<AimoChatMessage>()
            var charCount = 0L
            
            for (group in groups.asReversed()) {
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
        
        val groups = loadRequestGroups().toMutableList()
        groups.add(RequestGroup(
            requestId = requestId,
            messages = messages,
            createdAt = Instant.now()
        ))
        saveRequestGroups(groups)
    }

    override fun getHistory(maxCacheCharacters: Long?): List<AimoHistoryRequest> {
        val groups = loadRequestGroups()
        if (groups.isEmpty()) return emptyList()
        
        return if (maxCacheCharacters == null) {
            groups.map { group ->
                AimoHistoryRequest(
                    chatId = chatId,
                    requestId = group.requestId,
                    messages = group.messages,
                    createdAt = group.createdAt
                )
            }
        } else {
            val result = mutableListOf<AimoHistoryRequest>()
            var charCount = 0L
            
            for (group in groups.asReversed()) {
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

    override fun getChatMetadata(): Map<String, Any> = loadMetadata()

    override fun getChatProperty(property: String): Any? = loadMetadata()[property]

    override fun writeChatProperty(property: String, value: Any) {
        val metadata = loadMetadata().toMutableMap()
        metadata[property] = value
        saveMetadata(metadata)
    }

    override fun deleteChatProperty(property: String): Boolean {
        val metadata = loadMetadata().toMutableMap()
        val existed = metadata.containsKey(property)
        metadata.remove(property)
        if (existed) saveMetadata(metadata)
        return existed
    }

    override fun writeChatProperties(properties: Map<String, Any>) {
        val metadata = loadMetadata().toMutableMap()
        metadata.putAll(properties)
        saveMetadata(metadata)
    }

    override fun deleteChatProperties(keys: List<String>) {
        val metadata = loadMetadata().toMutableMap()
        keys.forEach { metadata.remove(it) }
        saveMetadata(metadata)
    }
}
