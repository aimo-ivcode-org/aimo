package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.Aimo
import org.ivcode.aimo.server.exceptions.NotFoundException
import org.ivcode.aimo.server.model.ChatConversationInfo
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ConversationService (
    private val aimo: Aimo
) {
    fun createConversation(userId: String): ChatConversationInfo {
        return ChatConversationInfo(
            aimo.createConversation(userId).chatId
        )
    }

    fun deleteConversation(chatId: UUID, userId: String?) {
        val deleted = if (userId != null) {
            aimo.deleteConversation(chatId, userId)
        } else {
            aimo.deleteConversationAdmin(chatId)
        }

        if (!deleted) {
            throw NotFoundException("Conversation with id $chatId not found or not authorized")
        }
    }

    fun getConversations(userId: String?): List<ChatConversationInfo> {
        val conversations = if (userId != null) {
            aimo.getConversations(userId)
        } else {
            aimo.getConversationsAdmin()
        }
        return conversations.map { it.toChatConversationInfo() }
    }
}


