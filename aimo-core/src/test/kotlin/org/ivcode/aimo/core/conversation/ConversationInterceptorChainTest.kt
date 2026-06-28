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
 * - Context propagates through chain
 * - Interceptors can modify context
 * - Different operations have appropriate context keys
 */
class ConversationInterceptorChainTest {

    @Test
    fun `single interceptor executes and calls proceed`() {
        val callLog = mutableListOf<String>()

        val interceptor = LoggingInterceptor("test", callLog)
        val context = mutableMapOf<String, Any>(
            "operation" to "getMessages",
            "chatId" to UUID.randomUUID()
        )

        val result = buildAndExecuteChain(listOf(interceptor), context) {
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

        val interceptor1 = LoggingInterceptor("first", callLog)
        val interceptor2 = LoggingInterceptor("second", callLog)
        val interceptor3 = LoggingInterceptor("third", callLog)
        val context = mutableMapOf<String, Any>(
            "operation" to "getMessages"
        )

        buildAndExecuteChain(listOf(interceptor1, interceptor2, interceptor3), context) {
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
    fun `interceptors can modify context`() {
        val callLog = mutableListOf<String>()

        val modifyingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
                context["modified"] = true
                callLog.add("modified")
                return chain.proceed(context)
            }
        }

        val readingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
                val value = context["modified"]
                callLog.add("read:$value")
                return chain.proceed(context)
            }
        }

        val context = mutableMapOf<String, Any>("operation" to "test")

        buildAndExecuteChain(listOf(modifyingInterceptor, readingInterceptor), context) {
            assertEquals(true, it["modified"], "Context modification should be visible in final action")
            null
        }

        assertTrue(callLog.contains("modified"))
        assertTrue(callLog.contains("read:true"))
    }

    @Test
    fun `getMessages operation has correct context keys`() {
        var capturedContext: MutableMap<String, Any>? = null

        val capturingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
                capturedContext = context
                return chain.proceed(context)
            }
        }

        val chatId = UUID.randomUUID()
        val context = mutableMapOf<String, Any>(
            "operation" to "getMessages",
            "chatId" to chatId,
            "maxCacheCharacters" to 1000L
        )

        buildAndExecuteChain(listOf(capturingInterceptor), context) { null }

        assertNotNull(capturedContext)
        assertEquals("getMessages", capturedContext!!["operation"])
        assertEquals(chatId, capturedContext!!["chatId"])
        assertEquals(1000L, capturedContext!!["maxCacheCharacters"])
    }

    @Test
    fun `addMessages operation has correct context keys`() {
        var capturedContext: MutableMap<String, Any>? = null

        val capturingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
                capturedContext = context
                return chain.proceed(context)
            }
        }

        val chatId = UUID.randomUUID()
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
        val context = mutableMapOf<String, Any>(
            "operation" to "addMessages",
            "chatId" to chatId,
            "requestId" to requestId,
            "messages" to messages
        )

        buildAndExecuteChain(listOf(capturingInterceptor), context) { null }

        assertNotNull(capturedContext)
        assertEquals("addMessages", capturedContext!!["operation"])
        assertEquals(requestId, capturedContext!!["requestId"])
        assertEquals(messages, capturedContext!!["messages"])
    }

    @Test
    fun `writeChatProperty operation has correct context keys`() {
        var capturedContext: MutableMap<String, Any>? = null

        val capturingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
                capturedContext = context
                return chain.proceed(context)
            }
        }

        val context = mutableMapOf<String, Any>(
            "operation" to "writeChatProperty",
            "chatId" to UUID.randomUUID(),
            "property" to "testKey",
            "value" to "testValue"
        )

        buildAndExecuteChain(listOf(capturingInterceptor), context) { null }

        assertNotNull(capturedContext)
        assertEquals("writeChatProperty", capturedContext!!["operation"])
        assertEquals("testKey", capturedContext!!["property"])
        assertEquals("testValue", capturedContext!!["value"])
    }

    @Test
    fun `interceptors can short-circuit the chain`() {
        val callLog = mutableListOf<String>()

        val shortCircuitInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
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

        val context = mutableMapOf<String, Any>("operation" to "getMessages")

        @Suppress("UNCHECKED_CAST")
        val result = buildAndExecuteChain(listOf(shortCircuitInterceptor, neverCalledInterceptor), context) {
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
        val context = mutableMapOf<String, Any>()

        buildAndExecuteChain(emptyList(), context) {
            callLog.add("final-action")
            null
        }

        assertEquals(1, callLog.size)
        assertEquals("final-action", callLog[0])
    }

    @Test
    fun `interceptors can return nullable results`() {
        val interceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
                return chain.proceed(context)
            }
        }

        val context = mutableMapOf<String, Any>()

        val result = buildAndExecuteChain(listOf(interceptor), context) { null }

        assertNull(result)
    }

    @Test
    fun `interceptors handle different operation types`() {
        var capturedOperations = mutableListOf<String>()

        val operationTrackingInterceptor = object : ConversationInterceptor {
            override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
                capturedOperations.add(context["operation"] as String)
                return chain.proceed(context)
            }
        }

        // Test different operations
        listOf("getMessages", "addMessages", "getChatMetadata", "getChatProperty", "writeChatProperty", "deleteChatProperty").forEach { op ->
            val context = mutableMapOf<String, Any>("operation" to op)
            buildAndExecuteChain(listOf(operationTrackingInterceptor), context) { null }
        }

        assertEquals(6, capturedOperations.size)
        assertTrue(capturedOperations.contains("getMessages"))
        assertTrue(capturedOperations.contains("writeChatProperty"))
    }

    // Helper methods

    private fun buildAndExecuteChain(
        interceptors: List<ConversationInterceptor>,
        context: MutableMap<String, Any>,
        finalAction: (MutableMap<String, Any>) -> Any?
    ): Any? {
        val chain = buildChain(interceptors, 0, finalAction)
        return chain.proceed(context)
    }

    private fun buildChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (MutableMap<String, Any>) -> Any?
    ): ConversationInterceptor.Chain {
        return object : ConversationInterceptor.Chain {
            override fun proceed(context: MutableMap<String, Any>): Any? {
                return if (index < interceptors.size) {
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, context)
                } else {
                    finalAction(context)
                }
            }
        }
    }

    private class LoggingInterceptor(
        private val name: String,
        private val callLog: MutableList<String>
    ) : ConversationInterceptor {
        override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
            callLog.add(name)
            return chain.proceed(context)
        }
    }
}