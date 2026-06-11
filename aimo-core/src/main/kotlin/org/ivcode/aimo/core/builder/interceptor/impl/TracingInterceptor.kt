package org.ivcode.aimo.core.builder.interceptor.impl

import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.slf4j.MDC
import java.util.UUID

/**
 * Distributed tracing interceptor for chat client operations.
 *
 * Manages trace context propagation using SLF4J MDC (Mapped Diagnostic Context).
 * Supports distributed tracing systems like OpenTelemetry, Jaeger, Zipkin, etc.
 *
 * Captures:
 * - Trace ID (from context or generated)
 * - Span ID (generated per operation)
 * - Parent span ID (if available)
 * - Operation name
 * - Tags: chatId, requestId, userId, model
 *
 * @property enabled Whether tracing is enabled
 * @property serviceName Service name for tracing tags
 */
class TracingInterceptor(
    private val enabled: Boolean = true,
    private val serviceName: String = "aimo-chat"
) : ChatClientInterceptor {

    override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
        if (!enabled) {
            return chain.proceed(context)
        }

        // Extract or generate trace ID
        val traceId = context["traceId"] as? String
            ?: UUID.randomUUID().toString().replace("-", "")

        // Generate span ID for this operation
        val spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)

        // Get parent span ID if available
        val parentSpanId = context["spanId"] as? String

        // Store trace context in MDC
        val previousTraceId = MDC.get("traceId")
        val previousSpanId = MDC.get("spanId")

        try {
            MDC.put("traceId", traceId)
            MDC.put("spanId", spanId)
            if (parentSpanId != null) {
                MDC.put("parentSpanId", parentSpanId)
            }

            // Add trace context to operation context for downstream propagation
            context["traceId"] = traceId
            context["spanId"] = spanId

            // Extract operation details for span tags
            val operation = context["operation"] as? String ?: "chat"
            val chatId = context["chatId"]?.toString()
            val requestId = context["requestId"]?.toString()

            MDC.put("operation", operation)
            MDC.put("service", serviceName)
            if (chatId != null) MDC.put("chatId", chatId)
            if (requestId != null) MDC.put("requestId", requestId)

            // Execute operation with trace context
            val result = chain.proceed(context)

            // Mark span as successful
            MDC.put("span.status", "ok")

            return result
        } catch (e: Exception) {
            // Mark span as failed
            MDC.put("span.status", "error")
            MDC.put("error.type", e.javaClass.simpleName)
            MDC.put("error.message", e.message ?: "")
            throw e
        } finally {
            // Restore previous MDC values
            if (previousTraceId != null) {
                MDC.put("traceId", previousTraceId)
            } else {
                MDC.remove("traceId")
            }

            if (previousSpanId != null) {
                MDC.put("spanId", previousSpanId)
            } else {
                MDC.remove("spanId")
            }

            // Clean up operation-specific MDC entries
            MDC.remove("parentSpanId")
            MDC.remove("operation")
            MDC.remove("service")
            MDC.remove("chatId")
            MDC.remove("requestId")
            MDC.remove("span.status")
            MDC.remove("error.type")
            MDC.remove("error.message")
        }
    }
}

