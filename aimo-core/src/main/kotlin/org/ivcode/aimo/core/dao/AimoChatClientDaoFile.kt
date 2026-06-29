package org.ivcode.aimo.core.dao

import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.util.UUID

class AimoChatClientDaoFile(
    private val dataDir: File,
    private val objectMapper: ObjectMapper
) : AimoChatClientDao {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = Any()

    init {
        dataDir.mkdirs()
    }

    private fun getChatDir(chatId: UUID): File = File(dataDir, chatId.toString())
    private fun getMetadataFile(chatId: UUID): File = File(getChatDir(chatId), "metadata.json")
    private fun getRequestsDir(chatId: UUID): File = File(getChatDir(chatId), "requests")
    private fun getRequestFile(chatId: UUID, requestId: UUID): File = File(getRequestsDir(chatId), "$requestId.json")

    private fun loadConversation(file: File): ChatConversationEntity? {
        return if (file.exists()) {
            try {
                objectMapper.readValue(file, ChatConversationEntity::class.java)
            } catch (e: Exception) {
                log.warn("Failed to deserialize conversation metadata from {}: {}", file.absolutePath, e.message, e)
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
                log.warn("Failed to deserialize chat request from {}: {}", file.absolutePath, e.message, e)
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

    override fun createChatConversation(metadata: Map<String, Any>): ChatConversationEntity {
        synchronized(lock) {
            val chatId = UUID.randomUUID()
            val conversation = ChatConversationEntity(
                chatId = chatId,
                metadata = metadata,
            )
            saveConversation(getMetadataFile(chatId), conversation)
            return conversation
        }
    }

    override fun getChatConversations(scopeMetadata: Map<String, Any>): List<ChatConversationEntity> {
        synchronized(lock) {
            val conversations = mutableListOf<ChatConversationEntity>()
            dataDir.listFiles { file -> file.isDirectory }?.forEach { chatDir ->
                val metadataFile = File(chatDir, "metadata.json")
                loadConversation(metadataFile)?.let { conversation ->
                    if (ConversationMetadataMatcher.matches(conversation.metadata, scopeMetadata)) {
                        conversations.add(conversation)
                    }
                }
            }
            return conversations
        }
    }

    override fun getChatConversation(chatId: UUID, scopeMetadata: Map<String, Any>): ChatConversationEntity? {
        synchronized(lock) {
            return getConversationIfMatches(chatId, scopeMetadata)
        }
    }

    override fun deleteChatConversation(chatId: UUID, scopeMetadata: Map<String, Any>): Boolean {
        synchronized(lock) {
            if (getConversationIfMatches(chatId, scopeMetadata) == null) return false
            getChatDir(chatId).deleteRecursively()
            return true
        }
    }

    override fun addChatRequest(request: ChatRequestEntity, scopeMetadata: Map<String, Any>): Boolean {
        synchronized(lock) {
            if (getConversationIfMatches(request.chatId, scopeMetadata) == null) return false
            saveRequest(getRequestFile(request.chatId, request.requestId), request)
            return true
        }
    }

    override fun getChatRequests(chatId: UUID, scopeMetadata: Map<String, Any>): List<ChatRequestEntity> {
        synchronized(lock) {
            if (getConversationIfMatches(chatId, scopeMetadata) == null) return emptyList()

            val requestsDir = getRequestsDir(chatId)
            if (!requestsDir.exists()) return emptyList()

            return requestsDir.listFiles()
                ?.mapNotNull { file -> loadRequest(file) }
                ?.sortedBy { it.createdAt }
                ?: emptyList()
        }
    }

    override fun getChatRequests(
        chatId: UUID,
        maxRequestCharacters: Long,
        scopeMetadata: Map<String, Any>,
    ): List<ChatRequestEntity> {
        synchronized(lock) {
            if (getConversationIfMatches(chatId, scopeMetadata) == null) return emptyList()
            if (maxRequestCharacters <= 0) return emptyList()

            val requestsDir = getRequestsDir(chatId)
            if (!requestsDir.exists()) return emptyList()

            val allRequests = requestsDir.listFiles()
                ?.mapNotNull { file -> loadRequest(file) }
                ?.sortedBy { it.createdAt }
                ?: return emptyList()

            if (allRequests.isEmpty()) return emptyList()

            var totalCharacters = 0L
            val selected = mutableListOf<ChatRequestEntity>()

            for (request in allRequests.asReversed()) {
                val requestCharacters = request.requestCharacters.toLong()
                if (totalCharacters + requestCharacters > maxRequestCharacters) {
                    break
                }
                selected.add(request)
                totalCharacters += requestCharacters
            }

            return selected.asReversed()
        }
    }

    override fun getMessages(chatId: UUID, scopeMetadata: Map<String, Any>): List<ChatMessageEntity> {
        synchronized(lock) {
            if (getConversationIfMatches(chatId, scopeMetadata) == null) return emptyList()
            return getChatRequests(chatId, scopeMetadata).flatMap { it.messages }
        }
    }

    override fun upsertConversationMetadata(
        chatId: UUID,
        metadata: Map<String, Any>,
        scopeMetadata: Map<String, Any>,
    ): Boolean {
        synchronized(lock) {
            val existing = getConversationIfMatches(chatId, scopeMetadata) ?: return false
            if (metadata.isEmpty()) return true

            val merged = existing.metadata.toMutableMap()
            merged.putAll(metadata)
            saveConversation(getMetadataFile(chatId), existing.copy(metadata = merged.toMap()))
            return true
        }
    }

    override fun deleteConversationMetadata(
        chatId: UUID,
        keys: List<String>,
        scopeMetadata: Map<String, Any>,
    ): Boolean {
        synchronized(lock) {
            val existing = getConversationIfMatches(chatId, scopeMetadata) ?: return false
            if (keys.isEmpty()) return true

            val updated = existing.metadata.toMutableMap()
            for (k in keys) {
                updated.remove(k)
            }

            saveConversation(getMetadataFile(chatId), existing.copy(metadata = updated.toMap()))
            return true
        }
    }

    private fun getConversationIfMatches(chatId: UUID, scopeMetadata: Map<String, Any>): ChatConversationEntity? {
        val conversation = loadConversation(getMetadataFile(chatId)) ?: return null
        if (!ConversationMetadataMatcher.matches(conversation.metadata, scopeMetadata)) return null
        return conversation
    }
}
