package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoChatResponse
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for ErrorHandlingInterceptor.
 *
 * Verifies:
 * - Retries retryable exceptions
 * - Exponential backoff works correctly
 * - Non-retryable exceptions fail immediately
 * - Max retries limit is respected
 * - Exception mapping works
 * - Can be disabled
 * - Custom retryable exceptions work
 */
class ErrorHandlingInterceptorTest {

    @Test
    fun `interceptor succeeds on first attempt`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 3,
            retryBackoffMs = 10,
            enabled = true
        )

        val context = mutableMapOf<String, Any>()
        var attemptCount = 0

        val response = executeChain(interceptor, context) {
            attemptCount++
            createTestResponse()
        }

        assertEquals(1, attemptCount, "Should succeed on first attempt")
        assertEquals("test response", response.messages.first().content)
    }

    @Test
    fun `interceptor retries retryable exception`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 3,
            retryBackoffMs = 10,
            enabled = true
        )

        val context = mutableMapOf<String, Any>()
        var attemptCount = 0

        val response = executeChain(interceptor, context) {
            attemptCount++
            if (attemptCount < 3) {
                throw SocketTimeoutException("Timeout")
            }
            createTestResponse()
        }

        assertEquals(3, attemptCount, "Should retry twice before succeeding on third attempt")
        assertEquals("test response", response.messages.first().content)
    }

    @Test
    fun `interceptor respects max retries limit`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 2,
            retryBackoffMs = 10,
            enabled = true
        )

        val context = mutableMapOf<String, Any>(
            "chatId" to UUID.randomUUID().toString(),
            "requestId" to UUID.randomUUID().toString()
        )
        var attemptCount = 0

        val ex = assertFailsWith<ChatOperationException> {
            executeChain(interceptor, context) {
                attemptCount++
                throw SocketTimeoutException("Persistent timeout")
            }
        }

        assertEquals(3, attemptCount, "Should try initial attempt + 2 retries = 3 total")
        assertTrue(ex.message!!.contains("Chat operation failed"))
        assertTrue(ex.cause is SocketTimeoutException)
    }

    @Test
    fun `interceptor uses exponential backoff`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 3,
            retryBackoffMs = 50,
            enabled = true
        )

        val context = mutableMapOf<String, Any>()
        var attemptCount = 0
        val attemptTimes = mutableListOf<Long>()

        try {
            executeChain(interceptor, context) {
                attemptCount++
                attemptTimes.add(System.currentTimeMillis())
                throw IOException("Network error")
            }
        } catch (e: ChatOperationException) {
            // Expected to fail after all retries
        }

        assertEquals(4, attemptCount, "Should try 4 times (initial + 3 retries)")

        // Verify exponential backoff delays
        // Attempt 1 -> Attempt 2: ~50ms (backoff = 50 * 2^0)
        // Attempt 2 -> Attempt 3: ~100ms (backoff = 50 * 2^1)
        // Attempt 3 -> Attempt 4: ~200ms (backoff = 50 * 2^2)
        if (attemptTimes.size >= 2) {
            val delay1 = attemptTimes[1] - attemptTimes[0]
            assertTrue(delay1 >= 45, "First retry delay should be ~50ms, was ${delay1}ms")
        }
        if (attemptTimes.size >= 3) {
            val delay2 = attemptTimes[2] - attemptTimes[1]
            assertTrue(delay2 >= 90, "Second retry delay should be ~100ms, was ${delay2}ms")
        }
    }

    @Test
    fun `interceptor does not retry non-retryable exception`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 3,
            retryBackoffMs = 10,
            enabled = true
        )

        val context = mutableMapOf<String, Any>(
            "chatId" to UUID.randomUUID().toString()
        )
        var attemptCount = 0

        val ex = assertFailsWith<ChatOperationException> {
            executeChain(interceptor, context) {
                attemptCount++
                throw IllegalArgumentException("Invalid argument")
            }
        }

        assertEquals(1, attemptCount, "Should not retry non-retryable exception")
        assertTrue(ex.cause is IllegalArgumentException)
    }

    @Test
    fun `interceptor uses custom retryable exceptions`() {
        val customRetryableExceptions = setOf(
            IllegalStateException::class.java
        )

        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 2,
            retryBackoffMs = 10,
            retryableExceptions = customRetryableExceptions,
            enabled = true
        )

        val context = mutableMapOf<String, Any>()
        var attemptCount = 0

        val response = executeChain(interceptor, context) {
            attemptCount++
            if (attemptCount < 2) {
                throw IllegalStateException("Retryable custom exception")
            }
            createTestResponse()
        }

        assertEquals(2, attemptCount, "Should retry custom exception once")
        assertEquals("test response", response.messages.first().content)
    }

    @Test
    fun `interceptor bypasses error handling when disabled`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 3,
            retryBackoffMs = 10,
            enabled = false
        )

        val context = mutableMapOf<String, Any>()
        var attemptCount = 0

        val ex = assertFailsWith<SocketTimeoutException> {
            executeChain(interceptor, context) {
                attemptCount++
                throw SocketTimeoutException("Should not be wrapped")
            }
        }

        assertEquals(1, attemptCount, "Should not retry when disabled")
        assertEquals("Should not be wrapped", ex.message)
    }

    @Test
    fun `interceptor maps exception with context information`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 0,
            retryBackoffMs = 10,
            enabled = true
        )

        val chatId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val context = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "requestId" to requestId,
            "operation" to "chat"
        )

        val ex = assertFailsWith<ChatOperationException> {
            executeChain(interceptor, context) {
                throw RuntimeException("Original error")
            }
        }

        assertEquals(chatId, ex.chatId)
        assertEquals(requestId, ex.requestId)
        assertEquals("chat", ex.operation)
        assertTrue(ex.message!!.contains("Chat operation failed"))
        assertTrue(ex.cause is RuntimeException)
    }

    @Test
    fun `interceptor handles IOException as retryable`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 2,
            retryBackoffMs = 10,
            enabled = true
        )

        val context = mutableMapOf<String, Any>()
        var attemptCount = 0

        val response = executeChain(interceptor, context) {
            attemptCount++
            if (attemptCount == 1) {
                throw IOException("Network problem")
            }
            createTestResponse()
        }

        assertEquals(2, attemptCount, "IOException should be retried")
        assertEquals("test response", response.messages.first().content)
    }

    @Test
    fun `interceptor handles interrupted exception gracefully`() {
        val interceptor = ErrorHandlingInterceptor(
            maxRetries = 2,
            retryBackoffMs = 100000, // Long delay to trigger interrupt
            enabled = true
        )

        val context = mutableMapOf<String, Any>()
        var attemptCount = 0

        // Start the operation in a separate thread
        val thread = Thread {
            try {
                executeChain(interceptor, context) {
                    attemptCount++
                    throw SocketTimeoutException("Timeout")
                }
            } catch (e: ChatOperationException) {
                // Expected
            }
        }

        thread.start()
        Thread.sleep(50) // Let first attempt fail and start waiting
        thread.interrupt() // Interrupt during backoff wait
        thread.join(1000)

        assertTrue(attemptCount >= 1, "At least one attempt should have been made")
        assertTrue(thread.isInterrupted || !thread.isAlive, "Thread should be interrupted or finished")
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