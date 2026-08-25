package org.ivcode.aimo.mcpclient.protocol

import org.ivcode.aimo.mcpclient.protocol.jsonrpc.JsonRpcNotification
import org.ivcode.aimo.mcpclient.protocol.jsonrpc.JsonRpcRequest
import org.ivcode.aimo.mcpclient.protocol.jsonrpc.JsonRpcResponse
import org.ivcode.aimo.mcpclient.protocol.transport.HttpTransport
import org.ivcode.aimo.mcpclient.protocol.transport.ProtocolTransport
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val MESSAGE_READER_JOIN_TIMEOUT_MS = 5_000L
private const val HTTP_IDLE_TIMEOUT_PREFIX = "Timeout waiting for message"

/**
 * Exchanges JSON-RPC messages with an MCP server over a transport and tracks pending requests.
 */
class ProtocolClient(
    private val transport: ProtocolTransport,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<JsonRpcResponse>>()
    private val notificationHandlers = ConcurrentHashMap<String, (JsonNode?) -> Unit>()

    @Volatile
    private var running = false

    private var messageReaderThread: Thread? = null

    /**
     * Connects the transport and starts the reader thread.
     */
    fun connect() {
        runCatching {
            transport.connect()
            running = true
            messageReaderThread = Thread { readMessages() }.apply {
                isDaemon = true
                name = "MCP-MessageReader"
                start()
            }
            log.info("Protocol client connected")
        }.onFailure {
            log.error("Failed to connect protocol transport", it)
        }.getOrThrow()
    }

    /**
     * Disconnects the transport and stops the reader thread.
     */
    fun disconnect() {
        closeReader()
        messageReaderThread?.interrupt()
        runCatching { messageReaderThread?.join(MESSAGE_READER_JOIN_TIMEOUT_MS) }
            .onFailure {
                if (it is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        messageReaderThread = null
        log.info("Protocol client disconnected")
    }

    /**
     * Returns whether the client is currently connected.
     *
     * @return true when the reader loop is running, otherwise false.
     */
    fun isConnected(): Boolean = running

    /**
     * Registers a notification handler for the given JSON-RPC method.
     *
     * @param method the notification method to handle.
     * @param handler callback invoked with the notification params.
     */
    fun onNotification(method: String, handler: (JsonNode?) -> Unit) {
        notificationHandlers[method] = handler
        log.debug("Registered notification handler for method: $method")
    }

    /**
     * Removes a notification handler for the given method.
     *
     * @param method the notification method to remove.
     */
    fun removeNotificationHandler(method: String) {
        notificationHandlers.remove(method)
        log.debug("Removed notification handler for method: $method")
    }

    private fun readMessages() {
        fun receiveMessageOrNull(): String? {
            return try {
                transport.receive()
            } catch (e: IOException) {
                if (running && isHttpIdleTimeout(e)) {
                    log.trace("No message received within timeout, continuing to listen...")
                    null
                } else {
                    throw e
                }
            }
        }

        fun processReceivedMessage(messageText: String) {
            runCatching { objectMapper.readTree(messageText) }
                .onSuccess { handleMessage(it) }
                .onFailure { log.warn("Failed to parse message as JSON: $messageText", it) }
        }

        try {
            while (running) {
                receiveMessageOrNull()?.let { processReceivedMessage(it) }
            }
        } finally {
            closeReader()
        }
    }

    private fun isHttpIdleTimeout(exception: IOException): Boolean {
        return transport is HttpTransport &&
            exception.message?.startsWith(HTTP_IDLE_TIMEOUT_PREFIX) == true
    }

    private fun closeReader() {
        running = false
        runCatching { transport.disconnect() }
            .onFailure { log.debug("Error disconnecting transport after reader shutdown", it) }

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
        val id = textValue(jsonNode.get("id"))

        if (id != null) {
            val future = pendingRequests.remove(id)
            if (future != null) {
                runCatching { objectMapper.treeToValue(jsonNode, JsonRpcResponse::class.java) }
                    .onSuccess { response ->
                        future.complete(response)
                        log.debug("Response received for request id=$id")
                    }
                    .onFailure { error ->
                        future.completeExceptionally(error)
                        log.error("Failed to parse response for request id=$id", error)
                    }
            } else {
                log.debug("Received message with id=$id but no matching pending request (likely a server notification)")
            }
            return
        }

        // Message without ID - this is a server notification.
        val method = textValue(jsonNode.get("method")) ?: "unknown"
        val params = jsonNode.get("params")
        log.debug("Received server notification: $method")

        val handler = notificationHandlers[method]
        if (handler != null) {
            runCatching { handler(params) }
                .onSuccess { log.debug("Notification handler for $method completed successfully") }
                .onFailure { error -> log.error("Error handling notification for method=$method", error) }
        } else {
            log.debug("No handler registered for notification method: $method")
        }
    }

    /**
     * Sends a JSON-RPC request and waits for the matching response.
     *
     * @param method the request method name.
     * @param params optional JSON-RPC params.
     * @param timeoutMs how long to wait for a response.
     * @return the matching JSON-RPC response.
     */
    fun sendRequest(method: String, params: JsonNode? = null, timeoutMs: Long = 60000): JsonRpcResponse {
        check(running) { "Cannot send request '$method' because the protocol client is disconnected" }

        val id = UUID.randomUUID().toString()
        val request = JsonRpcRequest(method = method, params = params, id = id)
        val future = CompletableFuture<JsonRpcResponse>()
        pendingRequests[id] = future

        return try {
            runCatching {
                val requestJson = objectMapper.writeValueAsString(request)
                log.debug("Sending request: method=$method id=$id")
                transport.send(requestJson)
                log.debug("Waiting for response: method=$method id=$id (timeout=${timeoutMs}ms)")
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            }.getOrElse { error ->
                when (error) {
                    is java.util.concurrent.TimeoutException -> {
                        log.error("Request timeout for method=$method id=$id after ${timeoutMs}ms")
                        failRequest("Request timeout for method=$method (${timeoutMs}ms)", error)
                    }

                    is InterruptedException -> {
                        Thread.currentThread().interrupt()
                        failRequest("Request interrupted for method=$method id=$id", error)
                    }

                    is IllegalStateException -> {
                        log.error("Request failed due to transport error: method=$method id=$id - ${error.message}")
                        failRequest("Failed to send request to MCP server: ${error.message}", error)
                    }

                    is ExecutionException -> {
                        val cause = error.cause ?: error
                        log.error("Request failed for method=$method id=$id", cause)
                        failRequest("Request failed for method=$method id=$id", cause)
                    }

                    else -> {
                        log.error("Request failed for method=$method id=$id", error)
                        failRequest("Request failed for method=$method id=$id", error)
                    }
                }
            }
        } finally {
            pendingRequests.remove(id)
        }
    }

    /**
     * Sends a JSON-RPC notification without expecting a response.
     *
     * @param method the notification method name.
     * @param params optional JSON-RPC params.
     */
    fun sendNotification(method: String, params: JsonNode? = null) {
        val notification = JsonRpcNotification(method = method, params = params)
        runCatching { transport.send(objectMapper.writeValueAsString(notification)) }
            .onFailure { error -> log.error("Failed to send notification method=$method", error) }
            .getOrThrow()
    }
}

/**
 * Signals a protocol failure while preserving the original cause.
 *
 * @param message the failure message.
 * @param cause the underlying cause, if any.
 * @return never returns normally.
 */
private fun failRequest(message: String, cause: Throwable? = null): Nothing {
    throw McpProtocolException(message, cause)
}

/**
 * Converts a JSON node to text when possible.
 *
 * @param node the node to inspect.
 * @return the node text or null when the node is missing or not textual.
 */
private fun textValue(node: JsonNode?): String? {
    return node?.takeUnless { it.isNull }?.toString()?.unquote()
}

class McpProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun String.unquote(): String {
    return if (length >= 2 && startsWith('"') && endsWith('"')) {
        substring(1, length - 1)
    } else {
        this
    }
}

