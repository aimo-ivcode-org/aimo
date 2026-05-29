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
     * Get history for a specific user's conversation.
     * This is a user-scoped operation with user isolation enforcement.
     *
     * @param chatId The conversation ID
     * @param userId The user ID requesting the history (must own the conversation)
     * @return The conversation history, or empty list if not found or unauthorized
     */
    fun getHistory(chatId: UUID, userId: String): List<ChatHistoryRequest> {
        return aimo.getChatHistory(chatId, userId).map { it.toChatHistoryRequest() }
    }
}

