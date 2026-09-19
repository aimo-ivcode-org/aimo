package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.conversation.ConversationFactory
import org.ivcode.aimo.server.exceptions.NotFoundException
import org.ivcode.aimo.server.model.ChatConversationInfo
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ConversationService (
    private val conversationFactory: ConversationFactory
) {
    fun createConversation(metadata: Map<String, Any> = emptyMap()): ChatConversationInfo {
        val conversation = conversationFactory.createConversation(metadata)
        return ChatConversationInfo(chatId = conversation.chatId, metadata = conversation.getChatMetadata())
    }

    fun getConversations(scopeMetadata: Map<String, Any> = emptyMap()): List<ChatConversationInfo> {
        return conversationFactory.getConversations(scopeMetadata).map { conversation ->
            ChatConversationInfo(chatId = conversation.chatId, metadata = conversation.getChatMetadata())
        }
    }

    fun getConversation(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): ChatConversationInfo {
        val conversation = conversationFactory.getConversation(chatId, scopeMetadata)
            ?: throw NotFoundException("Conversation with id $chatId not found")
        return ChatConversationInfo(chatId = conversation.chatId, metadata = conversation.getChatMetadata())
    }

    fun deleteConversation(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()) {
        if (!conversationFactory.deleteConversation(chatId, scopeMetadata)) {
            throw NotFoundException("Conversation with id $chatId not found")
        }
    }

    fun getMetadata(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): Map<String, Any> {
        val conversation = conversationFactory.getConversation(chatId, scopeMetadata)
            ?: throw NotFoundException("Conversation with id $chatId not found")
        return conversation.getChatMetadata()
    }

    fun upsertMetadata(
        chatId: UUID,
        metadata: Map<String, Any>,
        scopeMetadata: Map<String, Any> = emptyMap(),
    ) {
        val conversation = conversationFactory.getConversation(chatId, scopeMetadata)
            ?: throw NotFoundException("Conversation with id $chatId not found")
        conversation.writeChatProperties(metadata)
    }

    fun deleteMetadata(
        chatId: UUID,
        keys: List<String>,
        scopeMetadata: Map<String, Any> = emptyMap(),
    ) {
        val conversation = conversationFactory.getConversation(chatId, scopeMetadata)
            ?: throw NotFoundException("Conversation with id $chatId not found")
        conversation.deleteChatProperties(keys)
    }
}
