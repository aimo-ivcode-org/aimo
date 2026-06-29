package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ConversationInterceptor chain of responsibility pattern.
 *
 * Verifies:
 * - Interceptors execute in registration order
 * - Metadata propagates through chain
 * - Interceptors can modify metadata
 * - ChatId is properly passed to interceptors
 */
class ConversationInterceptorChainTest {

    @Test
    fun `single interceptor executes and calls proceed`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val interceptor = LoggingInterceptor("test", callLog)
        val metadata = mutableMapOf<String, Any>()

        val result = buildAndExecuteChain(listOf(interceptor), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            emptyList<AimoChatMessage>()
        }

        assertEquals(2, callLog.size)
        assertEquals("test", callLog[0], "Interceptor should execute first")
        assertEquals("final-action", callLog[1], "Final action should execute last")
        assertNotNull(result)
    }

    @Test
    fun `multiple interceptors execute in order`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val interceptor1 = LoggingInterceptor("first", callLog)
        val interceptor2 = LoggingInterceptor("second", callLog)
        val interceptor3 = LoggingInterceptor("third", callLog)
        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(interceptor1, interceptor2, interceptor3), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            null
        }

        assertEquals(4, callLog.size)
        assertEquals("first", callLog[0])
        assertEquals("second", callLog[1])
        assertEquals("third", callLog[2])
        assertEquals("final-action", callLog[3])
    }

    @Test
    fun `interceptors can modify metadata`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val modifyingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, chatId: UUID, metadata: MutableMap<String, Any>): Any? {
                metadata["modified"] = true
                callLog.add("modified")
                return chain.proceed(chatId, metadata)
            }
        }

        val readingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, chatId: UUID, metadata: MutableMap<String, Any>): Any? {
                val value = metadata["modified"]
                callLog.add("read:$value")
                return chain.proceed(chatId, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(modifyingInterceptor, readingInterceptor), chatId, metadata) { _, md ->
            assertEquals(true, md["modified"], "Metadata modification should be visible in final action")
            null
        }

        assertTrue(callLog.contains("modified"))
        assertTrue(callLog.contains("read:true"))
    }

    @Test
    fun `getMessages operation has correct metadata keys`() {
        var capturedMetadata: MutableMap<String, Any>? = null
        val chatId = UUID.randomUUID()

        val capturingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, chatId: UUID, metadata: MutableMap<String, Any>): Any? {
                capturedMetadata = metadata
                return chain.proceed(chatId, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>(
            "maxCacheCharacters" to 1000L
        )

        buildAndExecuteChain(listOf(capturingInterceptor), chatId, metadata) { _, _ -> null }

        assertNotNull(capturedMetadata)
        assertEquals(1000L, capturedMetadata!!["maxCacheCharacters"])
    }

    @Test
    fun `addMessages operation has correct metadata keys`() {
        var capturedMetadata: MutableMap<String, Any>? = null
        var capturedChatId: UUID? = null
        val chatId = UUID.randomUUID()

        val capturingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, cid: UUID, metadata: MutableMap<String, Any>): Any? {
                capturedChatId = cid
                capturedMetadata = metadata
                return chain.proceed(cid, metadata)
            }
        }

        val requestId = UUID.randomUUID()
        val messages = listOf(
            AimoChatMessage(
                messageId = 1,
                type = AimoChatMessageType.USER,
                content = "test",
                thinking = null,
                toolName = null,
                done = true
            )
        )
        val metadata = mutableMapOf<String, Any>(
            "requestId" to requestId,
            "messages" to messages
        )

        buildAndExecuteChain(listOf(capturingInterceptor), chatId, metadata) { _, _ -> null }

        assertNotNull(capturedChatId)
        assertNotNull(capturedMetadata)
        assertEquals(chatId, capturedChatId)
        assertEquals(requestId, capturedMetadata!!["requestId"])
        assertEquals(messages, capturedMetadata!!["messages"])
    }

    @Test
    fun `writeChatProperty operation has correct metadata keys`() {
        var capturedMetadata: MutableMap<String, Any>? = null
        val chatId = UUID.randomUUID()

        val capturingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, cid: UUID, metadata: MutableMap<String, Any>): Any? {
                capturedMetadata = metadata
                return chain.proceed(cid, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>(
            "property" to "testKey",
            "value" to "testValue"
        )

        buildAndExecuteChain(listOf(capturingInterceptor), chatId, metadata) { _, _ -> null }

        assertNotNull(capturedMetadata)
        assertEquals("testKey", capturedMetadata!!["property"])
        assertEquals("testValue", capturedMetadata!!["value"])
    }

    @Test
    fun `interceptors can short-circuit the chain`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()

        val shortCircuitInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, cid: UUID, metadata: MutableMap<String, Any>): Any? {
                callLog.add("short-circuit")
                // Don't call chain.proceed() - return cached value
                return listOf(
                    AimoChatMessage(
                        messageId = 1,
                        type = AimoChatMessageType.ASSISTANT,
                        content = "cached",
                        thinking = null,
                        toolName = null,
                        done = true
                    )
                )
            }
        }

        val neverCalledInterceptor = LoggingInterceptor("never-called", callLog)

        val metadata = mutableMapOf<String, Any>()

        @Suppress("UNCHECKED_CAST")
        val result = buildAndExecuteChain(listOf(shortCircuitInterceptor, neverCalledInterceptor), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            emptyList<AimoChatMessage>()
        } as List<AimoChatMessage>

        assertEquals(1, callLog.size)
        assertEquals("short-circuit", callLog[0])
        assertEquals("cached", result.first().content)
    }

    @Test
    fun `empty interceptor list executes final action directly`() {
        val callLog = mutableListOf<String>()
        val chatId = UUID.randomUUID()
        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(emptyList(), chatId, metadata) { _, _ ->
            callLog.add("final-action")
            null
        }

        assertEquals(1, callLog.size)
        assertEquals("final-action", callLog[0])
    }

    @Test
    fun `interceptors can return nullable results`() {
        val chatId = UUID.randomUUID()
        val interceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, cid: UUID, metadata: MutableMap<String, Any>): Any? {
                return chain.proceed(cid, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>()

        val result = buildAndExecuteChain(listOf(interceptor), chatId, metadata) { _, _ -> null }

        assertNull(result)
    }

    @Test
    fun `interceptors receive correct chatId`() {
        var capturedChatIds = mutableListOf<UUID>()
        val originalChatId = UUID.randomUUID()

        val trackingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, cid: UUID, metadata: MutableMap<String, Any>): Any? {
                capturedChatIds.add(cid)
                return chain.proceed(cid, metadata)
            }
        }

        val metadata = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(trackingInterceptor), originalChatId, metadata) { cid, _ ->
            capturedChatIds.add(cid)
            null
        }

        assertEquals(2, capturedChatIds.size)
        assertTrue(capturedChatIds.all { it == originalChatId })
    }

    // Helper methods

    private fun buildAndExecuteChain(
        interceptors: List<ConversationInterceptor>,
        chatId: UUID,
        metadata: MutableMap<String, Any>,
        finalAction: (UUID, MutableMap<String, Any>) -> Any?
    ): Any? {
        val chain = buildChain(interceptors, 0, finalAction)
        return chain.proceed(chatId, metadata)
    }

    private fun buildChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (UUID, MutableMap<String, Any>) -> Any?
    ): ConversationInterceptor.Chain {
        return object : ConversationInterceptor.Chain {
            override fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Any? {
                return if (index < interceptors.size) {
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, chatId, metadata)
                } else {
                    finalAction(chatId, metadata)
                }
            }
        }
    }

    private class LoggingInterceptor(
        private val name: String,
        private val callLog: MutableList<String>
    ) : ConversationInterceptor {
        override fun intercept(chain: ConversationInterceptor.Chain, chatId: UUID, metadata: MutableMap<String, Any>): Any? {
            callLog.add(name)
            return chain.proceed(chatId, metadata)
        }
    }
}