package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for ChatClientInterceptor chain of responsibility pattern.
 *
 * Verifies:
 * - Multiple interceptors execute in registration order
 * - Context propagates through chain
 * - Interceptors can modify context
 * - Final action executes only after all interceptors
 * - Short-circuit behavior works correctly
 */
class ChatClientInterceptorChainTest {

    @Test
    fun `single interceptor executes and calls proceed`() {
        val callLog = mutableListOf<String>()

        val interceptor = TrackingInterceptor("test", callLog)
        val context = mutableMapOf<String, Any>()

        val response = buildAndExecuteChain(listOf(interceptor), context) {
            callLog.add("final-action")
            createTestResponse()
        }

        assertEquals(2, callLog.size)
        assertEquals("test", callLog[0], "Interceptor should execute first")
        assertEquals("final-action", callLog[1], "Final action should execute last")
        assertNotNull(response)
    }

    @Test
    fun `multiple interceptors execute in correct order`() {
        val callLog = mutableListOf<String>()

        val interceptor1 = TrackingInterceptor("first", callLog)
        val interceptor2 = TrackingInterceptor("second", callLog)
        val interceptor3 = TrackingInterceptor("third", callLog)
        val context = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(interceptor1, interceptor2, interceptor3), context) {
            callLog.add("final-action")
            createTestResponse()
        }

        assertEquals(4, callLog.size)
        assertEquals("first", callLog[0], "First interceptor should execute first")
        assertEquals("second", callLog[1], "Second interceptor should execute second")
        assertEquals("third", callLog[2], "Third interceptor should execute third")
        assertEquals("final-action", callLog[3], "Final action should execute last")
    }

    @Test
    fun `five interceptors execute in correct order`() {
        val callLog = mutableListOf<String>()

        val interceptors = listOf(
            TrackingInterceptor("i1", callLog),
            TrackingInterceptor("i2", callLog),
            TrackingInterceptor("i3", callLog),
            TrackingInterceptor("i4", callLog),
            TrackingInterceptor("i5", callLog)
        )
        val context = mutableMapOf<String, Any>()

        buildAndExecuteChain(interceptors, context) {
            callLog.add("final")
            createTestResponse()
        }

        assertEquals(6, callLog.size)
        assertEquals(listOf("i1", "i2", "i3", "i4", "i5", "final"), callLog)
    }

    @Test
    fun `interceptors can modify context`() {
        val callLog = mutableListOf<String>()

        val modifyingInterceptor = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                context["key1"] = "value1"
                context["timestamp"] = System.currentTimeMillis()
                callLog.add("modified")
                return chain.proceed(context)
            }
        }

        val readingInterceptor = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                val value = context["key1"]
                val timestamp = context["timestamp"]
                callLog.add("read:$value:${timestamp != null}")
                return chain.proceed(context)
            }
        }

        val context = mutableMapOf<String, Any>()

        buildAndExecuteChain(listOf(modifyingInterceptor, readingInterceptor), context) {
            assertNotNull(it["key1"], "Context modification should be visible in final action")
            assertNotNull(it["timestamp"], "Timestamp should be visible in final action")
            createTestResponse()
        }

        assertTrue(callLog.contains("modified"))
        assertTrue(callLog.contains("read:value1:true"))
    }

    @Test
    fun `interceptors can access request context parameters`() {
        val capturedValues = mutableMapOf<String, Any?>()

        val capturingInterceptor = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                capturedValues["chatId"] = context["chatId"]
                capturedValues["requestId"] = context["requestId"]
                capturedValues["prompt"] = context["prompt"]
                return chain.proceed(context)
            }
        }

        val chatId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val context = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "requestId" to requestId,
            "prompt" to "test message"
        )

        buildAndExecuteChain(listOf(capturingInterceptor), context) {
            createTestResponse()
        }

        assertEquals(chatId, capturedValues["chatId"])
        assertEquals(requestId, capturedValues["requestId"])
        assertEquals("test message", capturedValues["prompt"])
    }

    @Test
    fun `interceptors can short-circuit the chain`() {
        val callLog = mutableListOf<String>()

        val shortCircuitInterceptor = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                callLog.add("short-circuit")
                // Don't call chain.proceed() - return cached response
                return createTestResponse("cached response")
            }
        }

        val neverCalledInterceptor = TrackingInterceptor("never-called", callLog)

        val context = mutableMapOf<String, Any>()

        val response = buildAndExecuteChain(listOf(shortCircuitInterceptor, neverCalledInterceptor), context) {
            callLog.add("final-action")
            createTestResponse()
        }

        assertEquals(1, callLog.size)
        assertEquals("short-circuit", callLog[0])
        assertEquals("cached response", response.messages.first().content)
    }

    @Test
    fun `interceptor chain with multiple interceptors modifying context`() {
        val context = mutableMapOf<String, Any>("counter" to 0)

        val incrementingInterceptor1 = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                val counter = context["counter"] as Int
                context["counter"] = counter + 1
                context["interceptor1"] = true
                return chain.proceed(context)
            }
        }

        val incrementingInterceptor2 = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                val counter = context["counter"] as Int
                context["counter"] = counter + 10
                context["interceptor2"] = true
                return chain.proceed(context)
            }
        }

        val incrementingInterceptor3 = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                val counter = context["counter"] as Int
                context["counter"] = counter + 100
                context["interceptor3"] = true
                return chain.proceed(context)
            }
        }

        buildAndExecuteChain(
            listOf(incrementingInterceptor1, incrementingInterceptor2, incrementingInterceptor3),
            context
        ) {
            assertEquals(111, it["counter"], "All interceptors should have modified counter")
            assertEquals(true, it["interceptor1"])
            assertEquals(true, it["interceptor2"])
            assertEquals(true, it["interceptor3"])
            createTestResponse()
        }

        assertEquals(111, context["counter"])
    }

    @Test
    fun `empty interceptor list executes final action directly`() {
        val callLog = mutableListOf<String>()
        val context = mutableMapOf<String, Any>()

        buildAndExecuteChain(emptyList(), context) {
            callLog.add("final-action")
            createTestResponse()
        }

        assertEquals(1, callLog.size)
        assertEquals("final-action", callLog[0])
    }

    @Test
    fun `interceptor can modify response`() {
        val modifyingInterceptor = object : ChatClientInterceptor {
            override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
                val response = chain.proceed(context)
                // Add a marker to the response
                context["response-modified"] = true
                return response.copy(
                    messages = response.messages.map {
                        it.copy(content = "${it.content} [modified]")
                    }
                )
            }
        }

        val context = mutableMapOf<String, Any>()

        val response = buildAndExecuteChain(listOf(modifyingInterceptor), context) {
            createTestResponse("original")
        }

        assertEquals("original [modified]", response.messages.first().content)
        assertEquals(true, context["response-modified"])
    }

    @Test
    fun `builder-level and factory-level interceptors execute in correct order`() {
        val callLog = mutableListOf<String>()

        // Simulate builder-level interceptors (execute first/outer)
        val builderInterceptor1 = TrackingInterceptor("builder-1", callLog)
        val builderInterceptor2 = TrackingInterceptor("builder-2", callLog)

        // Simulate factory-level interceptors (execute second/inner)
        val factoryInterceptor1 = TrackingInterceptor("factory-1", callLog)
        val factoryInterceptor2 = TrackingInterceptor("factory-2", callLog)

        // Combine: builder interceptors go first (outer), then factory defaults (inner)
        val allInterceptors = listOf(
            builderInterceptor1,
            builderInterceptor2,
            factoryInterceptor1,
            factoryInterceptor2
        )

        val context = mutableMapOf<String, Any>()

        buildAndExecuteChain(allInterceptors, context) {
            callLog.add("core-action")
            createTestResponse()
        }

        assertEquals(5, callLog.size)
        assertEquals("builder-1", callLog[0])
        assertEquals("builder-2", callLog[1])
        assertEquals("factory-1", callLog[2])
        assertEquals("factory-2", callLog[3])
        assertEquals("core-action", callLog[4])
    }

    // Helper methods

    private fun buildAndExecuteChain(
        interceptors: List<ChatClientInterceptor>,
        context: MutableMap<String, Any>,
        finalAction: (MutableMap<String, Any>) -> AimoChatResponse
    ): AimoChatResponse {
        val chain = buildChain(interceptors, 0, finalAction)
        return chain.proceed(context)
    }

    private fun buildChain(
        interceptors: List<ChatClientInterceptor>,
        index: Int,
        finalAction: (MutableMap<String, Any>) -> AimoChatResponse
    ): ChatClientInterceptor.Chain {
        return object : ChatClientInterceptor.Chain {
            override fun proceed(context: MutableMap<String, Any>): AimoChatResponse {
                return if (index < interceptors.size) {
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, context)
                } else {
                    finalAction(context)
                }
            }
        }
    }

    private fun createTestResponse(content: String = "test response"): AimoChatResponse {
        return AimoChatResponse(
            chatId = UUID.randomUUID(),
            responseId = UUID.randomUUID(),
            messages = listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.ASSISTANT,
                    content = content,
                    thinking = null,
                    toolName = null,
                    done = true
                )
            ),
            createdAt = Instant.now(),
            usage = null
        )
    }

    private class TrackingInterceptor(
        private val name: String,
        private val callLog: MutableList<String>
    ) : ChatClientInterceptor {
        override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
            callLog.add(name)
            return chain.proceed(context)
        }
    }
}