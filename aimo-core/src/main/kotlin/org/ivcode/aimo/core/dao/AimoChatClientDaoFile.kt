package org.ivcode.aimo.core.dao

import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.util.UUID

/**
 * File-based implementation of [AimoChatClientDao].
 * Delegates file path management to [ConversationFilePaths] and serialization to [ConversationSerializer].
 */
class AimoChatClientDaoFile(
    private val dataDir: File,
    private val objectMapper: ObjectMapper
) : AimoChatClientDao {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = Any()
    private val filePaths = ConversationFilePaths(dataDir)
    private val serializer = ConversationSerializer(objectMapper, log)

    init {
        dataDir.mkdirs()
    }

    override fun createChatConversation(metadata: Map<String, Any>): ChatConversationEntity {
        synchronized(lock) {
            val chatId = UUID.randomUUID()
            val conversation = ChatConversationEntity(
                chatId = chatId,
                metadata = metadata,
            )
            serializer.saveConversation(filePaths.getMetadataFile(chatId), conversation)
            return conversation
        }
    }

    override fun getChatConversations(scopeMetadata: Map<String, Any>): List<ChatConversationEntity> {
        synchronized(lock) {
            val conversations = mutableListOf<ChatConversationEntity>()
            dataDir.listFiles { file -> file.isDirectory }?.forEach { chatDir ->
                val metadataFile = File(chatDir, "metadata.json")
                serializer.loadConversation(metadataFile)?.let { conversation ->
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
            filePaths.getChatDir(chatId).deleteRecursively()
            return true
        }
    }

    override fun addChatRequest(request: ChatRequestEntity, scopeMetadata: Map<String, Any>): Boolean {
        synchronized(lock) {
            if (getConversationIfMatches(request.chatId, scopeMetadata) == null) return false
            serializer.saveRequest(filePaths.getRequestFile(request.chatId, request.requestId), request)
            return true
        }
    }

    override fun getChatRequests(chatId: UUID, scopeMetadata: Map<String, Any>): List<ChatRequestEntity> {
        synchronized(lock) {
            if (getConversationIfMatches(chatId, scopeMetadata) == null) {
                return emptyList()
            }

            val requestsDir = filePaths.getRequestsDir(chatId)
            return if (!requestsDir.exists()) {
                emptyList()
            } else {
                requestsDir.listFiles()
                    ?.mapNotNull { file -> serializer.loadRequest(file) }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            }
        }
    }

    override fun getChatRequests(
        chatId: UUID,
        maxRequestCharacters: Long,
        scopeMetadata: Map<String, Any>,
    ): List<ChatRequestEntity> {
        synchronized(lock) {
            // Early validation checks
            if (getConversationIfMatches(chatId, scopeMetadata) == null ||
                maxRequestCharacters <= 0
            ) {
                return emptyList()
            }

            val requestsDir = filePaths.getRequestsDir(chatId)
            val allRequests = if (requestsDir.exists()) {
                requestsDir.listFiles()
                    ?.mapNotNull { file -> serializer.loadRequest(file) }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
            } else {
                emptyList()
            }

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

            return if (metadata.isEmpty()) {
                true
            } else {
                val merged = existing.metadata.toMutableMap()
                merged.putAll(metadata)
                serializer.saveConversation(
                    filePaths.getMetadataFile(chatId),
                    existing.copy(metadata = merged.toMap())
                )
                true
            }
        }
    }

    override fun deleteConversationMetadata(
        chatId: UUID,
        keys: List<String>,
        scopeMetadata: Map<String, Any>,
    ): Boolean {
        synchronized(lock) {
            val existing = getConversationIfMatches(chatId, scopeMetadata) ?: return false

            return if (keys.isEmpty()) {
                true
            } else {
                val updated = existing.metadata.toMutableMap()
                for (k in keys) {
                    updated.remove(k)
                }
                serializer.saveConversation(
                    filePaths.getMetadataFile(chatId),
                    existing.copy(metadata = updated.toMap())
                )
                true
            }
        }
    }

    /**
     * Loads the conversation for the given ID and verifies it matches the provided scope metadata.
     *
     * @param chatId the conversation ID to load
     * @param scopeMetadata scope metadata to validate against
     * @return the loaded conversation if it exists and matches scope, null otherwise
     */
    private fun getConversationIfMatches(
        chatId: UUID,
        scopeMetadata: Map<String, Any>
    ): ChatConversationEntity? {
        val conversation = serializer.loadConversation(filePaths.getMetadataFile(chatId)) ?: return null
        return if (ConversationMetadataMatcher.matches(conversation.metadata, scopeMetadata)) {
            conversation
        } else {
            null
        }
    }
}

/**
 * Manages file paths for conversation storage.
 * Provides consistent path construction for chat directories, metadata files, and request files.
 */
private class ConversationFilePaths(private val dataDir: File) {
    fun getChatDir(chatId: UUID): File = File(dataDir, chatId.toString())
    fun getMetadataFile(chatId: UUID): File = File(getChatDir(chatId), "metadata.json")
    fun getRequestsDir(chatId: UUID): File = File(getChatDir(chatId), "requests")
    fun getRequestFile(chatId: UUID, requestId: UUID): File = File(getRequestsDir(chatId), "$requestId.json")
}

/**
 * Handles serialization and deserialization of conversation and request entities.
 * Uses Jackson ObjectMapper for JSON I/O with graceful error handling.
 */
private class ConversationSerializer(
    private val objectMapper: ObjectMapper,
    private val log: org.slf4j.Logger
) {
    /**
     * Loads a conversation entity from file.
     * Returns null if file doesn't exist or deserialization fails (with logging).
     */
    fun loadConversation(file: File): ChatConversationEntity? {
        return if (file.exists()) {
            try {
                objectMapper.readValue(file, ChatConversationEntity::class.java)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Gracefully handle deserialization failures (corrupt JSON, IO errors, etc.)
                // Log and return null to allow the DAO to continue operating
                log.warn("Failed to deserialize conversation metadata from {}: {}", file.absolutePath, e.message, e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Saves a conversation entity to file.
     * Creates parent directories and writes with pretty-printed JSON format.
     */
    fun saveConversation(file: File, conversation: ChatConversationEntity) {
        file.parentFile?.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, conversation)
    }

    /**
     * Loads a chat request entity from file.
     * Returns null if file doesn't exist or deserialization fails (with logging).
     */
    fun loadRequest(file: File): ChatRequestEntity? {
        return if (file.exists()) {
            try {
                objectMapper.readValue(file, ChatRequestEntity::class.java)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Gracefully handle deserialization failures (corrupt JSON, IO errors, etc.)
                // Log and return null to allow the DAO to continue operating
                log.warn("Failed to deserialize chat request from {}: {}", file.absolutePath, e.message, e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Saves a chat request entity to file.
     * Creates parent directories and writes with pretty-printed JSON format.
     */
    fun saveRequest(file: File, request: ChatRequestEntity) {
        file.parentFile?.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, request)
    }
}

