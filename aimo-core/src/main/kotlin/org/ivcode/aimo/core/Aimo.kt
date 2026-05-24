package org.ivcode.aimo.core

import java.util.UUID

interface Aimo {
    fun getConversationClient(chatId: UUID): AimoConversationClient?
    fun createConversation(): AimoConversationInfo
    fun getConversations(): List<AimoConversationInfo>
    fun deleteConversation(chatId: UUID): Boolean
    fun getChatHistory(chatId: UUID): List<AimoHistoryRequest>
    
    /**
     * Update metadata for an existing conversation identified by [chatId].
     *
     * This does not create a new conversation when [chatId] is missing.
     */
    fun upsertConversation(chatId: UUID, metadata: Map<String, String>): Boolean
}