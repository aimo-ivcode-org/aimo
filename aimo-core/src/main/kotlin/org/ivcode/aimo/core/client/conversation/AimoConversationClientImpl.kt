package org.ivcode.aimo.core.client.conversation

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.client.chat.AimoChatClientImpl
import org.ivcode.aimo.core.controller.SystemMessageCallback
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.ChatRequestEntity
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.toChatMessageEntity
import org.ivcode.aimo.core.toAimoChatMessage
import java.time.Instant
import java.util.UUID

internal class AimoConversationClientImpl(
    override val chatId: UUID,
    private val dao: AimoChatClientDao,
    private val model: AimoChatModel,
    private val userId: String? = null,
    private val tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
) : AimoConversationClient {

    override fun createChatClient(): AimoChatClient {
        return AimoChatClientImpl (
            chatId = chatId,
            conversation = this,
            model = model,
            tools = tools,
            systemMessages = systemMessages,
        )
    }

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
        // Load from DAO with optional character limit
        val messages = if (maxCacheCharacters == null) {
            dao.getChatRequests(userId, chatId)
        } else {
            dao.getChatRequests(
                userId = userId,
                chatId = chatId,
                maxRequestCharacters = maxCacheCharacters.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
        }.flatMap { it.messages.map { m -> m.toAimoChatMessage() } }
        return messages.takeIf { it.isNotEmpty() }
    }

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) {
        if(messages.isEmpty()) {
            throw IllegalArgumentException("AimoConversationClientImpl addMessages should have at least one message")
        }

        // Persist to DAO (source of truth)
        val success = dao.addChatRequest(
            userId = userId,
            request = ChatRequestEntity(
                chatId = chatId,
                requestId = requestId,
                messages = messages.map { it.toChatMessageEntity(requestId) },
                requestCharacters = messages.sumOf { it.content?.length ?: 0 },
                createdAt = Instant.now(),
            )
        )
        if (!success) {
            throw IllegalStateException("Failed to persist messages: conversation not found or user not authorized for chatId: $chatId")
        }
    }

    override fun getChatMetadata(): Map<String, Any> {
        return requireChatConversation().metadata.toMap()
    }

    override fun readChatMetadata(): Map<String, Any> {
        return getChatMetadata()
    }

    override fun getChatProperty(property: String): Any? {
        return requireChatConversation().metadata[property]
    }

    override fun readChatProperty(property: String): Any? {
        return getChatProperty(property)
    }

    override fun writeChatProperty(property: String, value: Any) {
        val success = dao.upsertConversationMetadata(chatId, userId, mapOf(property to value))
        if (!success) {
            throw IllegalStateException("Conversation not found for chatId: $chatId")
        }
    }

     override fun deleteChatProperty(property: String): Boolean {
         return dao.deleteConversationMetadata(chatId, userId, listOf(property))
     }

     private fun requireChatConversation() = dao.getChatConversation(chatId, userId)
         ?: throw IllegalStateException("Conversation not found for chatId: $chatId")
}
