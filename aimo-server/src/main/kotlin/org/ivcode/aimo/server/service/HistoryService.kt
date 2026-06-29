package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.server.model.ChatHistoryRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class HistoryService (
    private val conversationStore: AimoChatClientDao
) {
    fun getHistory(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): List<ChatHistoryRequest> {
        if (conversationStore.getChatConversation(chatId, scopeMetadata) == null) {
            return emptyList()
        }
        return conversationStore.getChatRequests(chatId, scopeMetadata).map { it.toChatHistoryRequest() }
    }
}
