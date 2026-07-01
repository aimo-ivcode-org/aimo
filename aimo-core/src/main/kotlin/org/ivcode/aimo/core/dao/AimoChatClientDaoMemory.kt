package org.ivcode.aimo.core.dao

import java.util.UUID

class AimoChatClientDaoMemory: AimoChatClientDao {

    private val conversations: MutableMap<UUID, ChatConversationEntity> = mutableMapOf()
    private val requests: MutableMap<UUID, MutableList<ChatRequestEntity>> = mutableMapOf()

    override fun createChatConversation(metadata: Map<String, Any>): ChatConversationEntity {
        val chatId = UUID.randomUUID()
        val conversation = ChatConversationEntity(
            chatId = chatId,
            metadata = metadata,
        )
        conversations[chatId] = conversation
        return conversation
    }

    override fun getChatConversations(scopeMetadata: Map<String, Any>): List<ChatConversationEntity> {
        return conversations.values.filter { ConversationMetadataMatcher.matches(it.metadata, scopeMetadata) }
    }

    override fun getChatConversation(chatId: UUID, scopeMetadata: Map<String, Any>): ChatConversationEntity? {
        return getConversationIfMatches(chatId, scopeMetadata)
    }

    override fun deleteChatConversation(chatId: UUID, scopeMetadata: Map<String, Any>): Boolean {
        if (getConversationIfMatches(chatId, scopeMetadata) == null) return false
        conversations.remove(chatId)
        requests.remove(chatId)
        return true
    }

    override fun addChatRequest(request: ChatRequestEntity, scopeMetadata: Map<String, Any>): Boolean {
        if (getConversationIfMatches(request.chatId, scopeMetadata) == null) return false
        val list = requests.getOrPut(request.chatId) { mutableListOf() }
        list.add(request)
        return true
    }

    override fun getChatRequests(chatId: UUID, scopeMetadata: Map<String, Any>): List<ChatRequestEntity> {
        if (getConversationIfMatches(chatId, scopeMetadata) == null) return emptyList()
        return requests[chatId]?.toList() ?: emptyList()
    }

    override fun getChatRequests(
        chatId: UUID,
        maxRequestCharacters: Long,
        scopeMetadata: Map<String, Any>,
    ): List<ChatRequestEntity> {
        if (getConversationIfMatches(chatId, scopeMetadata) == null) return emptyList()
        if (maxRequestCharacters <= 0) return emptyList()

        val chatRequests = requests[chatId] ?: return emptyList()
        if (chatRequests.isEmpty()) return emptyList()

        var totalCharacters = 0L
        val selected = mutableListOf<ChatRequestEntity>()

        for (request in chatRequests.asReversed()) {
            val requestCharacters = request.requestCharacters.toLong()
            if (totalCharacters + requestCharacters > maxRequestCharacters) {
                break
            }
            selected.add(request)
            totalCharacters += requestCharacters
        }

        return selected.asReversed()
    }

    override fun getMessages(chatId: UUID, scopeMetadata: Map<String, Any>): List<ChatMessageEntity> {
        if (getConversationIfMatches(chatId, scopeMetadata) == null) return emptyList()
        return requests[chatId]?.flatMap { it.messages } ?: emptyList()
    }

    override fun upsertConversationMetadata(
        chatId: UUID,
        metadata: Map<String, Any>,
        scopeMetadata: Map<String, Any>,
    ): Boolean {
        val existing = getConversationIfMatches(chatId, scopeMetadata) ?: return false
        if (metadata.isEmpty()) return true

        val merged = existing.metadata.toMutableMap()
        merged.putAll(metadata)
        conversations[chatId] = existing.copy(metadata = merged.toMap())
        return true
    }

    override fun deleteConversationMetadata(
        chatId: UUID,
        keys: List<String>,
        scopeMetadata: Map<String, Any>,
    ): Boolean {
        val existing = getConversationIfMatches(chatId, scopeMetadata) ?: return false
        if (keys.isEmpty()) return true

        val updated = existing.metadata.toMutableMap()
        for (k in keys) {
            updated.remove(k)
        }

        conversations[chatId] = existing.copy(metadata = updated.toMap())
        return true
    }

    private fun getConversationIfMatches(chatId: UUID, scopeMetadata: Map<String, Any>): ChatConversationEntity? {
        val conversation = conversations[chatId] ?: return null
        if (!ConversationMetadataMatcher.matches(conversation.metadata, scopeMetadata)) return null
        return conversation
    }
}
