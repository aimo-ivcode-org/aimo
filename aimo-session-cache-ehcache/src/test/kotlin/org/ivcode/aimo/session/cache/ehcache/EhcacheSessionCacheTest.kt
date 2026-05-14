package org.ivcode.aimo.session.cache.ehcache

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.cache.SessionTokenCalibration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EhcacheSessionCacheTest {

    @Test
    fun `stores and appends messages`() {
        val cache = EhcacheSessionCache(maxEntries = 100, ttl = java.time.Duration.ofMinutes(5))
        try {
            val chatId = UUID.randomUUID()
            cache.putMessages(chatId, listOf(message(1, "hello")))
            cache.appendMessages(chatId, listOf(message(2, "world")))

            val messages = cache.getMessages(chatId)
            assertNotNull(messages)
            assertEquals(listOf(1, 2), messages.map { it.messageId })
            assertEquals(listOf("hello", "world"), messages.map { it.content })
        } finally {
            cache.close()
        }
    }

    @Test
    fun `upserts metadata and stores token calibration`() {
        val cache = EhcacheSessionCache(maxEntries = 100, ttl = java.time.Duration.ofMinutes(5))
        try {
            val chatId = UUID.randomUUID()
            cache.putMetadata(chatId, mapOf("a" to 1))
            cache.upsertMetadata(chatId, mapOf("b" to 2, "a" to 3))

            val metadata = cache.getMetadata(chatId)
            assertNotNull(metadata)
            assertEquals(3, metadata["a"])
            assertEquals(2, metadata["b"])

            val calibration = SessionTokenCalibration(
                observedPromptCharacters = 120,
                observedPromptTokens = 30,
            )
            cache.putTokenCalibration(chatId, calibration)
            assertEquals(calibration, cache.getTokenCalibration(chatId))
        } finally {
            cache.close()
        }
    }

    @Test
    fun `evict removes all session state`() {
        val cache = EhcacheSessionCache(maxEntries = 100, ttl = java.time.Duration.ofMinutes(5))
        try {
            val chatId = UUID.randomUUID()
            cache.putMetadata(chatId, mapOf("x" to "y"))
            cache.putMessages(chatId, listOf(message(1, "hello")))

            cache.evict(chatId)

            assertNull(cache.getMetadata(chatId))
            assertNull(cache.getMessages(chatId))
            assertNull(cache.getTokenCalibration(chatId))
        } finally {
            cache.close()
        }
    }

    private fun message(id: Int, content: String): AimoChatMessage {
        return AimoChatMessage(
            messageId = id,
            type = AimoChatMessageType.USER,
            content = content,
            thinking = null,
            toolName = null,
            done = true,
        )
    }
}

