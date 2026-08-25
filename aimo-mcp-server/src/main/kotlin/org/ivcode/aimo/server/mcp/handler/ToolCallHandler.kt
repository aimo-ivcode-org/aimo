package org.ivcode.aimo.server.mcp.handler

import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

/**
 * Handles tool/call requests - invokes @McpTool methods with parameter binding.
 */
class ToolCallHandler(
    private val serviceRegistry: McpServiceRegistry,
    private val parameterBinder: ParameterBinder
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle a tool call request.
     *
     * Expects params to contain:
     * - name: tool name (or "beanName:toolName")
     * - arguments: map of tool parameters
     */
    fun handle(request: JsonRpcRequest): JsonRpcResponse =
        try {
            when (val parsedRequest = parseRequest(request)) {
                is ToolRequestParseResult.Error -> parsedRequest.response
                is ToolRequestParseResult.Success -> executeToolCall(request.id, parsedRequest)
            }
        } catch (exception: IllegalArgumentException) {
            logger.error("Error handling tool call", exception)
            error(request.id, McpErrorCode.INTERNAL_ERROR, "Internal error: ${exception.message}")
        } catch (exception: IllegalStateException) {
            logger.error("Error handling tool call", exception)
            error(request.id, McpErrorCode.INTERNAL_ERROR, "Internal error: ${exception.message}")
        }

    /**
     * Parse the request envelope into the fields required for tool invocation.
     *
     * @param request incoming JSON-RPC request.
     * @return either a parsed request or an error response to return to the client.
     */
    private fun parseRequest(request: JsonRpcRequest): ToolRequestParseResult {
        // Validate the JSON-RPC params object before attempting any lookup work.
        val params = request.params
        val toolName = params?.get("name")?.toString()
        val response = when {
            params == null -> ToolRequestParseResult.Error(
                error(request.id, McpErrorCode.INVALID_PARAMS, "Missing 'params' in request")
            )

            toolName == null -> ToolRequestParseResult.Error(
                error(request.id, McpErrorCode.INVALID_PARAMS, "Missing 'name' parameter")
            )

            else -> {
                val arguments = (params["arguments"] as? Map<*, *>)
                    ?.mapKeys { (key, _) -> key.toString() }
                    ?: emptyMap()
                ToolRequestParseResult.Success(toolName = toolName, arguments = arguments)
            }
        }

        return response
    }

    /**
     * Execute a resolved tool call.
     *
     * @param requestId JSON-RPC request identifier.
     * @param parsedRequest validated tool request data.
     * @return JSON-RPC response containing either the tool result or a protocol error.
     */
    private fun executeToolCall(
        requestId: Any?,
        parsedRequest: ToolRequestParseResult.Success
    ): JsonRpcResponse {
        // Resolve the tool metadata before binding or invoking anything.
        val toolRegistry = serviceRegistry.getTool(parsedRequest.toolName)
        val response = if (toolRegistry == null) {
            error(
                requestId,
                McpErrorCode.TOOL_NOT_FOUND,
                "Tool '${parsedRequest.toolName}' not found"
            )
        } else {
            logger.debug("Invoking tool: {}", parsedRequest.toolName)

            // Bind incoming arguments to the reflected method signature.
            when (val bindingResult = bindToolParameters(requestId, parsedRequest, toolRegistry.method)) {
                is ToolBindingResult.Error -> bindingResult.response
                is ToolBindingResult.Success -> {
                    // Invoke the tool and convert the result into MCP response content.
                    invokeTool(
                        requestId,
                        parsedRequest.toolName,
                        toolRegistry.bean,
                        toolRegistry.method,
                        bindingResult.bindingResult
                    )
                }
            }
        }

        return response
    }

    /**
     * Bind tool arguments to the reflected method parameters.
     *
     * @param requestId JSON-RPC request identifier.
     * @param parsedRequest validated tool request data.
     * @param method tool method to bind.
     * @return either the bound parameters or a ready INVALID_PARAMS response.
     */
    private fun bindToolParameters(
        requestId: Any?,
        parsedRequest: ToolRequestParseResult.Success,
        method: Method
    ): ToolBindingResult {
        return try {
            ToolBindingResult.Success(
                parameterBinder.bindParameters(
                    method = method,
                    arguments = parsedRequest.arguments,
                    context = buildToolContext(parsedRequest.toolName, requestId)
                )
            )
        } catch (exception: ParameterBindingException) {
            logger.warn(
                "Parameter binding failed for tool '{}': {}",
                parsedRequest.toolName,
                exception.message
            )
            ToolBindingResult.Error(
                error(
                    requestId,
                    McpErrorCode.INVALID_PARAMS,
                    exception.message ?: "Invalid parameter"
                )
            )
        }
    }

    /**
     * Invoke the target tool method.
     *
     * @param requestId JSON-RPC request identifier.
     * @param toolName tool name used for logging and error reporting.
     * @param target bean instance that owns the method.
     * @param method reflected method to invoke.
     * @param bindingResult bound argument values.
     * @return JSON-RPC response for the tool invocation.
     */
    private fun invokeTool(
        requestId: Any?,
        toolName: String,
        target: Any,
        method: Method,
        bindingResult: ParameterBinder.BindingResult
    ): JsonRpcResponse {
        return try {
            val result = MethodInvocationSupport.invoke(target, method, bindingResult)
            logger.debug("Tool invocation succeeded: {}", toolName)
            buildSuccessResponse(requestId, result)
        } catch (exception: ReflectiveOperationException) {
            executionError(requestId, toolName, exception)
        } catch (exception: IllegalArgumentException) {
            executionError(requestId, toolName, exception)
        }
    }

    /**
     * Build the request context passed to @McpContext parameters.
     *
     * @param toolName resolved tool name.
     * @param requestId JSON-RPC request identifier.
     * @return context map visible to the invoked tool.
     */
    private fun buildToolContext(toolName: String, requestId: Any?): Map<String, Any?> {
        return mapOf(
            "toolName" to toolName,
            "requestId" to requestId?.toString()
        )
    }

    /**
     * Build a successful tool response payload.
     *
     * @param requestId JSON-RPC request identifier.
     * @param result tool result object.
     * @return JSON-RPC response containing MCP text content.
     */
    private fun buildSuccessResponse(requestId: Any?, result: Any?): JsonRpcResponse {
        return JsonRpcResponse(
            id = requestId,
            result = mapOf(
                "content" to listOf(
                    mapOf(
                        "type" to "text",
                        "text" to result.toString()
                    )
                )
            )
        )
    }

    /**
     * Convert an invocation failure into an MCP tool error response.
     *
     * @param requestId JSON-RPC request identifier.
     * @param toolName tool name used for logging.
     * @param exception invocation failure.
     * @return JSON-RPC error response.
     */
    private fun executionError(
        requestId: Any?,
        toolName: String,
        exception: Exception
    ): JsonRpcResponse {
        logger.error("Tool invocation failed: {}", toolName, exception)
        return error(
            requestId,
            McpErrorCode.TOOL_EXECUTION_FAILED,
            "Tool execution failed: ${exception.cause?.message ?: exception.message}"
        )
    }

    private fun error(id: Any?, code: Int, message: String): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            error = JsonRpcError(code = code, message = message)
        )
    }
}

private sealed interface ToolRequestParseResult {
    /**
     * Parsed tool request payload.
     *
     * @property toolName resolved tool name from the request.
     * @property arguments request arguments keyed by parameter name.
     */
    data class Success(
        val toolName: String,
        val arguments: Map<String, Any?>
    ) : ToolRequestParseResult

    /**
     * Error response produced while parsing the tool request.
     *
     * @property response JSON-RPC response to return immediately.
     */
    data class Error(val response: JsonRpcResponse) : ToolRequestParseResult
}

private sealed interface ToolBindingResult {
    /**
     * Successful tool argument binding.
     *
     * @property bindingResult resolved arguments for invocation.
     */
    data class Success(val bindingResult: ParameterBinder.BindingResult) : ToolBindingResult

    /**
     * Error response produced while binding tool arguments.
     *
     * @property response JSON-RPC response to return immediately.
     */
    data class Error(val response: JsonRpcResponse) : ToolBindingResult
}

