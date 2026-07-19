package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatResponse
import org.slf4j.LoggerFactory

/**
 * Logging interceptor for chat client operations.
 *
 * Logs all chat operations with configurable detail level. Captures:
 * - Operation type (chat, chatStream)
 * - Chat ID and request ID
 * - User ID (if available)
 * - Model name (if available)
 * - Execution time
 * - Success/failure status
 *
 * @property logLevel The logging level to use (DEBUG, INFO, WARN, ERROR)
 * @property enabled Whether logging is enabled
 */
class LoggingInterceptor(
    private val logLevel: LogLevel = LogLevel.INFO,
    private val enabled: Boolean = true
) : ChatClientInterceptor {

    private val logger = LoggerFactory.getLogger(LoggingInterceptor::class.java)

    override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
        if (!enabled) {
            return chain.proceed(context)
        }

        val operation = context["operation"] as? String ?: "unknown"
        val chatId = context["chatId"]?.toString() ?: "unknown"
        val requestId = context["requestId"]?.toString() ?: "unknown"

        val startTime = System.currentTimeMillis()

        log("Starting $operation: chatId=$chatId, requestId=$requestId")

        return try {
            val response = chain.proceed(context)
            val duration = System.currentTimeMillis() - startTime

            log("Completed $operation: chatId=$chatId, requestId=$requestId, duration=${duration}ms, " +
                "responseId=${response.responseId}, messages=${response.messages.size}")

            response
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logError("Failed $operation: chatId=$chatId, requestId=$requestId, duration=${duration}ms", e)
            throw e
        }
    }

    private fun log(message: String) {
        when (logLevel) {
            LogLevel.DEBUG -> logger.debug(message)
            LogLevel.INFO -> logger.info(message)
            LogLevel.WARN -> logger.warn(message)
            LogLevel.ERROR -> logger.error(message)
        }
    }

    private fun logError(message: String, throwable: Throwable) {
        when (logLevel) {
            LogLevel.DEBUG -> logger.debug(message, throwable)
            LogLevel.INFO -> logger.info(message, throwable)
            LogLevel.WARN -> logger.warn(message, throwable)
            LogLevel.ERROR -> logger.error(message, throwable)
        }
    }

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}