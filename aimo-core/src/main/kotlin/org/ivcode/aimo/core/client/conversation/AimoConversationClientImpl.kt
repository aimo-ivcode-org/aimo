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

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>) {
        if(messages.isEmpty()) {
            throw IllegalArgumentException("AimoConversationClientImpl addMessages should have at least one message")
        }

        dao.addChatRequest(
            ChatRequestEntity(
                chatId = chatId,
                requestId = requestId,
                messages = messages.map { it.toChatMessageEntity(requestId) },
                requestCharacters = messages.sumOf { it.content?.length ?: 0 },
                createdAt = Instant.now(),
            )
        )
        appendCachedMessages(messages)
    }

    override fun getMessages(): List<AimoChatMessage>? {
        @Suppress("UNCHECKED_CAST")
        var cached = sessionCache.getSessionProperty(CACHE_KEY__MESSAGES) as? List<AimoChatMessage>

        // Seed the cache on first call: load all durable history into the session cache
        if (cached == null) {
            cached = dao.getChatRequests(chatId)
                .flatMap { it.messages.map { m -> m.toAimoChatMessage() } }
            if (cached.isNotEmpty()) {
                sessionCache.writeSessionProperty(CACHE_KEY__MESSAGES, cached as Any)
            }
        }

        return cached.takeIf { it.isNotEmpty() }
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

    private fun appendCachedMessages(messages: List<AimoChatMessage>) {
        sessionCache.appendToSessionProperty(CACHE_KEY__MESSAGES, messages.map { it as Any })
    }

    private companion object {
        const val CACHE_KEY__MESSAGES = "chat.messages"
    }
}


