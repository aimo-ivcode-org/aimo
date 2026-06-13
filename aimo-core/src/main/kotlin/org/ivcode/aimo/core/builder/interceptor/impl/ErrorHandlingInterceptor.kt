package org.ivcode.aimo.core.builder.interceptor.impl

import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.slf4j.LoggerFactory

/**
 * Error handling and retry interceptor for chat client operations.
 *
 * Provides:
 * - Standardized exception mapping (provider-specific → ChatException hierarchy)
 * - Automatic retry with exponential backoff
 * - Circuit breaker pattern (TODO: future enhancement)
 * - Fallback responses (TODO: future enhancement)
 *
 * @property maxRetries Maximum number of retry attempts (default: 3)
 * @property retryBackoffMs Initial backoff delay in milliseconds (default: 100)
 * @property retryableExceptions Exception types that should trigger retry
 * @property enabled Whether error handling is enabled
 */
class ErrorHandlingInterceptor(
    private val maxRetries: Int = 3,
    private val retryBackoffMs: Long = 100,
    private val retryableExceptions: Set<Class<out Exception>> = DEFAULT_RETRYABLE_EXCEPTIONS,
    private val enabled: Boolean = true
) : ChatClientInterceptor {

    private val logger = LoggerFactory.getLogger(ErrorHandlingInterceptor::class.java)

    override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
        if (!enabled) {
            return chain.proceed(context)
        }

        var lastException: Exception? = null
        var attempt = 0

        while (attempt <= maxRetries) {
            try {
                return chain.proceed(context)
            } catch (e: Exception) {
                lastException = e
                attempt++

                // Check if exception is retryable
                val isRetryable = retryableExceptions.any { it.isAssignableFrom(e.javaClass) }

                if (!isRetryable || attempt > maxRetries) {
                    logger.error("Non-retryable exception or max retries exceeded (attempt $attempt/$maxRetries)", e)
                    throw mapException(e, context)
                }

                // Calculate backoff delay with exponential increase (clamped to avoid overflow for large maxRetries)
                val backoffDelay = retryBackoffMs * (1L shl (attempt - 1).coerceAtMost(62))
                
                logger.warn(
                    "Retryable exception on attempt $attempt/$maxRetries, " +
                    "retrying after ${backoffDelay}ms: ${e.message}",
                    e
                )

                try {
                    Thread.sleep(backoffDelay)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw mapException(e, context)
                }
            }
        }

        // Should never reach here, but just in case
        throw mapException(
            lastException ?: IllegalStateException("Unknown error in ErrorHandlingInterceptor"),
            context
        )
    }

    /**
     * Maps provider-specific exceptions to standardized ChatException hierarchy.
     * This allows consistent error handling across different model providers.
     */
    private fun mapException(e: Exception, context: MutableMap<String, Any>): Exception {
        // For now, wrap in a ChatException-like wrapper
        // TODO: Create proper ChatException hierarchy in Phase 1
        return ChatOperationException(
            message = "Chat operation failed: ${e.message}",
            cause = e,
            chatId = context["chatId"]?.toString(),
            requestId = context["requestId"]?.toString(),
            operation = context["operation"] as? String
        )
    }

    companion object {
        private val DEFAULT_RETRYABLE_EXCEPTIONS = setOf(
            java.net.SocketTimeoutException::class.java,
            java.net.ConnectException::class.java,
            java.io.IOException::class.java,
            // Add more retryable exceptions as needed
        )
    }
}

/**
 * Exception thrown when a chat operation fails.
 * Includes context information for debugging and error tracking.
 */
class ChatOperationException(
    message: String,
    cause: Throwable? = null,
    val chatId: String? = null,
    val requestId: String? = null,
    val operation: String? = null
) : RuntimeException(message, cause)

