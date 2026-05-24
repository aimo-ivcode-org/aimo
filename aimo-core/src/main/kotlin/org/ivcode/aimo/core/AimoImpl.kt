package org.ivcode.aimo.core

import org.ivcode.aimo.core.client.conversation.AimoConversationClientImpl
import org.ivcode.aimo.core.controller.SystemMessageCallback
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoToolCallback
import java.util.UUID

internal class AimoImpl (
    private val model: AimoChatModel,
    private val chatClientDao: AimoChatClientDao,
    private val tools: List<AimoToolCallback>,
    private val systemMessage: List<SystemMessageCallback>,
): Aimo {
    override fun getConversationClient(chatId: UUID): AimoConversationClient? = chatClientDao.getChatConversation(chatId)?.let { conversation ->
        AimoConversationClientImpl (
            chatId = conversation.chatId,
            model = model,
            dao = chatClientDao,
            tools = tools,
            systemMessages = systemMessage,
        )
    }

    override fun createConversation(): AimoConversationInfo {
        val conversation = chatClientDao.createChatConversation()
        return conversation.toAimoConversationInfo()
    }

    override fun getConversations(): List<AimoConversationInfo> {
        return chatClientDao.getChatConversations().map { it.toAimoConversationInfo() }
    }

     override fun deleteConversation(chatId: UUID): Boolean {
         return chatClientDao.deleteChatConversation(chatId)
     }

    override fun getChatHistory(chatId: UUID): List<AimoHistoryRequest> {
        return chatClientDao.getChatRequests(chatId).map { it.toAimoHistoryRequest() }
    }

    override fun upsertConversation(chatId: UUID, metadata: Map<String, String>): Boolean {
        return chatClientDao.upsertConversationMetadata(chatId, metadata)
    }
}