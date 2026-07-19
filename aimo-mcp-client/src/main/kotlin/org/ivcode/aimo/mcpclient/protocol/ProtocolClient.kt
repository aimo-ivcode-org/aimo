package org.ivcode.aimo.mcpclient.protocol

import org.ivcode.aimo.mcpclient.protocol.transport.ProtocolTransport
import org.ivcode.aimo.mcpclient.protocol.jsonrpc.JsonRpcRequest
import org.ivcode.aimo.mcpclient.protocol.jsonrpc.JsonRpcResponse
import org.ivcode.aimo.mcpclient.protocol.jsonrpc.JsonRpcNotification
import org.slf4j.LoggerFactory
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

    private val notificationHandlers = ConcurrentHashMap<String, (JsonNode?) -> Unit>()

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
        closeReader()
        messageReaderThread?.interrupt()
        try {
            messageReaderThread?.join(5000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        log.info("Protocol client disconnected")
    }

    fun isConnected(): Boolean = running

    fun onNotification(method: String, handler: (JsonNode?) -> Unit) {
        notificationHandlers[method] = handler
        log.debug("Registered notification handler for method: $method")
    }

    fun removeNotificationHandler(method: String) {
        notificationHandlers.remove(method)
        log.debug("Removed notification handler for method: $method")
    }

    private fun readMessages() {
        try {
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
                    // HttpTransport uses IOException for idle receive timeouts; stdio I/O errors should stop the reader.
                    val isHttpIdleTimeout =
                        transport is org.ivcode.aimo.mcpclient.protocol.transport.HttpTransport &&
                            e.message?.startsWith("Timeout waiting for message") == true

                    if (running && isHttpIdleTimeout) {
                        log.trace("No message received within timeout, continuing to listen...")
                    } else {
                        if (running) {
                            log.error("Transport I/O error while reading messages; stopping reader thread", e)
                        }
                        break
                    }
                } catch (e: Exception) {
                    if (running) {
                        log.error("Error reading messages from transport", e)
                    }
                    break
                }
            }
        } finally {
            // When reader exits (via break or running=false), mark disconnected and fail pending requests
            closeReader()
        }
    }

    private fun closeReader() {
        running = false
        try {
            transport.disconnect()
        } catch (e: Exception) {
            log.debug("Error disconnecting transport after reader shutdown", e)
        }

        val disconnectError = McpProtocolException("Reader thread stopped; connection lost")
        pendingRequests.forEach { (id, future) ->
            if (!future.isDone) {
                future.completeExceptionally(disconnectError)
                log.debug("Completed pending request $id with disconnection error")
            }
        }
        pendingRequests.clear()
    }

    private fun handleMessage(jsonNode: JsonNode) {
        val idNode = jsonNode.get("id")
        val id = when {
            idNode == null || idNode.isNull -> null
            idNode.isTextual -> idNode.asText()
            else -> idNode.toString()
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
            val method = jsonNode.get("method")?.asText() ?: "unknown"
            val params = jsonNode.get("params")
            log.debug("Received server notification: $method")

            val handler = notificationHandlers[method]
            if (handler != null) {
                try {
                    handler(params)
                    log.debug("Notification handler for $method completed successfully")
                } catch (e: Exception) {
                    log.error("Error handling notification for method=$method", e)
                }
            } else {
                log.debug("No handler registered for notification method: $method")
            }
        }
    }

fun sendRequest(method: String, params: JsonNode? = null, timeoutMs: Long = 60000): JsonRpcResponse {
        if (!running) {
            throw McpProtocolException("Cannot send request '$method' because the protocol client is disconnected")
        }

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


class McpProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

