package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.server.exceptions.NotFoundException
import org.ivcode.aimo.server.model.ChatConversationInfo
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ConversationService (
    private val conversationStore: AimoChatClientDao
) {
    fun createConversation(metadata: Map<String, Any> = emptyMap()): ChatConversationInfo {
        val entity = conversationStore.createChatConversation(metadata)
        return entity.toChatConversationInfo()
    }

    fun getConversations(scopeMetadata: Map<String, Any> = emptyMap()): List<ChatConversationInfo> {
        return conversationStore.getChatConversations(scopeMetadata).map { it.toChatConversationInfo() }
    }

    fun getConversation(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): ChatConversationInfo {
        val entity = conversationStore.getChatConversation(chatId, scopeMetadata)
            ?: throw NotFoundException("Conversation with id $chatId not found")
        return entity.toChatConversationInfo()
    }

    fun deleteConversation(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()) {
        if (!conversationStore.deleteChatConversation(chatId, scopeMetadata)) {
            throw NotFoundException("Conversation with id $chatId not found")
        }
    }

    fun getMetadata(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): Map<String, Any> {
        val entity = conversationStore.getChatConversation(chatId, scopeMetadata)
            ?: throw NotFoundException("Conversation with id $chatId not found")
        return entity.metadata
    }

    fun upsertMetadata(
        chatId: UUID,
        metadata: Map<String, Any>,
        scopeMetadata: Map<String, Any> = emptyMap(),
    ) {
        if (!conversationStore.upsertConversationMetadata(chatId, metadata, scopeMetadata)) {
            throw NotFoundException("Conversation with id $chatId not found")
        }
    }

    fun deleteMetadata(
        chatId: UUID,
        keys: List<String>,
        scopeMetadata: Map<String, Any> = emptyMap(),
    ) {
        if (!conversationStore.deleteConversationMetadata(chatId, keys, scopeMetadata)) {
            throw NotFoundException("Conversation with id $chatId not found")
        }
    }
}
