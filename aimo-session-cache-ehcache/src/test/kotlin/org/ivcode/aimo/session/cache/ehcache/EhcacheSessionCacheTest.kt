package org.ivcode.aimo.session.cache.ehcache

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.cache.SessionTokenCalibration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EhcacheSessionCacheTest {

    @Test
    fun `stores and appends messages`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, ttl = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache = provider.get(chatId)
            cache.putCachedMessages(listOf(message(1, "hello")))
            cache.appendCachedMessages(listOf(message(2, "world")))

            val messages = cache.getCachedMessages()
            assertNotNull(messages)
            assertEquals(listOf(1, 2), messages.map { it.messageId })
            assertEquals(listOf("hello", "world"), messages.map { it.content })
        } finally {
            provider.close()
        }
    }

    @Test
    fun `writes and reads runtime properties`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, ttl = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache = provider.get(chatId)
            cache.writeSessionProperty("a", 1)
            cache.writeSessionProperty("b", 2)

            val a = cache.getSessionProperty("a")
            val b = cache.getSessionProperty("b")
            assertEquals(1, a)
            assertEquals(2, b)

            val allProps = cache.getSessionProperties()
            assertEquals(1, allProps["a"])
            assertEquals(2, allProps["b"])

            val calibration = SessionTokenCalibration(
                observedPromptCharacters = 120,
                observedPromptTokens = 30,
            )
            cache.writeSessionProperty(CACHE_KEY__TOKEN_CALIBRATION, calibration)
            assertEquals(calibration, cache.getTokenCalibration())
        } finally {
            provider.close()
        }
    }

    @Test
    fun `shares cache state for same chatId`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, ttl = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache1 = provider.get(chatId)
            val cache2 = provider.get(chatId)

            cache1.writeSessionProperty("key", "value")
            assertEquals("value", cache2.getSessionProperty("key"))
        } finally {
            provider.close()
        }
    }

    @Test
    fun `cache evict clears shared state for chatId`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, ttl = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache = provider.get(chatId)
            cache.writeSessionProperty("x", "y")
            cache.putCachedMessages(listOf(message(1, "hello")))

            cache.evict()

            val newCache = provider.get(chatId)
            assertEquals(emptyMap<String, Any>(), newCache.getSessionProperties())
            assertNull(newCache.getCachedMessages())
            assertNull(newCache.getTokenCalibration())
        } finally {
            provider.close()
        }
    }

    private fun org.ivcode.aimo.core.cache.AimoSessionCache.getCachedMessages(): List<AimoChatMessage>? {
        @Suppress("UNCHECKED_CAST")
        return getSessionProperty(CACHE_KEY__MESSAGES) as? List<AimoChatMessage>
    }

    private fun org.ivcode.aimo.core.cache.AimoSessionCache.putCachedMessages(messages: List<AimoChatMessage>) {
        writeSessionProperty(CACHE_KEY__MESSAGES, messages.toList())
    }

    private fun org.ivcode.aimo.core.cache.AimoSessionCache.appendCachedMessages(messages: List<AimoChatMessage>) {
        if (messages.isEmpty()) return
        putCachedMessages(getCachedMessages().orEmpty() + messages)
    }

    private fun org.ivcode.aimo.core.cache.AimoSessionCache.getTokenCalibration(): SessionTokenCalibration? {
        return getSessionProperty(CACHE_KEY__TOKEN_CALIBRATION) as? SessionTokenCalibration
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

    private companion object {
        const val CACHE_KEY__MESSAGES = "chat.messages"
        const val CACHE_KEY__TOKEN_CALIBRATION = "chat.tokenCalibration"
    }
}

