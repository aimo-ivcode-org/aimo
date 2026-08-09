package org.ivcode.aimo.server.mcp.transport

import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse

/**
 * Abstract transport layer for MCP server communications.
 *
 * Different transports (HTTP, SSE, stdio) implement this interface
 * to handle request/response routing.
 */
interface McpTransport {
    /**
     * Name/type of the transport.
     */
    val name: String

    /**
     * Initialize the transport (called during startup).
     */
    fun initialize()

    /**
     * Handle an incoming MCP request and return a response.
     */
    fun handleRequest(request: JsonRpcRequest): JsonRpcResponse

    /**
     * Shutdown the transport gracefully.
     */
    fun shutdown()

    /**
     * Check if the transport is active/running.
     */
    fun isActive(): Boolean
}

/**
 * Configuration for transport setup.
 */
data class TransportConfig(
    val type: String,  // "http", "sse", or "stdio"
    val enabled: Boolean = true,
    val port: Int? = null,
    val path: String? = null,
    val properties: Map<String, Any>? = null
)

