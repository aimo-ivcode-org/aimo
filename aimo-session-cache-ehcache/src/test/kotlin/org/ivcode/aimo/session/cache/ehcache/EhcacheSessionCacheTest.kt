package org.ivcode.aimo.session.cache.ehcache

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class EhcacheSessionCacheTest {

    @Test
    fun `stores and appends messages`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, tti = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache = provider.get(chatId)
            cache.putCachedMessages(listOf(message(1, "hello")))
            cache.appendToSessionProperty(CACHE_KEY__MESSAGES, listOf(message(2, "world") as Any))

            val messages = cache.getMessages()
            assertNotNull(messages)
            assertEquals(listOf(1, 2), messages.map { it.messageId })
            assertEquals(listOf("hello", "world"), messages.map { it.content })
        } finally {
            provider.close()
        }
    }

    @Test
    fun `writes and reads runtime properties`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, tti = java.time.Duration.ofMinutes(5))
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
        } finally {
            provider.close()
        }
    }

    @Test
    fun `shares cache state for same chatId`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, tti = java.time.Duration.ofMinutes(5))
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
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, tti = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache = provider.get(chatId)
            cache.writeSessionProperty("x", "y")
            cache.putCachedMessages(listOf(message(1, "hello")))

            cache.evict()

            val newCache = provider.get(chatId)
            assertEquals(emptyMap<String, Any>(), newCache.getSessionProperties())
            assertNull(newCache.getMessages())
        } finally {
            provider.close()
        }
    }

    @Test
    fun `appendToSessionProperty atomically appends to list without lost updates`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, tti = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache = provider.get(chatId)

            // Initial list
            cache.putCachedMessages(listOf(message(1, "first")))

            // Append second message atomically
            cache.appendToSessionProperty(CACHE_KEY__MESSAGES, listOf(message(2, "second") as Any))

            // Append third message atomically
            cache.appendToSessionProperty(CACHE_KEY__MESSAGES, listOf(message(3, "third") as Any))

            // Verify all messages are present in order
            val messages = cache.getMessages()
            assertNotNull(messages)
            assertEquals(3, messages.size)
            assertEquals(listOf(1, 2, 3), messages.map { it.messageId })
            assertEquals(listOf("first", "second", "third"), messages.map { it.content })
        } finally {
            provider.close()
        }
    }

    @Test
    fun `appendToSessionProperty is thread-safe across concurrent requests`() {
        val provider = EhcacheRuntimeStateProvider(maxEntries = 100, tti = java.time.Duration.ofMinutes(5))
        try {
            val chatId = java.util.UUID.randomUUID()
            val cache = provider.get(chatId)

            // Start with empty list
            cache.putCachedMessages(emptyList())

            // Simulate 10 concurrent "chat requests" each appending a message
            val executor = java.util.concurrent.Executors.newFixedThreadPool(10)
            val latch = java.util.concurrent.CountDownLatch(10)

            for (i in 1..10) {
                executor.submit {
                    try {
                        val messageToAppend = message(i, "message-$i") as Any
                        cache.appendToSessionProperty(CACHE_KEY__MESSAGES, listOf(messageToAppend))
                    } finally {
                        latch.countDown()
                    }
                }
            }

            executor.shutdown()
            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "Concurrent appends timed out")

            // Verify all 10 messages are present with no lost updates
            val messages = cache.getMessages()
            assertNotNull(messages)
            assertEquals(10, messages.size, "Expected all 10 appended messages, but got ${messages.size}")
            assertEquals(
                (1..10).toList(),
                messages.map { it.messageId }.sorted(),
                "All message IDs should be present"
            )
        } finally {
            provider.close()
        }
    }

    private fun org.ivcode.aimo.core.cache.AimoSessionCache.getMessages(): List<AimoChatMessage>? {
        @Suppress("UNCHECKED_CAST")
        return getSessionProperty(CACHE_KEY__MESSAGES) as? List<AimoChatMessage>
    }

    private fun org.ivcode.aimo.core.cache.AimoSessionCache.putCachedMessages(messages: List<AimoChatMessage>) {
        writeSessionProperty(CACHE_KEY__MESSAGES, messages.toList())
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
    }
}

