package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Routes JSON-RPC requests to appropriate handlers.
 *
 * Dispatches tool/call and prompts/get methods to registered service methods,
 * handling errors and validation.
 */
@Component
class McpRequestHandler(
    private val serviceRegistry: McpServiceRegistry,
    private val toolCallHandler: ToolCallHandler,
    private val promptGetHandler: PromptGetHandler,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle a JSON-RPC request and return a response.
     */
    fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            logger.debug("Handling JSON-RPC request: method={}, id={}", request.method, request.id)

            when (request.method) {
                "tools/call" -> toolCallHandler.handle(request)
                "prompts/get" -> promptGetHandler.handle(request)
                "tools/list" -> handleToolsList(request)
                "prompts/list" -> handlePromptsList(request)
                "initialize" -> handleInitialize(request)
                else -> {
                    logger.warn("Unknown method: {}", request.method)
                    JsonRpcResponse(
                        id = request.id,
                        error = JsonRpcError(
                            code = McpErrorCode.METHOD_NOT_FOUND,
                            message = "Method '${request.method}' not found"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling request: ${request.method}", e)
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = McpErrorCode.INTERNAL_ERROR,
                    message = "Internal server error: ${e.message}"
                )
            )
        }
    }

    /**
     * Handle tools/list request.
     */
    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = serviceRegistry.getToolDefinitions()
        return JsonRpcResponse(
            id = request.id,
            result = mapOf("tools" to tools)
        )
    }

    /**
     * Handle prompts/list request.
     */
    private fun handlePromptsList(request: JsonRpcRequest): JsonRpcResponse {
        val prompts = serviceRegistry.getPromptDefinitions()
        return JsonRpcResponse(
            id = request.id,
            result = mapOf("prompts" to prompts)
        )
    }

    /**
     * Handle initialize request.
     */
    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        // Stub for now - detailed initialization in transport layer
        return JsonRpcResponse(
            id = request.id,
            result = mapOf(
                "protocolVersion" to "2024-11-05",
                "capabilities" to mapOf(
                    "tools" to mapOf("listChanged" to false),
                    "prompts" to mapOf("listChanged" to false)
                ),
                "serverInfo" to mapOf(
                    "name" to "aimo-mcp-server",
                    "version" to "1.0.0"
                )
            )
        )
    }
}

