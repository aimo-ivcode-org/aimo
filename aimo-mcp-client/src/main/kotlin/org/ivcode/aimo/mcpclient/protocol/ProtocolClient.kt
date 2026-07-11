package org.ivcode.aimo.mcpclient.protocol

import org.ivcode.aimo.mcpclient.protocol.transport.ProtocolTransport
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Protocol client that handles JSON-RPC 2.0 message exchange via a transport.
 * Manages request/response pairing by ID and coordinates lifecycle.
 */
class ProtocolClient(
    private val transport: ProtocolTransport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<JsonRpcResponse>>()
    private var messageReaderThread: Thread? = null
    @Volatile
    private var running = false

    fun connect() {
        try {
            transport.connect()
            running = true
            // Start message reader thread
            messageReaderThread = Thread { readMessages() }.apply {
                isDaemon = true
                name = "MCP-MessageReader"
                start()
            }
            log.info("Protocol client connected")
        } catch (e: Exception) {
            log.error("Failed to connect protocol transport", e)
            throw e
        }
    }

    fun disconnect() {
        running = false
        transport.disconnect()
        try {
            messageReaderThread?.join(5000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        log.info("Protocol client disconnected")
    }

    private fun readMessages() {
        while (running) {
            try {
                val messageText = transport.receive()
                try {
                    val jsonNode = objectMapper.readTree(messageText)
                    handleMessage(jsonNode)
                } catch (e: Exception) {
                    log.warn("Failed to parse message as JSON: $messageText", e)
                    // Continue reading even if parse fails
                }
            } catch (e: java.io.IOException) {
                // Expected when no message arrives within the poll timeout; keep listening.
                // This is normal idle behavior when there are no pending requests.
                if (running) {
                    log.debug("No message received within timeout, continuing to listen...")
                }
            } catch (e: Exception) {
                if (running) {
                    log.error("Error reading messages from transport", e)
                }
                break
            }
        }
    }

    private fun handleMessage(jsonNode: JsonNode) {
        val id = jsonNode.get("id")?.let {
            // Handle both string and numeric IDs
            if (it.isTextual) it.asText() else it.toString()
        }

        if (id != null) {
            val future = pendingRequests.remove(id)
            if (future != null) {
                try {
                    val response = objectMapper.treeToValue(jsonNode, JsonRpcResponse::class.java)
                    future.complete(response)
                    log.debug("Response received for request id=$id")
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                    log.error("Failed to parse response for request id=$id", e)
                }
            } else {
                log.debug("Received message with id=$id but no matching pending request (likely a server notification)")
            }
        } else {
            // Message without ID - this is a server notification
            log.debug("Received server notification: ${jsonNode.get("method")?.asText() ?: "unknown"}")
        }
    }

    fun sendRequest(method: String, params: JsonNode? = null, timeoutMs: Long = 60000): JsonRpcResponse {
        val id = UUID.randomUUID().toString()
        val request = JsonRpcRequest(method = method, params = params, id = id)
        val future = CompletableFuture<JsonRpcResponse>()
        pendingRequests[id] = future

        return try {
            val requestJson = objectMapper.writeValueAsString(request)
            log.debug("Sending request: method=$method id=$id")
            transport.send(requestJson)
            log.debug("Waiting for response: method=$method id=$id (timeout=${timeoutMs}ms)")
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            pendingRequests.remove(id)
            log.error("Request timeout for method=$method id=$id after ${timeoutMs}ms")
            throw McpProtocolException("Request timeout for method=$method (${timeoutMs}ms)", e)
        } catch (e: IllegalStateException) {
            // Likely HTTP transport error
            pendingRequests.remove(id)
            log.error("Request failed due to transport error: method=$method id=$id - ${e.message}")
            throw McpProtocolException("Failed to send request to MCP server: ${e.message}", e)
        } catch (e: Exception) {
            pendingRequests.remove(id)
            log.error("Request failed for method=$method id=$id", e)
            throw e
        }
    }

    fun sendNotification(method: String, params: JsonNode? = null) {
        val notification = JsonRpcNotification(method = method, params = params)
        try {
            transport.send(objectMapper.writeValueAsString(notification))
        } catch (e: Exception) {
            log.error("Failed to send notification method=$method", e)
            throw e
        }
    }
}

sealed class JsonRpcMessage {
    data class Request(val request: JsonRpcRequest) : JsonRpcMessage()
    data class Notification(val notification: JsonRpcNotification) : JsonRpcMessage()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonNode? = null,
    val id: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonNode? = null,
    val error: JsonRpcError? = null,
    val id: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonNode? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonNode? = null,
)

class McpProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

