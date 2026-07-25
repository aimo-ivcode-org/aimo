package org.ivcode.aimo.server.mcp.error

import com.fasterxml.jackson.annotation.JsonInclude
import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Handles error conversion and formatting for MCP responses.
 */
@Component
class McpErrorHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Convert an exception to a structured MCP error.
     */
    fun handleException(exception: Exception, requestId: Any?): JsonRpcError {
        logger.error("Handling exception for request $requestId", exception)

        return when (exception) {
            is ToolNotFoundException -> JsonRpcError(
                code = McpErrorCode.TOOL_NOT_FOUND,
                message = exception.message ?: "Tool not found"
            )
            is PromptNotFoundException -> JsonRpcError(
                code = McpErrorCode.PROMPT_NOT_FOUND,
                message = exception.message ?: "Prompt not found"
            )
            is ParameterValidationException -> JsonRpcError(
                code = McpErrorCode.INVALID_PARAMS,
                message = exception.message ?: "Invalid parameters"
            )
            is ToolExecutionException -> JsonRpcError(
                code = McpErrorCode.TOOL_EXECUTION_FAILED,
                message = exception.message ?: "Tool execution failed",
                data = mapOf("cause" to (exception.cause?.message ?: "Unknown"))
            )
            is IllegalArgumentException -> JsonRpcError(
                code = McpErrorCode.INVALID_PARAMS,
                message = exception.message ?: "Invalid argument"
            )
            else -> JsonRpcError(
                code = McpErrorCode.INTERNAL_ERROR,
                message = "Internal server error: ${exception.message}",
                data = mapOf("type" to exception.javaClass.simpleName)
            )
        }
    }

    /**
     * Create error response for missing required parameter.
     */
    fun missingRequiredParameter(paramName: String): JsonRpcError {
        return JsonRpcError(
            code = McpErrorCode.INVALID_PARAMS,
            message = "Missing required parameter: $paramName"
        )
    }

    /**
     * Create error response for invalid parameter type.
     */
    fun invalidParameterType(paramName: String, expectedType: String): JsonRpcError {
        return JsonRpcError(
            code = McpErrorCode.INVALID_PARAMS,
            message = "Invalid type for parameter '$paramName': expected $expectedType"
        )
    }
}

// Custom exceptions

/**
 * Thrown when a tool is not found.
 */
class ToolNotFoundException(message: String) : Exception(message)

/**
 * Thrown when a prompt is not found.
 */
class PromptNotFoundException(message: String) : Exception(message)

/**
 * Thrown when parameter validation fails.
 */
class ParameterValidationException(message: String) : Exception(message)

/**
 * Thrown when tool execution fails.
 */
class ToolExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

