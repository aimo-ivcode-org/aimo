package org.ivcode.aimo.core.dao

import java.util.UUID

class AimoChatClientDaoMemory: AimoChatClientDao {
    
    private val conversations: MutableMap<UUID, ChatConversationEntity> = mutableMapOf()
    private val requests: MutableMap<UUID, MutableList<ChatRequestEntity>> = mutableMapOf()
    
    // Helper: Check if userId is authorized to access a conversation
    private fun isOwner(conversation: ChatConversationEntity, userId: String): Boolean {
        return conversation.userId == userId
    }

    // ====================================
    // Creation (requires userId)
    // ====================================

    override fun createChatConversation(userId: String): ChatConversationEntity {
        val chatId = UUID.randomUUID()
        val conversation = ChatConversationEntity(
            chatId = chatId,
            userId = userId,
            metadata = mapOf()
        )
        conversations[chatId] = conversation
        return conversation
    }

    override fun createChatConversation(userId: String, metadata: Map<String, String>): ChatConversationEntity {
        val chatId = UUID.randomUUID()
        val conversation = ChatConversationEntity(
            chatId = chatId,
            userId = userId,
            metadata = metadata
        )
        conversations[chatId] = conversation
        return conversation
    }

    // ====================================
    // User-Scoped Methods
    // ====================================

    override fun getChatConversations(userId: String): List<ChatConversationEntity> {
        return conversations.values.filter { it.userId == userId }
    }

    override fun getChatConversation(chatId: UUID, userId: String): ChatConversationEntity? {
        val conversation = conversations[chatId] ?: return null
        return if (isOwner(conversation, userId)) conversation else null
    }

    override fun deleteChatConversation(chatId: UUID, userId: String): Boolean {
        val conversation = conversations[chatId] ?: return false
        if (!isOwner(conversation, userId)) return false

        conversations.remove(chatId)
        requests.remove(chatId)
        return true
    }

    override fun addChatRequest(userId: String, request: ChatRequestEntity): Boolean {
        val conversation = conversations[request.chatId] ?: return false
        if (!isOwner(conversation, userId)) return false

        val list = requests.getOrPut(request.chatId) { mutableListOf() }
        list.add(request)
        return true
    }

    override fun getChatRequests(userId: String, chatId: UUID): List<ChatRequestEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        if (!isOwner(conversation, userId)) return emptyList()
        return requests[chatId]?.toList() ?: emptyList()
    }

    override fun getChatRequests(userId: String, chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        if (!isOwner(conversation, userId)) return emptyList()

        if (maxRequestCharacters <= 0) {
            return emptyList()
        }

        val chatRequests = requests[chatId] ?: return emptyList()
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

    override fun getMessages(userId: String, chatId: UUID): List<ChatMessageEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        if (!isOwner(conversation, userId)) return emptyList()
        return requests[chatId]?.flatMap { it.messages } ?: emptyList()
    }

    override fun upsertConversationMetadata(chatId: UUID, userId: String, metadata: Map<String, Any>): Boolean {
        val existing = conversations[chatId] ?: return false
        if (!isOwner(existing, userId)) return false

        if (metadata.isEmpty()) return true

        val merged = existing.metadata.toMutableMap()
        for ((k, v) in metadata) {
            merged[k] = v
        }
        conversations[chatId] = existing.copy(metadata = merged.toMap())
        return true
    }

    override fun deleteConversationMetadata(chatId: UUID, userId: String, keys: List<String>): Boolean {
        val existing = conversations[chatId] ?: return false
        if (!isOwner(existing, userId)) return false

        if (keys.isEmpty()) return true

        val updated = existing.metadata.toMutableMap()
        for (k in keys) {
            updated.remove(k)
        }

        conversations[chatId] = existing.copy(metadata = updated.toMap())
        return true
    }

    // ====================================
    // Admin Methods (no auth check)
    // ====================================

    override fun getChatConversation(chatId: UUID): ChatConversationEntity? {
        return conversations[chatId]
    }

    override fun getChatConversationsAdmin(): List<ChatConversationEntity> {
        return conversations.values.toList()
    }

    override fun getChatConversationAdmin(chatId: UUID): ChatConversationEntity? {
        return conversations[chatId]
    }

    override fun deleteChatConversationAdmin(chatId: UUID): Boolean {
        val conversation = conversations[chatId] ?: return false
        conversations.remove(chatId)
        requests.remove(chatId)
        return true
    }

    override fun addChatRequestAdmin(request: ChatRequestEntity): Boolean {
        val conversation = conversations[request.chatId] ?: return false
        val list = requests.getOrPut(request.chatId) { mutableListOf() }
        list.add(request)
        return true
    }

    override fun getChatRequestsAdmin(chatId: UUID): List<ChatRequestEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        return requests[chatId]?.toList() ?: emptyList()
    }

    override fun getChatRequestsAdmin(chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity> {
        val conversation = conversations[chatId] ?: return emptyList()

        if (maxRequestCharacters <= 0) {
            return emptyList()
        }

        val chatRequests = requests[chatId] ?: return emptyList()
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

    override fun getMessagesAdmin(chatId: UUID): List<ChatMessageEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        return requests[chatId]?.flatMap { it.messages } ?: emptyList()
    }

    override fun upsertConversationMetadataAdmin(chatId: UUID, metadata: Map<String, Any>): Boolean {
        val existing = conversations[chatId] ?: return false

        if (metadata.isEmpty()) return true

        val merged = existing.metadata.toMutableMap()
        for ((k, v) in metadata) {
            merged[k] = v
        }
        conversations[chatId] = existing.copy(metadata = merged.toMap())
        return true
    }

    override fun deleteConversationMetadataAdmin(chatId: UUID, keys: List<String>): Boolean {
        val existing = conversations[chatId] ?: return false

        if (keys.isEmpty()) return true

        val updated = existing.metadata.toMutableMap()
        for (k in keys) {
            updated.remove(k)
        }

        conversations[chatId] = existing.copy(metadata = updated.toMap())
        return true
    }
}
