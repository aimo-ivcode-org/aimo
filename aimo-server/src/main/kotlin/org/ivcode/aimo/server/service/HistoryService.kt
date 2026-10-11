package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.conversation.ConversationFactory
import org.ivcode.aimo.server.model.ChatHistoryRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class HistoryService (
    private val conversationFactory: ConversationFactory
) {
    fun getHistory(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): List<ChatHistoryRequest> {
        val conversation = conversationFactory.getConversation(chatId, scopeMetadata) ?: return emptyList()
        return conversation.getHistory().map { it.toChatHistoryRequest() }
    }
}
