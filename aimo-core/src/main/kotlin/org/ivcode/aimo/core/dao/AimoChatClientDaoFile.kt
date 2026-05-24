package org.ivcode.aimo.core.dao

import tools.jackson.databind.ObjectMapper
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * File-based persistent implementation of [AimoChatClientDao].
 *
 * Stores conversations and messages as JSON files in a directory structure:
 * - `basePath/conversations/{chatId}.json` - conversation metadata
 * - `basePath/messages/{chatId}.json` - all messages for a chat
 *
 * Thread-safe within a single JVM process using file locks for metadata operations.
 * Not recommended for multi-process or clustered deployments.
 *
 * @param basePath The root directory where conversations and messages will be stored.
 *                 Will be created if it does not exist.
 */
class AimoChatClientDaoFile(basePath: Path) : AimoChatClientDao {

    private val basePath = basePath.normalize()
    private val conversationsPath: Path = basePath.resolve("conversations")
    private val messagesPath: Path = basePath.resolve("messages")

    private val mapper: ObjectMapper = ObjectMapper()

    private val lock = Any()

    init {
        conversationsPath.createDirectories()
        messagesPath.createDirectories()
    }

    override fun createChatConversation(): ChatConversationEntity {
        val chatId = UUID.randomUUID()
        val conversation = ChatConversationEntity(
            chatId = chatId,
            metadata = emptyMap()
        )
        saveConversation(conversation)
        return conversation
    }

    override fun createChatConversation(metadata: Map<String, String>): ChatConversationEntity {
        val chatId = UUID.randomUUID()
        val conversation = ChatConversationEntity(
            chatId = chatId,
            metadata = metadata
        )
        saveConversation(conversation)
        return conversation
    }

    override fun getChatConversations(): List<ChatConversationEntity> {
        return synchronized(lock) {
            if (!conversationsPath.exists()) {
                return emptyList()
            }
            conversationsPath.listDirectoryEntries("*.json")
                .mapNotNull { path ->
                    try {
                        mapper.readValue(path.readText(), ChatConversationEntity::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                .toList()
        }
    }

    override fun getChatConversation(chatId: UUID): ChatConversationEntity? {
        return synchronized(lock) {
            loadConversation(chatId)
        }
    }

    override fun getChatConversation(chatId: UUID, metadata: Map<String, String>): ChatConversationEntity? {
        return synchronized(lock) {
            val conversation = loadConversation(chatId) ?: return null

            for ((key, value) in metadata) {
                if (conversation.metadata[key] != value) {
                    return null
                }
            }

            return conversation
        }
    }

    override fun deleteChatConversation(chatId: UUID): Boolean {
        return synchronized(lock) {
            val conversationFile = conversationFile(chatId)
            val messagesFile = messagesFile(chatId)

            val conversationDeleted = conversationFile.toFile().delete()
            messagesFile.toFile().delete()

            return conversationDeleted
        }
    }

    override fun deleteChatConversation(chatId: UUID, metadata: Map<String, String>): Boolean {
        return synchronized(lock) {
            val conversation = loadConversation(chatId) ?: return false

            for ((key, value) in metadata) {
                if (conversation.metadata[key] != value) {
                    return false
                }
            }

            conversationFile(chatId).toFile().delete()
            messagesFile(chatId).toFile().delete()
            return true
        }
    }

    override fun addChatRequest(request: ChatRequestEntity) {
        synchronized(lock) {
            val requests = loadRequests(request.chatId).toMutableList()
            requests.add(request)
            saveRequests(request.chatId, requests)
        }
    }

    override fun getChatRequests(chatId: UUID): List<ChatRequestEntity> {
        return synchronized(lock) {
            loadRequests(chatId)
        }
    }

    override fun getChatRequests(chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity> {
        return synchronized(lock) {
            if (maxRequestCharacters <= 0) {
                return emptyList()
            }

            val chatRequests = loadRequests(chatId)
            if (chatRequests.isEmpty()) {
                return emptyList()
            }

            var totalCharacters = 0L
            val maxCharacters = maxRequestCharacters.toLong()
            val selected = mutableListOf<ChatRequestEntity>()

            // Pick newest requests first until adding the next would exceed the budget.
            for (request in chatRequests.asReversed()) {
                val requestCharacters = request.requestCharacters.toLong()
                if (totalCharacters + requestCharacters > maxCharacters) {
                    break
                }

                selected.add(request)
                totalCharacters += requestCharacters
            }

            return selected.asReversed()
        }
    }

    override fun getMessages(chatId: UUID): List<ChatMessageEntity> {
        return synchronized(lock) {
            loadRequests(chatId).flatMap { it.messages }
        }
    }

    override fun upsertConversationMetadata(chatId: UUID, metadata: Map<String, Any>): Boolean {
        return synchronized(lock) {
            val existing = loadConversation(chatId) ?: return false

            if (metadata.isEmpty()) {
                return true
            }

            val merged = existing.metadata.toMutableMap()
            for ((k, v) in metadata) {
                merged[k] = v
            }

            saveConversation(existing.copy(metadata = merged.toMap()))
            return true
        }
    }

    override fun deleteConversationMetadata(chatId: UUID, keys: List<String>): Boolean {
        return synchronized(lock) {
            val existing = loadConversation(chatId) ?: return false

            if (keys.isEmpty()) {
                return true
            }

            val updated = existing.metadata.toMutableMap()
            for (k in keys) {
                updated.remove(k)
            }

            saveConversation(existing.copy(metadata = updated.toMap()))
            return true
        }
    }

    private fun conversationFile(chatId: UUID): Path {
        return conversationsPath.resolve("$chatId.json")
    }

    private fun messagesFile(chatId: UUID): Path {
        return messagesPath.resolve("$chatId.json")
    }

    private fun saveConversation(conversation: ChatConversationEntity) {
        val jsonString = mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(conversation)
        conversationFile(conversation.chatId).writeText(jsonString)
    }

    private fun loadConversation(chatId: UUID): ChatConversationEntity? {
        return try {
            val file = conversationFile(chatId)
            if (!file.exists()) {
                return null
            }
            mapper.readValue(file.readText(), ChatConversationEntity::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveRequests(chatId: UUID, requests: List<ChatRequestEntity>) {
        val jsonString = mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(requests)
        messagesFile(chatId).writeText(jsonString)
    }

    private fun loadRequests(chatId: UUID): List<ChatRequestEntity> {
        return try {
            val file = messagesFile(chatId)
            if (!file.exists()) {
                return emptyList()
            }
            mapper.readValue(
                file.readText(),
                mapper.typeFactory.constructCollectionType(List::class.java, ChatRequestEntity::class.java)
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}






