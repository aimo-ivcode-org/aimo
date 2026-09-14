package org.ivcode.aimo.mcpclient.protocol.transport

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val ACCEPTED_NO_BODY_STATUS = 202
private const val BAD_REQUEST_STATUS = 400
private const val UNAUTHORIZED_STATUS = 401
private const val FORBIDDEN_STATUS = 403
private const val HTTP_ERROR_STATUS_THRESHOLD = 400
private const val SSE_DATA_PREFIX_WITH_SPACE = "data: "
private const val SSE_DATA_PREFIX = "data:"
private const val SSE_PREVIEW_LENGTH = 80
private const val MESSAGE_PREVIEW_LENGTH = 100
private const val ERROR_BODY_PREVIEW_LENGTH = 2_000

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
            runCatching {
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
            }.onFailure { log.debug("Failed to terminate MCP session cleanly (non-fatal)", it) }
        }
        log.info("HTTP transport disconnected")
    }

    override fun send(message: String) {
        check(connected) { "HTTP transport not connected" }

        runCatching {
            log.debug("Sending message via HTTP POST to $url")
            val request = buildRequest(message)
            log.debug("Executing HTTP POST request to $url")
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

            captureSessionId(response)
            handleResponse(response, message)
            log.debug("Message sent via HTTP successfully")
        }.onFailure {
            log.error("Failed to send message via HTTP", it)
        }.getOrThrow()
    }

    private fun buildRequest(message: String): HttpRequest {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            // Use request timeout instead of connect timeout.
            .timeout(java.time.Duration.ofSeconds(requestTimeoutSeconds))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", protocolVersion)
            .POST(HttpRequest.BodyPublishers.ofString(message))

        if (!authToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $authToken")
        }
        sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }

        return requestBuilder.build()
    }

    private fun captureSessionId(response: HttpResponse<java.io.InputStream>) {
        response.headers().firstValue("Mcp-Session-Id").ifPresent {
            if (sessionId == null) {
                log.debug("Captured MCP session id")
            }
            sessionId = it
        }
    }

    private fun handleResponse(response: HttpResponse<java.io.InputStream>, message: String) {
        val statusCode = response.statusCode()
        when {
            statusCode == ACCEPTED_NO_BODY_STATUS -> {
                response.body().close()
                log.debug("Message accepted ($ACCEPTED_NO_BODY_STATUS) with no response body")
            }

            statusCode >= HTTP_ERROR_STATUS_THRESHOLD -> handleErrorResponse(response, message)

            else -> handleSuccessResponse(response)
        }
    }

    private fun handleErrorResponse(response: HttpResponse<java.io.InputStream>, message: String) {
        val statusCode = response.statusCode()
        val body = response.body().bufferedReader().use { it.readText() }
        log.error("HTTP POST failed with status $statusCode: ${body.take(ERROR_BODY_PREVIEW_LENGTH)}")
        log.debug("HTTP POST request payload (truncated): ${message.take(ERROR_BODY_PREVIEW_LENGTH)}")

        throw when (statusCode) {
            UNAUTHORIZED_STATUS, FORBIDDEN_STATUS -> IllegalStateException(
                "HTTP authentication failed (status $statusCode). " +
                    "Verify that auth token is valid and has required permissions. Response: $body"
            )

            BAD_REQUEST_STATUS -> IllegalStateException(
                "HTTP request rejected with Bad Request ($BAD_REQUEST_STATUS). Response: $body"
            )

            else -> IllegalStateException("HTTP POST failed: status $statusCode, response: $body")
        }
    }

    private fun handleSuccessResponse(response: HttpResponse<java.io.InputStream>) {
        val contentType = response.headers().firstValue("Content-Type").orElse(null).orEmpty().lowercase()
        val statusCode = response.statusCode()
        log.debug("Response Content-Type: $contentType, Status: $statusCode")

        when {
            "text/event-stream" in contentType -> {
                log.debug("Response is an SSE stream; reading until stream closes")
                readSseResponseStream(response.body())
                log.debug("SSE stream reading completed")
            }

            "application/json" in contentType -> {
                val body = response.body().bufferedReader().use { it.readText() }
                if (body.isNotBlank()) {
                    log.debug("Received JSON response: ${previewText(body, ERROR_BODY_PREVIEW_LENGTH)}")
                    messageQueue.offer(body)
                }
            }

            else -> {
                // Unknown content type; try to read as text and queue if non-empty.
                val body = response.body().bufferedReader().use { it.readText() }
                if (body.isNotBlank()) {
                    log.warn("Unexpected Content-Type '$contentType'; attempting to parse body as message")
                    messageQueue.offer(body)
                }
            }
        }
    }

    /**
     * Reads a single request-scoped SSE stream (as returned in the POST response) to
     * completion, queueing any JSON-RPC messages found in `data:` lines. The server
     * closes this stream once it has sent the response(s) for the originating request.
     */
    private fun readSseResponseStream(inputStream: java.io.InputStream) {
        var lineCount = 0
        var messageCount = 0
        runCatching {
            inputStream.bufferedReader().use { reader ->
                while (true) {
                    val current = reader.readLine() ?: break
                    lineCount++

                    extractSseData(current)?.let { data ->
                        log.debug(
                            "Received SSE data (line $lineCount): ${previewText(data, SSE_PREVIEW_LENGTH)}"
                        )
                        messageQueue.offer(data)
                        messageCount++
                    }
                }
            }
        }.onFailure {
            log.error("Error reading SSE stream after $lineCount lines, $messageCount messages processed", it)
            // Don't rethrow - we want to keep the connection alive and try the next request.
        }
        log.debug("SSE stream closed after reading $lineCount lines ($messageCount messages)")
    }

    override fun receive(): String {
        check(connected) { "HTTP transport not connected" }

        return try {
            val message = messageQueue.poll(messageTimeoutSeconds, TimeUnit.SECONDS)
            if (message != null) {
                log.debug("Received message from queue: ${previewText(message, MESSAGE_PREVIEW_LENGTH)}")
                message
            } else {
                // Normal case: no message arrived within timeout (expected between requests)
                throw java.io.IOException(
                    "Timeout waiting for message (${messageTimeoutSeconds}s) - " +
                        "this is normal when no requests are pending"
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.IOException("Interrupted while waiting for message", e)
        }
    }
}

private fun extractSseData(line: String): String? {
    return when {
        line.startsWith(SSE_DATA_PREFIX_WITH_SPACE) -> line.removePrefix(SSE_DATA_PREFIX_WITH_SPACE)
        line.startsWith(SSE_DATA_PREFIX) -> line.removePrefix(SSE_DATA_PREFIX).trimStart()
        line.startsWith("event:") || line.startsWith("retry:") ||
            line.startsWith("id:") || line.startsWith(":") -> null

        else -> line // raw JSON (not spec-compliant, but tolerate it)
    }
}

private fun previewText(text: String, limit: Int): String {
    return text.take(limit) + if (text.length > limit) "..." else ""
}

