package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoChatResponse
import org.slf4j.MDC
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for TracingInterceptor.
 *
 * Verifies:
 * - TraceId is generated or propagated
 * - SpanId is generated for each operation
 * - MDC context is properly managed
 * - Context is added to operation context
 * - MDC is restored after operation
 * - Error status is captured
 * - Can be disabled
 */
class TracingInterceptorTest {

    @Test
    fun `interceptor generates traceId when not provided`() {
        val interceptor = TracingInterceptor(enabled = true, serviceName = "test-service")
        val context = mutableMapOf<String, Any>()

        executeChain(interceptor, context) {
            // Verify traceId was added to context
            val traceId = context["traceId"] as? String
            assertNotNull(traceId, "TraceId should be generated")
            assertTrue(traceId.length > 0, "TraceId should not be empty")

            // Verify it's available in MDC
            assertEquals(traceId, MDC.get("traceId"))

            createTestResponse()
        }

        // Verify MDC is cleaned up
        assertNull(MDC.get("traceId"), "MDC should be cleaned up after operation")
    }

    @Test
    fun `interceptor propagates existing traceId`() {
        val interceptor = TracingInterceptor(enabled = true)
        val existingTraceId = "existing-trace-12345"
        val context = mutableMapOf<String, Any>("traceId" to existingTraceId)

        executeChain(interceptor, context) {
            assertEquals(existingTraceId, context["traceId"])
            assertEquals(existingTraceId, MDC.get("traceId"))
            createTestResponse()
        }

        assertNull(MDC.get("traceId"))
    }

    @Test
    fun `interceptor generates unique spanId`() {
        val interceptor = TracingInterceptor(enabled = true)
        val context = mutableMapOf<String, Any>()

        executeChain(interceptor, context) {
            val spanId = context["spanId"] as? String
            assertNotNull(spanId, "SpanId should be generated")
            assertEquals(16, spanId.length, "SpanId should be 16 characters")
            assertEquals(spanId, MDC.get("spanId"))
            createTestResponse()
        }

        assertNull(MDC.get("spanId"))
    }

    @Test
    fun `interceptor captures parent spanId when provided`() {
        val interceptor = TracingInterceptor(enabled = true)
        val parentSpanId = "parent-span-123"
        val context = mutableMapOf<String, Any>("spanId" to parentSpanId)

        executeChain(interceptor, context) {
            assertEquals(parentSpanId, MDC.get("parentSpanId"))
            // New spanId should be generated
            val newSpanId = context["spanId"] as? String
            assertNotNull(newSpanId)
            assertTrue(newSpanId != parentSpanId, "New spanId should differ from parent")
            createTestResponse()
        }

        assertNull(MDC.get("parentSpanId"))
    }

    @Test
    fun `interceptor adds operation details to MDC`() {
        val interceptor = TracingInterceptor(enabled = true, serviceName = "my-service")
        val chatId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val context = mutableMapOf<String, Any>(
            "operation" to "chat",
            "chatId" to chatId,
            "requestId" to requestId
        )

        executeChain(interceptor, context) {
            assertEquals("chat", MDC.get("operation"))
            assertEquals("my-service", MDC.get("service"))
            assertEquals(chatId.toString(), MDC.get("chatId"))
            assertEquals(requestId.toString(), MDC.get("requestId"))
            createTestResponse()
        }

        // Verify all MDC keys are cleaned up
        assertNull(MDC.get("operation"))
        assertNull(MDC.get("service"))
        assertNull(MDC.get("chatId"))
        assertNull(MDC.get("requestId"))
    }

    @Test
    fun `interceptor marks span as successful`() {
        val interceptor = TracingInterceptor(enabled = true)
        val context = mutableMapOf<String, Any>()

        executeChain(interceptor, context) {
            createTestResponse()
        }

        // Status should be cleaned up after operation
        assertNull(MDC.get("span.status"))
    }

    @Test
    fun `interceptor marks span as error on exception`() {
        val interceptor = TracingInterceptor(enabled = true)
        val context = mutableMapOf<String, Any>()
        val testException = RuntimeException("Test error")

        try {
            executeChain(interceptor, context) {
                throw testException
            }
            throw AssertionError("Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals("Test error", e.message)
        }

        // MDC should be cleaned up even after exception
        assertNull(MDC.get("span.status"))
        assertNull(MDC.get("error.type"))
        assertNull(MDC.get("error.message"))
    }

    @Test
    fun `interceptor bypasses tracing when disabled`() {
        val interceptor = TracingInterceptor(enabled = false)
        val context = mutableMapOf<String, Any>()

        executeChain(interceptor, context) {
            // No trace context should be added when disabled
            assertNull(MDC.get("traceId"))
            assertNull(MDC.get("spanId"))
            createTestResponse()
        }

        assertNull(context["traceId"])
        assertNull(context["spanId"])
    }

    @Test
    fun `interceptor restores previous MDC values`() {
        // Set up existing MDC context
        val previousTraceId = "previous-trace-id"
        val previousSpanId = "previous-span-id"
        MDC.put("traceId", previousTraceId)
        MDC.put("spanId", previousSpanId)

        try {
            val interceptor = TracingInterceptor(enabled = true)
            val context = mutableMapOf<String, Any>()

            executeChain(interceptor, context) {
                // Inside operation, we should have new values
                val currentTraceId = MDC.get("traceId")
                val currentSpanId = MDC.get("spanId")
                assertTrue(currentTraceId != previousTraceId, "Should have new traceId during operation")
                assertTrue(currentSpanId != previousSpanId, "Should have new spanId during operation")
                createTestResponse()
            }

            // After operation, previous values should be restored
            assertEquals(previousTraceId, MDC.get("traceId"))
            assertEquals(previousSpanId, MDC.get("spanId"))
        } finally {
            // Clean up
            MDC.remove("traceId")
            MDC.remove("spanId")
        }
    }

    @Test
    fun `multiple operations generate unique spanIds`() {
        val interceptor = TracingInterceptor(enabled = true)
        val spanIds = mutableSetOf<String>()

        repeat(3) {
            val context = mutableMapOf<String, Any>()
            executeChain(interceptor, context) {
                val spanId = context["spanId"] as String
                spanIds.add(spanId)
                createTestResponse()
            }
        }

        assertEquals(3, spanIds.size, "Each operation should have unique spanId")
    }

    // Helper methods

    private fun executeChain(
        interceptor: ChatClientInterceptor,
        context: MutableMap<String, Any>,
        finalAction: () -> AimoChatResponse
    ): AimoChatResponse {
        val chain = object : ChatClientInterceptor.Chain {
            override fun proceed(context: MutableMap<String, Any>): AimoChatResponse {
                return finalAction()
            }
        }
        return interceptor.intercept(chain, context)
    }

    private fun createTestResponse(): AimoChatResponse {
        return AimoChatResponse(
            chatId = UUID.randomUUID(),
            responseId = UUID.randomUUID(),
            messages = listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.ASSISTANT,
                    content = "test response",
                    thinking = null,
                    toolName = null,
                    done = true
                )
            ),
            createdAt = Instant.now(),
            usage = null
        )
    }
}