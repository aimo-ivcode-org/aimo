package org.ivcode.aimo.mcpclient.protocol

import org.ivcode.aimo.mcpclient.protocol.transport.ProtocolTransport
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
        try {
            while (running) {
                val messageText = transport.receive()
                val jsonNode = objectMapper.readTree(messageText)
                handleMessage(jsonNode)
            }
        } catch (e: Exception) {
            if (running) {
                log.error("Error reading messages from transport", e)
            }
        }
    }

    private fun handleMessage(jsonNode: JsonNode) {
        val id = jsonNode.get("id")?.asText()
        if (id != null) {
            val future = pendingRequests.remove(id)
            if (future != null) {
                try {
                    val response = objectMapper.treeToValue(jsonNode, JsonRpcResponse::class.java)
                    future.complete(response)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        }
    }

    fun sendRequest(method: String, params: JsonNode? = null, timeoutMs: Long = 30000): JsonRpcResponse {
        val id = UUID.randomUUID().toString()
        val request = JsonRpcRequest(method = method, params = params, id = id)
        val future = CompletableFuture<JsonRpcResponse>()
        pendingRequests[id] = future

        return try {
            transport.send(objectMapper.writeValueAsString(request))
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            pendingRequests.remove(id)
            log.error("Request timeout for method=$method id=$id")
            throw McpProtocolException("Request timeout for method=$method", e)
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

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonNode? = null,
    val id: String? = null,
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonNode? = null,
    val error: JsonRpcError? = null,
    val id: String,
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonNode? = null,
)

data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonNode? = null,
)

class McpProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

