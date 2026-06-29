package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.AimoChatMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Auditing interceptor for conversation operations.
 *
 * Logs all conversation DAO operations for compliance, debugging, and security auditing.
 *
 * Captures chat ID, timestamp, and operation parameters from metadata.
 *
 * @property auditLogger Optional custom logger for audit events (uses default if null)
 * @property enabled Whether auditing is enabled
 * @property logLevel The logging level for audit events (default: INFO)
 */
class AuditingConversationInterceptor(
    private val auditLogger: Logger? = null,
    private val enabled: Boolean = true,
    private val logLevel: AuditLogLevel = AuditLogLevel.INFO
) : ConversationInterceptor {

    private val logger = auditLogger ?: LoggerFactory.getLogger("AUDIT.Conversation")

    override fun intercept(chain: ConversationInterceptor.Chain, chatId: UUID, metadata: MutableMap<String, Any>): Any? {
        if (!enabled) {
            return chain.proceed(chatId, metadata)
        }

        val timestamp = Instant.now()
        val auditEntry = buildAuditEntry(chatId, timestamp, metadata)

        log("BEFORE $auditEntry")

        return try {
            val result = chain.proceed(chatId, metadata)

            // Log successful completion with result summary
            val resultSummary = summarizeResult(result)
            log("SUCCESS $auditEntry | result=$resultSummary")

            result
        } catch (e: Exception) {
            // Log failure with exception details
            log("FAILURE $auditEntry | error=${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private fun buildAuditEntry(
        chatId: UUID,
        timestamp: Instant,
        metadata: Map<String, Any>
    ): String {
        val sb = StringBuilder()
        sb.append("timestamp=$timestamp")
        sb.append(" | chatId=$chatId")

        // Add metadata-specific details
        @Suppress("UNCHECKED_CAST")
        val messages = metadata["messages"] as? List<AimoChatMessage>
        if (messages != null) {
            sb.append(" | messageCount=${messages.size}")
            sb.append(" | requestId=${metadata["requestId"]}")
        }
        (metadata["property"] as? String)?.let { sb.append(" | property=$it") }

        return sb.toString()
    }

    private fun summarizeResult(result: Any?): String {
        return when (result) {
            null -> "ok"
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                val messages = result as? List<AimoChatMessage>
                "messageCount=${messages?.size ?: 0}"
            }
            is Map<*, *> -> {
                "metadataKeys=${result.keys.size}"
            }
            is Boolean -> "deleted=$result"
            else -> "ok"
        }
    }

    private fun log(message: String) {
        when (logLevel) {
            AuditLogLevel.DEBUG -> logger.debug(message)
            AuditLogLevel.INFO -> logger.info(message)
            AuditLogLevel.WARN -> logger.warn(message)
            AuditLogLevel.ERROR -> logger.error(message)
        }
    }

    enum class AuditLogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}