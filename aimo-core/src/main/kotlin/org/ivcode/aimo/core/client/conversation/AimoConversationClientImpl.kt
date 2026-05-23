package org.ivcode.aimo.core.client.conversation

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.AimoSessionCacheProvider
import org.ivcode.aimo.core.cache.NoOpAimoSessionCacheProvider
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
    private val tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val sessionCacheProvider: AimoSessionCacheProvider = NoOpAimoSessionCacheProvider,
) : AimoConversationClient {

    private val sessionCache: AimoSessionCache = sessionCacheProvider.get(chatId)

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
            dao.getChatRequests(chatId)
        } else {
            dao.getChatRequests(
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
        dao.addChatRequest(
            ChatRequestEntity(
                chatId = chatId,
                requestId = requestId,
                messages = messages.map { it.toChatMessageEntity(requestId) },
                requestCharacters = messages.sumOf { it.content?.length ?: 0 },
                createdAt = Instant.now(),
            )
        )
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
        val success = dao.upsertConversationMetadata(chatId, mapOf(property to value))
        if (!success) {
            throw IllegalStateException("Conversation not found for chatId: $chatId")
        }
    }

    override fun deleteChatProperty(property: String): Boolean {
        return dao.deleteConversationMetadata(chatId, listOf(property))
    }

    override fun getRuntimeMetadata(): Map<String, Any> {
        return sessionCache.getSessionProperties()
    }

    override fun getRuntimeProperty(property: String): Any? {
        return sessionCache.getSessionProperty(property)
    }

    override fun writeRuntimeProperty(property: String, value: Any) {
        sessionCache.writeSessionProperty(property, value)
    }

    override fun deleteRuntimeProperty(property: String): Boolean {
        return sessionCache.deleteSessionProperty(property)
    }

    private fun requireChatConversation() = dao.getChatConversation(chatId)
        ?: throw IllegalStateException("Conversation not found for chatId: $chatId")
}
