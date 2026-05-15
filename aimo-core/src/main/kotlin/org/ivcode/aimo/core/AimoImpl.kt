package org.ivcode.aimo.core

import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.NoOpAimoSessionCache
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
    private val sessionCache: AimoSessionCache = NoOpAimoSessionCache,
): Aimo {
    override fun getConversationClient(chatId: UUID): AimoConversationClient? = chatClientDao.getChatConversation(chatId)?.let { conversation ->
        AimoConversationClientImpl (
            chatId = conversation.chatId,
            model = model,
            dao = chatClientDao,
            tools = tools,
            systemMessages = systemMessage,
            sessionCache = sessionCache,
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
        val deleted = chatClientDao.deleteChatConversation(chatId)
        if (deleted) {
            sessionCache.evict(chatId)
        }
        return deleted
    }

    override fun getChatHistory(chatId: UUID): List<AimoHistoryRequest> {
        return chatClientDao.getChatRequests(chatId).map { it.toAimoHistoryRequest() }
    }

    override fun upsertConversation(chatId: UUID, metadata: Map<String, String>): Boolean {
        return chatClientDao.upsertConversationMetadata(chatId, metadata)
    }
}