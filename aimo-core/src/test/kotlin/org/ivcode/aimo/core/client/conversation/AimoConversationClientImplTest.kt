package org.ivcode.aimo.core.client.conversation

import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.NoOpAimoSessionCache
import org.ivcode.aimo.core.cache.SessionCacheStats
import org.ivcode.aimo.core.cache.SessionTokenCalibration
import org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoPrompt
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimoConversationClientImplTest {

    @Test
    fun `chat metadata reads latest durable values from dao`() {
        val dao = AimoChatClientDaoMemory()
        val conversation = dao.createChatConversation(mapOf("title" to "first"))
        val client = createConversationClient(dao, conversation.chatId)

        dao.upsertConversationMetadata(conversation.chatId, mapOf("title" to "second", "status" to "open"))

        assertEquals("second", client.getChatProperty("title"))
        assertEquals(
            mapOf("title" to "second", "status" to "open"),
            client.getChatMetadata(),
        )
    }

    @Test
    fun `chat property writes and deletes are visible across conversation clients`() {
        val dao = AimoChatClientDaoMemory()
        val conversation = dao.createChatConversation()
        val firstClient = createConversationClient(dao, conversation.chatId)
        val secondClient = createConversationClient(dao, conversation.chatId)

        firstClient.writeChatProperty("title", "Shared")
        assertEquals("Shared", secondClient.getChatProperty("title"))

        assertTrue(secondClient.deleteChatProperty("title"))
        assertNull(firstClient.getChatProperty("title"))
    }

    @Test
    fun `runtime metadata stays out of durable conversation metadata`() {
        val dao = AimoChatClientDaoMemory()
        val conversation = dao.createChatConversation()
        val cache = TestSessionCache()
        val client = createConversationClient(dao, conversation.chatId, cache)

        client.writeRuntimeProperty("draft", "local")

        assertEquals("local", client.getRuntimeProperty("draft"))
        assertEquals(mapOf("draft" to "local"), cache.getRuntimeMetadata(conversation.chatId))
        assertNull(dao.getChatConversation(conversation.chatId)?.metadata?.get("draft"))
    }

    private fun createConversationClient(
        dao: AimoChatClientDaoMemory,
        chatId: UUID,
        sessionCache: AimoSessionCache = NoOpAimoSessionCache,
    ): AimoConversationClientImpl {
        return AimoConversationClientImpl(
            chatId = chatId,
            dao = dao,
            model = testModel(),
            tools = emptyList(),
            systemMessages = emptyList(),
            sessionCache = sessionCache,
        )
    }

    private class TestSessionCache : AimoSessionCache {
        private val runtimeMetadata = mutableMapOf<UUID, MutableMap<String, Any>>()

        override fun getRuntimeMetadata(chatId: UUID): Map<String, Any>? = runtimeMetadata[chatId]?.toMap()

        override fun putRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>) {
            runtimeMetadata[chatId] = metadata.toMutableMap()
        }

        override fun upsertRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>) {
            val current = runtimeMetadata.getOrPut(chatId) { mutableMapOf() }
            current.putAll(metadata)
        }

        override fun removeRuntimeMetadata(chatId: UUID, keys: List<String>) {
            val current = runtimeMetadata[chatId] ?: return
            keys.forEach(current::remove)
        }

        override fun getMessages(chatId: UUID): List<AimoChatMessage>? = null
        override fun putMessages(chatId: UUID, messages: List<AimoChatMessage>) = Unit
        override fun appendMessages(chatId: UUID, messages: List<AimoChatMessage>) = Unit
        override fun getTokenCalibration(chatId: UUID): SessionTokenCalibration? = null
        override fun putTokenCalibration(chatId: UUID, calibration: SessionTokenCalibration) = Unit
        override fun getCacheStats(chatId: UUID): SessionCacheStats? = null
        override fun putCacheStats(chatId: UUID, stats: SessionCacheStats) = Unit
        override fun evict(chatId: UUID) {
            runtimeMetadata.remove(chatId)
        }
    }

    private fun testModel(): AimoChatModel {
        return AimoChatModel(
            name = "test",
            chatEngine = object : AimoChatEngine {
                override val options = AimoChatOptions()

                override fun call(prompt: AimoPrompt): AimoChatResponse {
                    throw UnsupportedOperationException("Not used in conversation client tests")
                }

                override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                    throw UnsupportedOperationException("Not used in conversation client tests")
                }
            },
            options = AimoChatOptions(),
        )
    }
}






