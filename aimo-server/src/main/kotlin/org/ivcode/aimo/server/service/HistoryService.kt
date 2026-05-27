package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.Aimo
import org.ivcode.aimo.server.model.ChatHistoryRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class HistoryService (
    private val aimo: Aimo
) {
    /**
     * Get history for any conversation without user isolation checks.
     * This is an admin operation.
     */
    fun getHistory(chatId: UUID): List<ChatHistoryRequest> {
        return aimo.getChatHistoryAdmin(chatId).map { it.toChatHistoryRequest() }
    }

    /**
     * Get history for a specific user's conversation.
     * This is a user-scoped operation.
     */
    fun getHistory(chatId: UUID, userId: String): List<ChatHistoryRequest> {
        return aimo.getChatHistory(chatId, userId).map { it.toChatHistoryRequest() }
    }
}

