package org.ivcode.aimo.mcpclient.protocol.transport

import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * HTTP-based MCP transport implementing the "Streamable HTTP" transport from the
 * MCP spec (2025-11-25). Unlike a persistent connection, every JSON-RPC message is
 * sent as its own POST request. The response to that POST is either:
 *   - `Content-Type: application/json` — a single JSON-RPC response body, or
 *   - `Content-Type: text/event-stream` — an SSE stream scoped to *this* request,
 *     which the server closes after sending the matching response(s).
 *
 * A session may be established during `initialize`; if the server returns an
 * `Mcp-Session-Id` header, it is captured and sent on all subsequent requests.
 *
 * Per spec, the `MCP-Protocol-Version` header is sent on all HTTP requests to indicate
 * the protocol version being used.
 */
class HttpTransport(
    private val url: String,
    private val authToken: String? = null,
    private val protocolVersion: String = "2025-11-25",
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val connectTimeoutSeconds: Long = 10,
    private val messageTimeoutSeconds: Long = 60,
    private val requestTimeoutSeconds: Long = 60,  // Timeout for the full HTTP request/response
) : ProtocolTransport {
    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(connectTimeoutSeconds))
        .build()

    @Volatile
    private var connected = false

    @Volatile
    private var sessionId: String? = null

    private val messageQueue = LinkedBlockingQueue<String>()

    override fun connect() {
        // Streamable HTTP has no persistent connection to establish up front;
        // each request/response is exchanged over its own POST. Just mark ready.
        connected = true
        log.info("HTTP transport ready: url=$url")
    }

    override fun disconnect() {
        connected = false
        val sid = sessionId
        if (!sid.isNullOrBlank()) {
            try {
                val requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(connectTimeoutSeconds))
                    .header("MCP-Protocol-Version", protocolVersion)
                    .header("Mcp-Session-Id", sid)
                    .DELETE()

                if (!authToken.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $authToken")
                }

                httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
            } catch (e: Exception) {
                log.debug("Failed to terminate MCP session cleanly (non-fatal)", e)
            }
        }
        log.info("HTTP transport disconnected")
    }

    override fun send(message: String) {
        if (!connected) {
            throw IllegalStateException("HTTP transport not connected")
        }
        try {
            log.debug("Sending message via HTTP POST to $url")
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(requestTimeoutSeconds))  // Use request timeout instead of connect timeout
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", protocolVersion)
                .POST(HttpRequest.BodyPublishers.ofString(message))

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $authToken")
            }
            sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }

            val request = requestBuilder.build()
            log.debug("Executing HTTP POST request to $url")
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

            // Capture session id if the server assigned one (typically on initialize response)
            response.headers().firstValue("Mcp-Session-Id").ifPresent {
                if (sessionId == null) {
                    log.debug("Captured MCP session id")
                }
                sessionId = it
            }

            if (response.statusCode() == 202) {
                // Accepted with no body - this was a notification/response with nothing to return
                response.body().close()
                log.debug("Message accepted (202) with no response body")
                return
            }

            if (response.statusCode() >= 400) {
                val body = response.body().bufferedReader().use { it.readText() }
log.error("HTTP POST failed with status ${response.statusCode()}: ${body.take(2000)}")
log.debug("HTTP POST request payload (truncated): ${message.take(2000)}")

                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw IllegalStateException("HTTP authentication failed (status ${response.statusCode()}). " +
                        "Verify that auth token is valid and has required permissions. Response: $body")
                } else if (response.statusCode() == 400) {
                    throw IllegalStateException("HTTP request rejected with Bad Request (400). " +
                        "Response: $body")
                } else {
                    throw IllegalStateException("HTTP POST failed: status ${response.statusCode()}, response: $body")
                }
            }

            val contentType = (response.headers().firstValue("Content-Type").orElse("") ?: "").lowercase()
            log.debug("Response Content-Type: $contentType, Status: ${response.statusCode()}")
            when {
                "text/event-stream" in contentType -> {
                    log.debug("Response is an SSE stream; reading until stream closes")
                    readSseResponseStream(response.body())
                    log.debug("SSE stream reading completed")
                }
                "application/json" in contentType -> {
                    val body = response.body().bufferedReader().use { it.readText() }
                    if (body.isNotBlank()) {
                        log.debug("Received JSON response: $body")
                        messageQueue.offer(body)
                    }
                }
                else -> {
                    // Unknown content type; try to read as text and queue if non-empty
                    val body = response.body().bufferedReader().use { it.readText() }
                    if (body.isNotBlank()) {
                        log.warn("Unexpected Content-Type '$contentType'; attempting to parse body as message")
                        messageQueue.offer(body)
                    }
                }
            }
            log.debug("Message sent via HTTP successfully")
        } catch (e: Exception) {
            log.error("Failed to send message via HTTP", e)
            throw e
        }
    }

    /**
     * Reads a single request-scoped SSE stream (as returned in the POST response) to
     * completion, queueing any JSON-RPC messages found in `data:` lines. The server
     * closes this stream once it has sent the response(s) for the originating request.
     */
    private fun readSseResponseStream(inputStream: java.io.InputStream) {
        try {
            inputStream.bufferedReader().use { reader ->
                var line: String? = null
                var lineCount = 0
                var messageCount = 0
                try {
                    while (reader.readLine().also { line = it } != null) {
                        lineCount++
                        val current = line ?: continue
                        if (current.isEmpty()) continue // SSE event delimiter

                        val data = when {
                            current.startsWith("data: ") -> current.substring(6)
                            current.startsWith("data:") -> current.substring(5)
                            current.startsWith("event:") || current.startsWith("retry:") ||
                                current.startsWith("id:") || current.startsWith(":") -> null
                            else -> current // raw JSON (not spec-compliant, but tolerate it)
                        }

                        if (!data.isNullOrEmpty()) {
                            log.debug("Received SSE data (line $lineCount): ${data.take(80)}${if (data.length > 80) "..." else ""}")
                            messageQueue.offer(data)
                            messageCount++
                        }
                    }
                    log.debug("SSE stream closed after reading $lineCount lines ($messageCount messages)")
                } catch (e: Exception) {
                    log.error("Error reading SSE stream after $lineCount lines, $messageCount messages processed", e)
                    // Don't rethrow - we want to keep the connection alive and try the next request
                }
            }
        } catch (e: Exception) {
            log.error("Error closing SSE stream reader", e)
            // Don't rethrow - handle gracefully
        }
    }

    override fun receive(): String {
        if (!connected) {
            throw IllegalStateException("HTTP transport not connected")
        }

        try {
            val message = messageQueue.poll(messageTimeoutSeconds, TimeUnit.SECONDS)
            if (message != null) {
                log.debug("Received message from queue: ${message.take(100)}${if (message.length > 100) "..." else ""}")
                return message
            }
            // Normal case: no message arrived within timeout (expected between requests)
            throw java.io.IOException("Timeout waiting for message (${messageTimeoutSeconds}s) - this is normal when no requests are pending")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.IOException("Interrupted while waiting for message", e)
        }
    }
}
