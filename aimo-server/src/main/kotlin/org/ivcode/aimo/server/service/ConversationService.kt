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
    fun createConversation(): ChatConversationInfo {
        return ChatConversationInfo(
            aimo.createConversation().chatId
        )
    }

    fun deleteConversation(chatId: UUID) {
        if(!aimo.deleteConversation(chatId)) {
            throw NotFoundException("Conversation with id $chatId not found")
        }
    }

    fun getConversations(): List<ChatConversationInfo> {
        return aimo.getConversations().map { it.toChatConversationInfo() }
    }
}

