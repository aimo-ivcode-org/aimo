package org.ivcode.aimo.server.mcp.transport

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.handler.McpRequestHandler
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SSE (Server-Sent Events) transport for MCP server.
 *
 * Provides streaming connections for bidirectional communication
 * using Server-Sent Events.
 */
@RestController
@RequestMapping("/mcp/sse")
class SseMcpTransport(
    private val requestHandler: McpRequestHandler,
    private val objectMapper: ObjectMapper
) : McpTransport {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val emitters = CopyOnWriteArrayList<SseEmitter>()
    private var isActive = false

    override val name: String = "sse"

    override fun initialize() {
        logger.info("SSE MCP transport initialized at /mcp/sse")
        isActive = true
    }

    override fun shutdown() {
        logger.info("SSE MCP transport shutdown")
        emitters.forEach { emitter ->
            try {
                emitter.complete()
            } catch (e: IOException) {
                logger.debug("Error completing SSE emitter", e)
            }
        }
        emitters.clear()
        isActive = false
    }

    override fun isActive(): Boolean = isActive

    override fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return requestHandler.handleRequest(request)
    }

    /**
     * Establish SSE connection for streaming.
     */
    @GetMapping("/connect")
    fun connect(): SseEmitter {
        logger.debug("New SSE client connected")
        val emitter = SseEmitter(300000L)  // 5 minute timeout

        emitter.onCompletion {
            logger.debug("SSE emitter completed")
            emitters.remove(emitter)
        }
        emitter.onTimeout {
            logger.debug("SSE emitter timeout")
            emitters.remove(emitter)
        }
        emitter.onError { throwable ->
            logger.debug("SSE emitter error", throwable)
            emitters.remove(emitter)
        }

        emitters.add(emitter)

        try {
            // Send initial connection message
            emitter.send(
                SseEmitter.event()
                    .id("connection")
                    .name("connected")
                    .data(mapOf("status" to "connected"))
                    .build()
            )
        } catch (e: IOException) {
            logger.error("Error sending initial SSE message", e)
            emitters.remove(emitter)
        }

        return emitter
    }

    /**
     * Handle MCP request over SSE connection.
     * Client sends JSON-RPC request, we send response via SSE.
     */
    @PostMapping(
        "/request",
        consumes = ["application/json"],
        produces = ["application/json"]
    )
    fun handleSseRequest(@RequestBody request: JsonRpcRequest): Map<String, Any?> {
        logger.debug("Received SSE MCP request: method={}, id={}", request.method, request.id)

        val response = handleRequest(request)

        // Broadcast response to all connected clients
        broadcastMessage(response)

        return mapOf(
            "status" to "received",
            "id" to request.id
        )
    }

    /**
     * Broadcast a message to all connected SSE clients.
     */
    private fun broadcastMessage(response: JsonRpcResponse) {
        emitters.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .id(response.id?.toString() ?: System.currentTimeMillis().toString())
                        .name("response")
                        .data(response)
                        .build()
                )
            } catch (e: IOException) {
                logger.debug("Error sending SSE message, removing emitter", e)
                emitters.remove(emitter)
            }
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "healthy",
            "transport" to "sse",
            "connected_clients" to emitters.size
        )
    }
}

