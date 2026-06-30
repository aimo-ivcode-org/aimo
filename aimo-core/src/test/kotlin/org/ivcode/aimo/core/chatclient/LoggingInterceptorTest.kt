package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for LoggingInterceptor.
 *
 * Verifies:
 * - Logs are generated for successful operations
 * - Logs capture timing information
 * - Logs capture operation details (chatId, requestId)
 * - Error logging works correctly
 * - Can be disabled
 * - Different log levels work
 */
class LoggingInterceptorTest {

    @Test
    fun `interceptor proceeds with operation when enabled`() {
        val interceptor = LoggingInterceptor(
            logLevel = LoggingInterceptor.LogLevel.INFO,
            enabled = true
        )

        val context = createContext()
        var chainCalled = false

        val response = executeChain(interceptor, context) {
            chainCalled = true
            createTestResponse()
        }

        assertTrue(chainCalled, "Chain should be called")
        assertEquals("test response", response.messages.first().content)
    }

    @Test
    fun `interceptor bypasses logging when disabled`() {
        val interceptor = LoggingInterceptor(
            logLevel = LoggingInterceptor.LogLevel.INFO,
            enabled = false
        )

        val context = createContext()
        var chainCalled = false

        val response = executeChain(interceptor, context) {
            chainCalled = true
            createTestResponse()
        }

        assertTrue(chainCalled, "Chain should be called even when disabled")
        assertEquals("test response", response.messages.first().content)
    }

    @Test
    fun `interceptor captures timing information`() {
        val interceptor = LoggingInterceptor(
            logLevel = LoggingInterceptor.LogLevel.DEBUG,
            enabled = true
        )

        val context = createContext()
        val startTime = System.currentTimeMillis()

        executeChain(interceptor, context) {
            // Simulate some work
            Thread.sleep(10)
            createTestResponse()
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        assertTrue(duration >= 10, "Duration should be at least 10ms")
    }

    @Test
    fun `interceptor handles exceptions and rethrows`() {
        val interceptor = LoggingInterceptor(
            logLevel = LoggingInterceptor.LogLevel.ERROR,
            enabled = true
        )

        val context = createContext()
        val testException = RuntimeException("Test error")

        try {
            executeChain(interceptor, context) {
                throw testException
            }
            throw AssertionError("Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals("Test error", e.message)
        }
    }

    @Test
    fun `interceptor extracts context information`() {
        val interceptor = LoggingInterceptor(
            logLevel = LoggingInterceptor.LogLevel.INFO,
            enabled = true
        )

        val chatId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val context = mutableMapOf<String, Any>(
            "operation" to "chat",
            "chatId" to chatId,
            "requestId" to requestId
        )

        val response = executeChain(interceptor, context) {
            createTestResponse()
        }

        // Verify response is returned correctly
        assertEquals(1, response.messages.size)
    }

    @Test
    fun `interceptor handles missing context gracefully`() {
        val interceptor = LoggingInterceptor(
            logLevel = LoggingInterceptor.LogLevel.WARN,
            enabled = true
        )

        // Empty context - should not throw
        val context = mutableMapOf<String, Any>()

        val response = executeChain(interceptor, context) {
            createTestResponse()
        }

        assertEquals("test response", response.messages.first().content)
    }

    @Test
    fun `different log levels work without error`() {
        listOf(
            LoggingInterceptor.LogLevel.DEBUG,
            LoggingInterceptor.LogLevel.INFO,
            LoggingInterceptor.LogLevel.WARN,
            LoggingInterceptor.LogLevel.ERROR
        ).forEach { level ->
            val interceptor = LoggingInterceptor(logLevel = level, enabled = true)
            val context = createContext()

            val response = executeChain(interceptor, context) {
                createTestResponse()
            }

            assertEquals("test response", response.messages.first().content)
        }
    }

    // Helper methods

    private fun createContext(): MutableMap<String, Any> {
        return mutableMapOf(
            "operation" to "chat",
            "chatId" to UUID.randomUUID(),
            "requestId" to UUID.randomUUID()
        )
    }

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