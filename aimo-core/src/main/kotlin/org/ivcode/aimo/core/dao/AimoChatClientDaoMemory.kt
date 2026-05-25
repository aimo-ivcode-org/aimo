package org.ivcode.aimo.core.dao

import java.util.UUID

class AimoChatClientDaoMemory: AimoChatClientDao {
    
    private val conversations: MutableMap<UUID, ChatConversationEntity> = mutableMapOf()
    private val requests: MutableMap<UUID, MutableList<ChatRequestEntity>> = mutableMapOf()
    
    // Helper: Check if userId is authorized to access a conversation
    private fun canAccess(conversation: ChatConversationEntity, userId: String?): Boolean {
        // If userId is null, allow access (no authorization check)
        if (userId == null) return true
        // If userId is provided, verify it matches conversation owner
        return conversation.userId == userId
    }

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

    override fun getChatConversations(userId: String?): List<ChatConversationEntity> {
        return if (userId == null) {
            // No userId provided: return all conversations (no auth check)
            conversations.values.toList()
        } else {
            // userId provided: only conversations owned by this userId
            conversations.values.filter { it.userId == userId }
        }
    }

    override fun getChatConversation(chatId: UUID): ChatConversationEntity? {
        return conversations[chatId]
    }

     override fun getChatConversation(chatId: UUID, userId: String?): ChatConversationEntity? {
         val conversation = conversations[chatId] ?: return null
         // Check authorization
         return if (canAccess(conversation, userId)) conversation else null
     }

     // Legacy metadata-based lookup for backward compatibility (not part of interface)
     fun getChatConversationByMetadata(chatId: UUID, metadata: Map<String, String>): ChatConversationEntity? {
         val conversation = conversations[chatId] ?: return null
         for (md in metadata) {
             val conversationValue = conversation.metadata[md.key] ?: return null
             if (conversationValue != md.value) return null
         }
         return conversation
     }

     override fun deleteChatConversation(chatId: UUID, userId: String?): Boolean {
         val conversation = conversations[chatId] ?: return false
         // Check authorization
         if (!canAccess(conversation, userId)) return false

         conversations.remove(chatId)
         requests.remove(chatId)
         return true
     }

     // Legacy metadata-based deletion for backward compatibility (not part of interface)
     fun deleteChatConversationByMetadata(chatId: UUID, metadata: Map<String, String>): Boolean {
         val conversation = conversations[chatId] ?: return false
         for (md in metadata) {
             val conversationValue = conversation.metadata[md.key] ?: return false
             if (conversationValue != md.value) return false
         }
         conversations.remove(chatId)
         requests.remove(chatId)
         return true
     }

    override fun addChatRequest(userId: String?, request: ChatRequestEntity) {
        // userId is provided for authorization context but not stored in the request
        // (it's already implicit in the conversation)
        val list = requests.getOrPut(request.chatId) { mutableListOf() }
        list.add(request)
    }

    override fun getChatRequests(userId: String?, chatId: UUID): List<ChatRequestEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        // Check authorization
        if (!canAccess(conversation, userId)) return emptyList()
        return requests[chatId]?.toList() ?: emptyList()
    }

    override fun getChatRequests(userId: String?, chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        // Check authorization
        if (!canAccess(conversation, userId)) return emptyList()

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

    override fun getMessages(userId: String?, chatId: UUID): List<ChatMessageEntity> {
        val conversation = conversations[chatId] ?: return emptyList()
        // Check authorization
        if (!canAccess(conversation, userId)) return emptyList()
        return requests[chatId]?.flatMap { it.messages } ?: emptyList()
    }

    override fun upsertConversationMetadata(chatId: UUID, userId: String?, metadata: Map<String, Any>): Boolean {
        val existing = conversations[chatId] ?: return false
        // Check authorization
        if (!canAccess(existing, userId)) return false

        if (metadata.isEmpty()) return true

        val merged = existing.metadata.toMutableMap()
        for ((k, v) in metadata) {
            merged[k] = v
        }
        conversations[chatId] = existing.copy(metadata = merged.toMap())
        return true
    }

    override fun deleteConversationMetadata(chatId: UUID, userId: String?, keys: List<String>): Boolean {
        val existing = conversations[chatId] ?: return false
        // Check authorization
        if (!canAccess(existing, userId)) return false

        if (keys.isEmpty()) return true

        val updated = existing.metadata.toMutableMap()
        for (k in keys) {
            updated.remove(k)
        }

        conversations[chatId] = existing.copy(metadata = updated.toMap())
        return true
    }
}
