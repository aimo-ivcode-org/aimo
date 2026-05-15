package org.ivcode.aimo.core.client.conversation

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.NoOpAimoSessionCache
import org.ivcode.aimo.core.client.chat.AimoChatClientImpl
import org.ivcode.aimo.core.controller.SystemMessageCallback
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.ChatRequestEntity
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.toChatMessageEntity
import java.time.Instant
import java.util.UUID

internal class AimoConversationClientImpl(
    override val chatId: UUID,
    private val dao: AimoChatClientDao,
    private val model: AimoChatModel,
    private val tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val sessionCache: AimoSessionCache = NoOpAimoSessionCache,
) : AimoConversationClient {

    override fun createChatClient(): AimoChatClient {
        return AimoChatClientImpl (
            chatId = chatId,
            conversation = this,
            dao = dao,
            model = model,
            tools = tools,
            systemMessages = systemMessages,
            sessionCache = sessionCache,
        )
    }

    override fun addMessages(messages: List<AimoChatMessage>) {
        if(messages.isEmpty()) {
            throw IllegalArgumentException("AimoConversationClientImpl addMessages should have at least one message")
        }

        val requestId = UUID.randomUUID()
        dao.addChatRequest(
            ChatRequestEntity(
                chatId = chatId,
                requestId = requestId,
                messages = messages.map { it.toChatMessageEntity(requestId) },
                requestCharacters = messages.sumOf { it.content?.length ?: 0 },
                createdAt = Instant.now(),
            )
        )
        sessionCache.appendMessages(chatId, messages)
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
        dao.upsertConversationMetadata(chatId, mapOf(property to value))
    }

    override fun deleteChatProperty(property: String): Boolean {
        return dao.deleteConversationMetadata(chatId, listOf(property))
    }

    override fun getRuntimeMetadata(): Map<String, Any> {
        return sessionCache.getRuntimeMetadata(chatId)?.toMap() ?: emptyMap()
    }

    override fun getRuntimeProperty(property: String): Any? {
        return sessionCache.getRuntimeMetadata(chatId)?.get(property)
    }

    override fun writeRuntimeProperty(property: String, value: Any) {
        sessionCache.upsertRuntimeMetadata(chatId, mapOf(property to value))
    }

    override fun deleteRuntimeProperty(property: String): Boolean {
        val metadata = sessionCache.getRuntimeMetadata(chatId) ?: return false
        if (!metadata.containsKey(property)) {
            return false
        }

        sessionCache.removeRuntimeMetadata(chatId, listOf(property))
        return true
    }

    private fun requireChatConversation() = dao.getChatConversation(chatId)
        ?: throw IllegalStateException("Conversation not found for chatId: $chatId")
}


