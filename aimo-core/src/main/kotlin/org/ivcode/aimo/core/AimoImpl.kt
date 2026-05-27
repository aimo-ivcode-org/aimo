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

    // ============================================
    // User-facing methods (with userId required)
    // ============================================

    override fun getConversationClient(chatId: UUID, userId: String): AimoConversationClient? {
        return chatClientDao.getChatConversation(chatId, userId)?.let { conversation ->
            AimoConversationClientImpl (
                chatId = conversation.chatId,
                model = model,
                dao = chatClientDao,
                userId = userId,
                tools = tools,
                systemMessages = systemMessage,
            )
        }
    }

    override fun createConversation(userId: String): AimoConversationInfo {
        return chatClientDao.createChatConversation(userId).toAimoConversationInfo()
    }

    override fun getConversations(userId: String): List<AimoConversationInfo> {
        return chatClientDao.getChatConversations(userId).map { it.toAimoConversationInfo() }
    }

    override fun deleteConversation(chatId: UUID, userId: String): Boolean {
        return chatClientDao.deleteChatConversation(chatId, userId)
    }

    override fun getChatHistory(chatId: UUID, userId: String): List<AimoHistoryRequest> {
        return chatClientDao.getChatRequests(userId, chatId).map { it.toAimoHistoryRequest() }
    }

    override fun upsertConversation(chatId: UUID, metadata: Map<String, String>, userId: String): Boolean {
        return chatClientDao.upsertConversationMetadata(chatId, userId, metadata)
    }

    // ============================================
    // Admin-only methods (without userId checks)
    // ============================================

    override fun getConversationClientAdmin(chatId: UUID): AimoConversationClient? {
        return chatClientDao.getChatConversation(chatId, null)?.let { conversation ->
            AimoConversationClientImpl (
                chatId = conversation.chatId,
                model = model,
                dao = chatClientDao,
                userId = null,
                tools = tools,
                systemMessages = systemMessage,
            )
        }
    }

    override fun getConversationsAdmin(): List<AimoConversationInfo> {
        return chatClientDao.getChatConversations(null).map { it.toAimoConversationInfo() }
    }

    override fun deleteConversationAdmin(chatId: UUID): Boolean {
        return chatClientDao.deleteChatConversation(chatId, null)
    }

    override fun getChatHistoryAdmin(chatId: UUID): List<AimoHistoryRequest> {
        return chatClientDao.getChatRequests(null, chatId).map { it.toAimoHistoryRequest() }
    }

    override fun upsertConversationAdmin(chatId: UUID, metadata: Map<String, String>): Boolean {
        return chatClientDao.upsertConversationMetadata(chatId, null, metadata)
    }
}

