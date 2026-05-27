package org.ivcode.aimo.core.dao

import tools.jackson.databind.ObjectMapper
import java.io.File
import java.util.UUID

/**
 * File-based implementation of [AimoChatClientDao].
 *
 * Persists conversations and requests to the filesystem in JSON format.
 * Directory structure:
 * ```
 * dataDir/
 *   <chatId>/
 *     metadata.json (ChatConversationEntity)
 *     requests/
 *       <requestId>.json (ChatRequestEntity)
 * ```
 *
 * Thread-safe via synchronized access patterns.
 *
 * @param dataDir the root directory where all chat data is persisted
 * @param objectMapper JSON serializer
 */
class AimoChatClientDaoFile(
    private val dataDir: File,
    private val objectMapper: ObjectMapper
) : AimoChatClientDao {

    private val lock = Any()

    init {
        dataDir.mkdirs()
    }

    // ===== Helper methods for file operations =====

    private fun getChatDir(chatId: UUID): File = File(dataDir, chatId.toString())
    private fun getMetadataFile(chatId: UUID): File = File(getChatDir(chatId), "metadata.json")
    private fun getRequestsDir(chatId: UUID): File = File(getChatDir(chatId), "requests")
    private fun getRequestFile(chatId: UUID, requestId: UUID): File = File(getRequestsDir(chatId), "$requestId.json")

    private fun loadConversation(file: File): ChatConversationEntity? {
        return if (file.exists()) {
            try {
                objectMapper.readValue(file, ChatConversationEntity::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun saveConversation(file: File, conversation: ChatConversationEntity) {
        file.parentFile?.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, conversation)
    }

    private fun loadRequest(file: File): ChatRequestEntity? {
        return if (file.exists()) {
            try {
                objectMapper.readValue(file, ChatRequestEntity::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun saveRequest(file: File, request: ChatRequestEntity) {
        file.parentFile?.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, request)
    }

    private fun canAccess(conversation: ChatConversationEntity, userId: String?): Boolean {
        // If userId is null, allow access (no authorization check)
        if (userId == null) return true
        // If userId is provided, verify it matches conversation owner
        return conversation.userId == userId
    }

    // ===== AimoChatClientDao Implementation =====

    override fun createChatConversation(userId: String): ChatConversationEntity {
        synchronized(lock) {
            val chatId = UUID.randomUUID()
            val conversation = ChatConversationEntity(
                chatId = chatId,
                userId = userId,
                metadata = mapOf()
            )
            saveConversation(getMetadataFile(chatId), conversation)
            return conversation
        }
    }

    override fun createChatConversation(userId: String, metadata: Map<String, String>): ChatConversationEntity {
        synchronized(lock) {
            val chatId = UUID.randomUUID()
            val conversation = ChatConversationEntity(
                chatId = chatId,
                userId = userId,
                metadata = metadata
            )
            saveConversation(getMetadataFile(chatId), conversation)
            return conversation
        }
    }

    override fun getChatConversations(userId: String?): List<ChatConversationEntity> {
        synchronized(lock) {
            val conversations = mutableListOf<ChatConversationEntity>()
            dataDir.listFiles { file -> file.isDirectory }?.forEach { chatDir ->
                val metadataFile = File(chatDir, "metadata.json")
                loadConversation(metadataFile)?.let { conversation ->
                    if (userId == null || conversation.userId == userId) {
                        conversations.add(conversation)
                    }
                }
            }
            return conversations
        }
    }

    override fun getChatConversation(chatId: UUID): ChatConversationEntity? {
        synchronized(lock) {
            return loadConversation(getMetadataFile(chatId))
        }
    }

    override fun getChatConversation(chatId: UUID, userId: String?): ChatConversationEntity? {
        synchronized(lock) {
            val conversation = loadConversation(getMetadataFile(chatId)) ?: return null
            return if (canAccess(conversation, userId)) conversation else null
        }
    }

    override fun deleteChatConversation(chatId: UUID, userId: String?): Boolean {
        synchronized(lock) {
            val conversation = loadConversation(getMetadataFile(chatId)) ?: return false
            if (!canAccess(conversation, userId)) return false

            getChatDir(chatId).deleteRecursively()
            return true
        }
    }

    override fun addChatRequest(userId: String?, request: ChatRequestEntity): Boolean {
        synchronized(lock) {
            val conversation = loadConversation(getMetadataFile(request.chatId)) ?: return false
            if (!canAccess(conversation, userId)) return false

            saveRequest(getRequestFile(request.chatId, request.requestId), request)
            return true
        }
    }

    override fun getChatRequests(userId: String?, chatId: UUID): List<ChatRequestEntity> {
        synchronized(lock) {
            val conversation = loadConversation(getMetadataFile(chatId)) ?: return emptyList()
            if (!canAccess(conversation, userId)) return emptyList()

            val requestsDir = getRequestsDir(chatId)
            if (!requestsDir.exists()) return emptyList()

            return requestsDir.listFiles()
                ?.mapNotNull { file -> loadRequest(file) }
                ?.sortedBy { it.createdAt }
                ?: emptyList()
        }
    }

    override fun getChatRequests(userId: String?, chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity> {
        synchronized(lock) {
            val conversation = loadConversation(getMetadataFile(chatId)) ?: return emptyList()
            if (!canAccess(conversation, userId)) return emptyList()

            if (maxRequestCharacters <= 0) {
                return emptyList()
            }

            val requestsDir = getRequestsDir(chatId)
            if (!requestsDir.exists()) return emptyList()

            val allRequests = requestsDir.listFiles()
                ?.mapNotNull { file -> loadRequest(file) }
                ?.sortedBy { it.createdAt }
                ?: return emptyList()

            if (allRequests.isEmpty()) {
                return emptyList()
            }

            var totalCharacters = 0L
            val maxCharacters = maxRequestCharacters.toLong()
            val selected = mutableListOf<ChatRequestEntity>()

            // Pick newest requests first until adding the next would exceed the budget.
            for (request in allRequests.asReversed()) {
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

    override fun getMessages(userId: String?, chatId: UUID): List<ChatMessageEntity> {
        synchronized(lock) {
            val conversation = loadConversation(getMetadataFile(chatId)) ?: return emptyList()
            if (!canAccess(conversation, userId)) return emptyList()

            val requests = getChatRequests(userId, chatId)
            return requests.flatMap { it.messages }
        }
    }

    override fun upsertConversationMetadata(chatId: UUID, userId: String?, metadata: Map<String, Any>): Boolean {
        synchronized(lock) {
            val existing = loadConversation(getMetadataFile(chatId)) ?: return false
            if (!canAccess(existing, userId)) return false

            if (metadata.isEmpty()) return true

            val merged = existing.metadata.toMutableMap()
            for ((k, v) in metadata) {
                merged[k] = v
            }
            saveConversation(getMetadataFile(chatId), existing.copy(metadata = merged.toMap()))
            return true
        }
    }

    override fun deleteConversationMetadata(chatId: UUID, userId: String?, keys: List<String>): Boolean {
        synchronized(lock) {
            val existing = loadConversation(getMetadataFile(chatId)) ?: return false
            if (!canAccess(existing, userId)) return false

            if (keys.isEmpty()) return true

            val updated = existing.metadata.toMutableMap()
            for (k in keys) {
                updated.remove(k)
            }

            saveConversation(getMetadataFile(chatId), existing.copy(metadata = updated.toMap()))
            return true
        }
    }
}

