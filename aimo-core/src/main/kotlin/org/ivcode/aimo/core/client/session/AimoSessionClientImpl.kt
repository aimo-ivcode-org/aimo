package org.ivcode.aimo.core.client.session

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoSessionClient
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

internal class   AimoSessionClientImpl (
    override val chatId: UUID,
    private val dao: AimoChatClientDao,
    private val model: AimoChatModel,
    private val tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    metadata: Map<String, Any>,
    private val sessionCache: AimoSessionCache = NoOpAimoSessionCache,
) : AimoSessionClient {

    private val metadata: MutableMap<String, Any> = (sessionCache.getMetadata(chatId) ?: metadata).toMutableMap()

    init {
        sessionCache.putMetadata(chatId, this.metadata)
    }

    override fun createChatClient(): AimoChatClient {
        return AimoChatClientImpl (
            chatId = chatId,
            session = this,
            dao = dao,
            model = model,
            tools = tools,
            systemMessages = systemMessages,
            sessionCache = sessionCache,
        )
    }

    override fun addMessages(messages: List<AimoChatMessage>) {
        if(messages.isEmpty()) {
            throw IllegalArgumentException("AimoSessionClientImpl addMessages should have at least one message")
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

    override fun getMetadata(): Map<String, Any> {
        return metadata.toMap()
    }

    override fun readMetadata(): Map<String, Any> {
        return dao.getChatSession(chatId)?.let { session ->
            replaceAllMetadata(session.metadata)
            sessionCache.putMetadata(chatId, session.metadata)
            metadata.toMap()
        } ?: throw IllegalStateException("Chat session not found for chatId: $chatId")
    }

    override fun getProperty(property: String): Any? {
        return metadata[property]
    }

    override fun readProperty(property: String): Any? {
        return readMetadata()[property]
    }

    override fun writeProperty(property: String, value: Any) {
        dao.upsertSessionMetadata(chatId, mapOf(property to value))
        putMetadata(property, value)
        sessionCache.upsertMetadata(chatId, mapOf(property to value))
    }

    override fun deleteProperty(property: String): Boolean {
        val deleted = dao.deleteSessionMetadata(chatId, listOf(property))
        if (deleted) {
            removeMetadata(property)
            sessionCache.removeMetadata(chatId, listOf(property))
        }
        return deleted
    }

    @Synchronized
    private fun replaceAllMetadata(metadata: Map<String, Any>) {
        this.metadata.clear()
        this.metadata.putAll(metadata)
    }

    @Synchronized
    private fun putMetadata(name: String, value: Any) {
        this.metadata[name] = value
    }

    @Synchronized
    private fun removeMetadata(name: String) {
        this.metadata.remove(name)
    }
}