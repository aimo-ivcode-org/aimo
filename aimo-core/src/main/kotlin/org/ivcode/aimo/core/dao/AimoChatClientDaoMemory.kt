package org.ivcode.aimo.core.dao

import java.util.UUID

class AimoChatClientDaoMemory: AimoChatClientDao {
    
    private val conversations: MutableMap<UUID, ChatConversationEntity> = mutableMapOf()
    private val requests: MutableMap<UUID, MutableList<ChatRequestEntity>> = mutableMapOf()
    
    override fun createChatConversation(): ChatConversationEntity {
        val chatId = UUID.randomUUID()
        val conversation = ChatConversationEntity(
            chatId = chatId,
            metadata = mapOf()
        )
        
        conversations[chatId] = conversation
        return conversation
    }

    override fun createChatConversation(metadata: Map<String, String>): ChatConversationEntity {
        val chatId = UUID.randomUUID()
        val conversation = ChatConversationEntity(
            chatId = chatId,
            metadata = metadata
        )

        conversations[chatId] = conversation
        return conversation
    }

    override fun getChatConversations(): List<ChatConversationEntity> {
        return conversations.values.toList()
    }

    override fun getChatConversation(chatId: UUID): ChatConversationEntity? {
        return conversations[chatId]
    }

    override fun getChatConversation(
        chatId: UUID,
        metadata: Map<String, String>
    ): ChatConversationEntity? {
        val conversation = conversations[chatId] ?: return null

        for(md in metadata) {
            val conversationValue = conversation.metadata[md.key] ?: return null
            if (conversationValue != md.value) return null
        }
        
        return conversation
    }

    override fun deleteChatConversation(chatId: UUID): Boolean {
        val deleted = conversations.remove(chatId) != null
        if (deleted) {
            requests.remove(chatId)
        }
        return deleted
    }

    override fun deleteChatConversation(
        chatId: UUID,
        metadata: Map<String, String>
    ): Boolean {
        val conversation = conversations[chatId] ?: return false

        for(md in metadata) {
            val conversationValue = conversation.metadata[md.key] ?: return false
            if (conversationValue != md.value) return false
        }
        
        conversations.remove(chatId)
        requests.remove(chatId)
        return true
    }

    override fun addChatRequest(request: ChatRequestEntity) {
        // Add the request to the list of requests for the chat, creating the list if needed
        val list = requests.getOrPut(request.chatId) { mutableListOf() }
        list.add(request)
    }

    override fun getChatRequests(chatId: UUID): List<ChatRequestEntity> {
        return requests[chatId]?.toList() ?: emptyList()
    }

    override fun getChatRequests(chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity> {
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

        // Pick newest requests first until the cumulative character budget would be exceeded.
        for (request in chatRequests.asReversed()) {
            val requestCharacters = request.requestCharacters.toLong()
            if (totalCharacters + requestCharacters > maxCharacters) {
                if (selected.isEmpty()) {
                    // Always include the newest request so tail lookups retain the latest message id.
                    selected.add(request)
                }
                break
            }

            selected.add(request)
            totalCharacters += requestCharacters
        }

        return selected.asReversed()
    }

    override fun getMessages(chatId: UUID): List<ChatMessageEntity> {
        // Flatten messages from all requests for the chat in insertion order
        return requests[chatId]?.flatMap { it.messages } ?: emptyList()
    }

    override fun upsertConversationMetadata(
        chatId: UUID,
        metadata: Map<String, Any>
    ): Boolean {
        // If the conversation does not exist we do not create one; return false to indicate
        // the operation was not performed per the user's requirement.
        val existing = conversations[chatId] ?: return false

        // If metadata is empty, nothing to do; treat as successful no-op
        if (metadata.isEmpty()) return true

        // Merge existing metadata with provided metadata (provided keys override)
        val merged = existing.metadata.toMutableMap()
        for ((k, v) in metadata) {
            merged[k] = v
        }
        conversations[chatId] = existing.copy(metadata = merged.toMap())
        return true
    }

    override fun deleteConversationMetadata(
        chatId: UUID,
        keys: List<String>
    ): Boolean {
        // If the conversation does not exist, indicate failure
        val existing = conversations[chatId] ?: return false

        // If no keys provided, nothing to do; treat as successful no-op
        if (keys.isEmpty()) return true

        val updated = existing.metadata.toMutableMap()
        for (k in keys) {
            updated.remove(k)
        }

        conversations[chatId] = existing.copy(metadata = updated.toMap())
        return true
    }
}
