package org.ivcode.aimo.core.builder.interceptor.impl

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.builder.interceptor.ConversationInterceptor
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Auditing interceptor for conversation operations.
 *
 * Logs all conversation DAO operations for compliance, debugging, and security auditing.
 *
 * Captures:
 * - Operation type (getMessages, addMessages, getChatProperty, etc.)
 * - User ID performing the operation
 * - Chat ID being accessed
 * - Timestamp
 * - Operation parameters (message count, property names, etc.)
 * - Success/failure status
 * - Exception details on failure
 *
 * @property auditLogger Optional custom logger for audit events (uses default if null)
 * @property enabled Whether auditing is enabled
 * @property logLevel The logging level for audit events (default: INFO)
 */
class AuditingConversationInterceptor(
    private val auditLogger: org.slf4j.Logger? = null,
    private val enabled: Boolean = true,
    private val logLevel: AuditLogLevel = AuditLogLevel.INFO
) : ConversationInterceptor {

    private val logger = auditLogger ?: LoggerFactory.getLogger("AUDIT.Conversation")

    override fun intercept(chain: ConversationInterceptor.Chain, context: MutableMap<String, Any>): Any? {
        if (!enabled) {
            return chain.proceed(context)
        }

        val operation = context["operation"] as? String ?: "unknown"
        val chatId = context["chatId"] as? UUID
        val userId = context["userId"] as? String
        val timestamp = Instant.now()

        // Build audit log entry
        val auditEntry = buildAuditEntry(operation, chatId, userId, timestamp, context)

        log("BEFORE $auditEntry")

        return try {
            val result = chain.proceed(context)

            // Log successful completion with result summary
            val resultSummary = summarizeResult(operation, result)
            log("SUCCESS $auditEntry | result=$resultSummary")

            result
        } catch (e: Exception) {
            // Log failure with exception details
            log("FAILURE $auditEntry | error=${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private fun buildAuditEntry(
        operation: String,
        chatId: UUID?,
        userId: String?,
        timestamp: Instant,
        context: Map<String, Any>
    ): String {
        val sb = StringBuilder()
        sb.append("operation=$operation")
        sb.append(" | timestamp=$timestamp")
        if (userId != null) sb.append(" | userId=$userId")
        if (chatId != null) sb.append(" | chatId=$chatId")

        // Add operation-specific details
        when (operation) {
            "addMessages" -> {
                @Suppress("UNCHECKED_CAST")
                val messages = context["messages"] as? List<AimoChatMessage>
                sb.append(" | messageCount=${messages?.size ?: 0}")
                sb.append(" | requestId=${context["requestId"]}")
            }
            "getChatProperty" -> {
                sb.append(" | property=${context["property"]}")
            }
            "writeChatProperty" -> {
                sb.append(" | property=${context["property"]}")
                // Don't log the actual value for security reasons
            }
            "deleteChatProperty" -> {
                sb.append(" | property=${context["property"]}")
            }
        }

        return sb.toString()
    }

    private fun summarizeResult(operation: String, result: Any?): String {
        return when (operation) {
            "getMessages" -> {
                @Suppress("UNCHECKED_CAST")
                val messages = result as? List<AimoChatMessage>
                "messageCount=${messages?.size ?: 0}"
            }
            "getChatMetadata" -> {
                @Suppress("UNCHECKED_CAST")
                val metadata = result as? Map<*, *>
                "metadataKeys=${metadata?.keys?.size ?: 0}"
            }
            "getChatProperty" -> {
                if (result != null) "valuePresent=true" else "valuePresent=false"
            }
            "deleteChatProperty" -> {
                "deleted=$result"
            }
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

