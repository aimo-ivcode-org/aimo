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
    fun createConversation(userId: String): ChatConversationInfo {
        val entity = conversationStore.createChatConversation(userId)
        return ChatConversationInfo(entity.chatId)
    }

    fun deleteConversation(chatId: UUID, userId: String?) {
        val deleted = if (userId != null) {
            conversationStore.deleteChatConversation(chatId, userId)
        } else {
            conversationStore.deleteChatConversationAdmin(chatId)
        }

        if (!deleted) {
            throw NotFoundException("Conversation with id $chatId not found or not authorized")
        }
    }

    fun getConversations(userId: String?): List<ChatConversationInfo> {
        val conversations = if (userId != null) {
            conversationStore.getChatConversations(userId)
        } else {
            conversationStore.getChatConversationsAdmin()
        }
        return conversations.map { it.toChatConversationInfo() }
    }
}


