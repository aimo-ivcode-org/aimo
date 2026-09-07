package org.ivcode.aimo.server.mcp.handler

import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

/**
 * Handles prompts/get requests - invokes @McpPrompt methods.
 */
class PromptGetHandler(
    private val serviceRegistry: McpServiceRegistry,
    private val parameterBinder: ParameterBinder
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle a prompt get request.
     *
     * Expects params to contain:
     * - name: prompt name (or "beanName:promptName")
     * - arguments: optional map of prompt parameters
     */
    fun handle(request: JsonRpcRequest): JsonRpcResponse =
        try {
            when (val parsedRequest = parseRequest(request)) {
                is PromptRequestParseResult.Error -> parsedRequest.response
                is PromptRequestParseResult.Success -> executePromptRequest(request.id, parsedRequest)
            }
        } catch (exception: IllegalArgumentException) {
            logger.error("Error handling prompt get", exception)
            error(request.id, McpErrorCode.INTERNAL_ERROR, "Internal error: ${exception.message}")
        } catch (exception: IllegalStateException) {
            logger.error("Error handling prompt get", exception)
            error(request.id, McpErrorCode.INTERNAL_ERROR, "Internal error: ${exception.message}")
        }

    /**
     * Parse the prompt request payload.
     *
     * @param request incoming JSON-RPC request.
     * @return either parsed prompt invocation data or a ready error response.
     */
    private fun parseRequest(request: JsonRpcRequest): PromptRequestParseResult {
        // Validate the request envelope before performing service lookup.
        val params = request.params
        val promptName = params?.get("name")?.toString()
        val response = when {
            params == null -> PromptRequestParseResult.Error(
                error(request.id, McpErrorCode.INVALID_PARAMS, "Missing 'params' in request")
            )

            promptName == null -> PromptRequestParseResult.Error(
                error(request.id, McpErrorCode.INVALID_PARAMS, "Missing 'name' parameter")
            )

            else -> {
                val arguments = (params["arguments"] as? Map<*, *>)
                    ?.mapKeys { (key, _) -> key.toString() }
                    ?: emptyMap()
                PromptRequestParseResult.Success(promptName = promptName, arguments = arguments)
            }
        }

        return response
    }

    /**
     * Execute a resolved prompt request.
     *
     * @param requestId JSON-RPC request identifier.
     * @param parsedRequest validated prompt request data.
     * @return JSON-RPC response containing either prompt messages or a protocol error.
     */
    private fun executePromptRequest(
        requestId: Any?,
        parsedRequest: PromptRequestParseResult.Success
    ): JsonRpcResponse {
        // Resolve the prompt metadata before binding or invoking anything.
        val promptRegistry = serviceRegistry.getPrompt(parsedRequest.promptName)
        val response = if (promptRegistry == null) {
            error(
                requestId,
                McpErrorCode.PROMPT_NOT_FOUND,
                "Prompt '${parsedRequest.promptName}' not found"
            )
        } else {
            logger.debug("Invoking prompt: {}", parsedRequest.promptName)

            when (val bindingResult = bindPromptParameters(requestId, parsedRequest, promptRegistry.method)) {
                is PromptBindingResult.Error -> bindingResult.response
                is PromptBindingResult.Success -> {
                    // Invoke the prompt and translate the return value into MCP message content.
                    invokePrompt(
                        requestId = requestId,
                        promptName = parsedRequest.promptName,
                        target = promptRegistry.bean,
                        method = promptRegistry.method,
                        bindingResult = bindingResult.bindingResult
                    )
                }
            }
        }

        return response
    }

    /**
     * Bind prompt arguments to the reflected method parameters.
     *
     * @param requestId JSON-RPC request identifier.
     * @param parsedRequest validated prompt request data.
     * @param method prompt method to bind.
     * @return either the bound parameters or a ready INVALID_PARAMS response.
     */
    private fun bindPromptParameters(
        requestId: Any?,
        parsedRequest: PromptRequestParseResult.Success,
        method: Method
    ): PromptBindingResult {
        return try {
            PromptBindingResult.Success(
                parameterBinder.bindParameters(
                    method = method,
                    arguments = parsedRequest.arguments,
                    context = buildPromptContext(parsedRequest.promptName, requestId)
                )
            )
        } catch (exception: ParameterBindingException) {
            logger.warn(
                "Parameter binding failed for prompt '{}': {}",
                parsedRequest.promptName,
                exception.message
            )
            PromptBindingResult.Error(
                error(
                    requestId,
                    McpErrorCode.INVALID_PARAMS,
                    exception.message ?: "Invalid parameter"
                )
            )
        }
    }

    /**
     * Invoke the target prompt method.
     *
     * @param requestId JSON-RPC request identifier.
     * @param promptName prompt name used for logging.
     * @param target bean instance that owns the method.
     * @param method reflected prompt method.
     * @param bindingResult bound argument values.
     * @return JSON-RPC response for the prompt invocation.
     */
    private fun invokePrompt(
        requestId: Any?,
        promptName: String,
        target: Any,
        method: Method,
        bindingResult: ParameterBinder.BindingResult
    ): JsonRpcResponse {
        return try {
            val result = MethodInvocationSupport.invoke(target, method, bindingResult)
            logger.debug("Prompt invocation succeeded: {}", promptName)
            buildSuccessResponse(requestId, result)
        } catch (exception: ReflectiveOperationException) {
            executionError(requestId, promptName, exception)
        } catch (exception: IllegalArgumentException) {
            executionError(requestId, promptName, exception)
        }
    }

    /**
     * Build the request context passed to @McpContext prompt parameters.
     *
     * @param promptName resolved prompt name.
     * @param requestId JSON-RPC request identifier.
     * @return context map visible to the invoked prompt.
     */
    private fun buildPromptContext(promptName: String, requestId: Any?): Map<String, Any?> {
        return mapOf(
            "promptName" to promptName,
            "requestId" to requestId?.toString()
        )
    }

    /**
     * Build a successful prompt response payload.
     *
     * @param requestId JSON-RPC request identifier.
     * @param result prompt result object.
     * @return JSON-RPC response containing MCP prompt messages.
     */
    private fun buildSuccessResponse(requestId: Any?, result: Any?): JsonRpcResponse {
        return JsonRpcResponse(
            id = requestId,
            result = mapOf(
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to result?.toString()
                    )
                )
            )
        )
    }

    /**
     * Convert an invocation failure into an MCP prompt error response.
     *
     * @param requestId JSON-RPC request identifier.
     * @param promptName prompt name used for logging.
     * @param exception invocation failure.
     * @return JSON-RPC error response.
     */
    private fun executionError(
        requestId: Any?,
        promptName: String,
        exception: Exception
    ): JsonRpcResponse {
        logger.error("Prompt invocation failed: {}", promptName, exception)
        return error(
            requestId,
            McpErrorCode.TOOL_EXECUTION_FAILED,
            "Prompt execution failed: ${exception.cause?.message ?: exception.message}"
        )
    }

    private fun error(id: Any?, code: Int, message: String): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            error = JsonRpcError(code = code, message = message)
        )
    }
}

private sealed interface PromptRequestParseResult {
    /**
     * Parsed prompt request payload.
     *
     * @property promptName resolved prompt name from the request.
     * @property arguments request arguments keyed by parameter name.
     */
    data class Success(
        val promptName: String,
        val arguments: Map<String, Any?>
    ) : PromptRequestParseResult

    /**
     * Error response produced while parsing the prompt request.
     *
     * @property response JSON-RPC response to return immediately.
     */
    data class Error(val response: JsonRpcResponse) : PromptRequestParseResult
}

private sealed interface PromptBindingResult {
    /**
     * Successful prompt argument binding.
     *
     * @property bindingResult resolved arguments for invocation.
     */
    data class Success(val bindingResult: ParameterBinder.BindingResult) : PromptBindingResult

    /**
     * Error response produced while binding prompt arguments.
     *
     * @property response JSON-RPC response to return immediately.
     */
    data class Error(val response: JsonRpcResponse) : PromptBindingResult
}

