package org.ivcode.aimo.core

import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.NoOpAimoSessionCache
import org.ivcode.aimo.core.client.session.AimoSessionClientImpl
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
    override fun getSessionClient(chatId: UUID): AimoSessionClient? = chatClientDao.getChatSession(chatId)?.let { session ->
        val metadata = sessionCache.getMetadata(chatId) ?: session.metadata
        AimoSessionClientImpl (
            chatId = session.chatId,
            model = model,
            dao = chatClientDao,
            tools = tools,
            systemMessages = systemMessage,
            metadata = metadata,
            sessionCache = sessionCache,
        )
    }

    override fun createSession(): AimoSession  {
        val session = chatClientDao.createChatSession()
        sessionCache.putMetadata(session.chatId, session.metadata)
        return session.toAimoSession()
    }

    override fun getSessions(): List<AimoSession> {
        return chatClientDao.getChatSessions().map { it.toAimoSession() }
    }

    override fun deleteSession(chatId: UUID): Boolean {
        val deleted = chatClientDao.deleteChatSession(chatId)
        if (deleted) {
            sessionCache.evict(chatId)
        }
        return deleted
    }

    override fun getChatHistory(chatId: UUID): List<AimoHistoryRequest> {
        return chatClientDao.getChatRequests(chatId).map { it.toAimoHistoryRequest() }
    }

    override fun upsertSession(chatId: UUID, metadata: Map<String, String>): Boolean {
        return chatClientDao.upsertSessionMetadata(chatId, metadata)
    }
}