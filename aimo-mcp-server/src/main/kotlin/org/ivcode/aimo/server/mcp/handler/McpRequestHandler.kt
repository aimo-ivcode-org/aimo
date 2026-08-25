package org.ivcode.aimo.server.mcp.handler

import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.slf4j.LoggerFactory

/**
 * Routes JSON-RPC requests to appropriate handlers.
 *
 * Dispatches tool/call and prompts/get methods to registered service methods,
 * handling errors and validation.
 */
class McpRequestHandler(
    private val serviceRegistry: McpServiceRegistry,
    private val toolCallHandler: ToolCallHandler,
    private val promptGetHandler: PromptGetHandler
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle a JSON-RPC request and return a response.
     */
    fun handleRequest(request: JsonRpcRequest): JsonRpcResponse =
        try {
            logger.debug("Handling JSON-RPC request: method={}, id={}", request.method, request.id)
            dispatchRequest(request)
        } catch (exception: IllegalArgumentException) {
            logger.error("Error handling request: ${request.method}", exception)
            internalErrorResponse(request, exception)
        } catch (exception: IllegalStateException) {
            logger.error("Error handling request: ${request.method}", exception)
            internalErrorResponse(request, exception)
        }

    /**
     * Dispatch the request to the appropriate MCP handler.
     *
     * @param request incoming JSON-RPC request.
     * @return handler response for the request method.
     */
    private fun dispatchRequest(request: JsonRpcRequest): JsonRpcResponse {
        return when (request.method) {
            "tools/call" -> toolCallHandler.handle(request)
            "prompts/get" -> promptGetHandler.handle(request)
            "tools/list" -> handleToolsList(request)
            "prompts/list" -> handlePromptsList(request)
            "initialize" -> handleInitialize(request)
            else -> unknownMethodResponse(request)
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

    /**
     * Build the protocol response for an unknown method.
     *
     * @param request request that could not be routed.
     * @return JSON-RPC method-not-found response.
     */
    private fun unknownMethodResponse(request: JsonRpcRequest): JsonRpcResponse {
        logger.warn("Unknown method: {}", request.method)
        return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = McpErrorCode.METHOD_NOT_FOUND,
                message = "Method '${request.method}' not found"
            )
        )
    }

    /**
     * Build a standard internal-error response.
     *
     * @param request request that failed while being processed.
     * @param exception failure that should be surfaced as an internal error.
     * @return JSON-RPC internal-error response.
     */
    private fun internalErrorResponse(
        request: JsonRpcRequest,
        exception: Exception
    ): JsonRpcResponse {
        return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = McpErrorCode.INTERNAL_ERROR,
                message = "Internal server error: ${exception.message}"
            )
        )
    }
}

