package org.ivcode.aimo.core.conversation

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Auditing interceptor for conversation factory operations.
 *
 * Logs all conversation access and deletion for compliance, debugging, and security auditing.
 *
 * Captures chat ID, timestamp, and metadata.
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

    override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
        if (!enabled) {
            return chain.proceed(chatId, metadata)
        }

        val timestamp = Instant.now()
        val auditEntry = buildAuditEntry("GET", chatId, timestamp, metadata)

        log("BEFORE $auditEntry")

        return try {
            val result = chain.proceed(chatId, metadata)

            // Log successful completion
            log("SUCCESS $auditEntry | conversationFound=${result != null}")

            result
        } catch (e: Exception) {
            // Log failure with exception details
            log("FAILURE $auditEntry | error=${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
        if (!enabled) {
            return chain.proceed(chatId, metadata)
        }

        val timestamp = Instant.now()
        val auditEntry = buildAuditEntry("DELETE", chatId, timestamp, metadata)

        log("BEFORE $auditEntry")

        return try {
            val result = chain.proceed(chatId, metadata)

            // Log successful completion
            log("SUCCESS $auditEntry | conversationDeleted=$result")

            result
        } catch (e: Exception) {
            // Log failure with exception details
            log("FAILURE $auditEntry | error=${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private fun buildAuditEntry(
        operation: String,
        chatId: UUID,
        timestamp: Instant,
        metadata: Map<String, Any>
    ): String {
        val sb = StringBuilder()
        sb.append("operation=$operation")
        sb.append(" | timestamp=$timestamp")
        sb.append(" | chatId=$chatId")
        sb.append(" | metadataKeys=${metadata.keys.size}")

        return sb.toString()
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