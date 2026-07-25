package org.ivcode.aimo.server.mcp.transport

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.handler.McpRequestHandler
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP transport for MCP server.
 *
 * Registered as a Spring REST controller that handles JSON-RPC requests
 * via HTTP POST endpoints.
 */
@RestController
@RequestMapping("\${aimo.mcp.transports.http.basePath:/mcp}")
class HttpMcpTransport(
    private val requestHandler: McpRequestHandler,
    private val objectMapper: ObjectMapper
) : McpTransport {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var isActive = false

    override val name: String = "http"

    override fun initialize() {
        logger.info("HTTP MCP transport initialized at /mcp")
        isActive = true
    }

    override fun shutdown() {
        logger.info("HTTP MCP transport shutdown")
        isActive = false
    }

    override fun isActive(): Boolean = isActive

    override fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return requestHandler.handleRequest(request)
    }

    /**
     * Main entry point for HTTP POST requests.
     * Handles both /mcp and /mcp/ paths for compatibility.
     */
    @PostMapping(
        path = ["", "/"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun handleJsonRpc(@RequestBody request: JsonRpcRequest): JsonRpcResponse {
        logger.debug("Received HTTP MCP request: method={}, id={}", request.method, request.id)
        val response = handleRequest(request)
        logger.debug("Returning HTTP MCP response: id={}, hasError={}", response.id, response.error != null)
        return response
    }

    /**
     * Single request endpoint - for tools/call.
     */
    @PostMapping(
        path = ["/tools/call", "tools/call"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun toolsCall(@RequestBody requestBody: Map<String, Any?>): JsonRpcResponse {
        val request = JsonRpcRequest(
            id = requestBody["id"],
            method = "tools/call",
            params = requestBody["params"] as? Map<String, Any?>
        )
        return handleRequest(request)
    }

    /**
     * Single request endpoint - for prompts/get.
     */
    @PostMapping(
        path = ["/prompts/get", "prompts/get"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun promptsGet(@RequestBody requestBody: Map<String, Any?>): JsonRpcResponse {
        val request = JsonRpcRequest(
            id = requestBody["id"],
            method = "prompts/get",
            params = requestBody["params"] as? Map<String, Any?>
        )
        return handleRequest(request)
    }

    /**
     * Single request endpoint - for tools/list.
     */
    @PostMapping(
        path = ["/tools/list", "tools/list"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun toolsList(@RequestBody requestBody: Map<String, Any?>): JsonRpcResponse {
        val request = JsonRpcRequest(
            id = requestBody["id"],
            method = "tools/list",
            params = requestBody["params"] as? Map<String, Any?>
        )
        return handleRequest(request)
    }

    /**
     * Single request endpoint - for prompts/list.
     */
    @PostMapping(
        path = ["/prompts/list", "prompts/list"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun promptsList(@RequestBody requestBody: Map<String, Any?>): JsonRpcResponse {
        val request = JsonRpcRequest(
            id = requestBody["id"],
            method = "prompts/list",
            params = requestBody["params"] as? Map<String, Any?>
        )
        return handleRequest(request)
    }
}

