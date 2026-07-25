package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.annotation.McpContext
import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.reflect.Parameter

/**
 * Handles tool/call requests - invokes @McpTool methods with parameter binding.
 */
@Component
class ToolCallHandler(
    private val serviceRegistry: McpServiceRegistry,
    private val parameterBinder: ParameterBinder,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle a tool call request.
     *
     * Expects params to contain:
     * - name: tool name (or "beanName:toolName")
     * - arguments: map of tool parameters
     */
    fun handle(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            val params = request.params ?: return error(
                request.id,
                McpErrorCode.INVALID_PARAMS,
                "Missing 'params' in request"
            )

            val toolName = params["name"]?.toString() ?: return error(
                request.id,
                McpErrorCode.INVALID_PARAMS,
                "Missing 'name' parameter"
            )

            val arguments = (params["arguments"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                ?: emptyMap<String, Any?>()

            // Look up tool
            val toolRegistry = serviceRegistry.getTool(toolName) ?: return error(
                request.id,
                McpErrorCode.TOOL_NOT_FOUND,
                "Tool '$toolName' not found"
            )

            logger.debug("Invoking tool: {}", toolName)

            // Bind parameters
            val boundArgs = parameterBinder.bindParameters(
                method = toolRegistry.method,
                arguments = arguments,
                context = mapOf(
                    "toolName" to toolName,
                    "requestId" to request.id?.toString()
                )
            )

            // Invoke tool
            val result = try {
                toolRegistry.method.invoke(toolRegistry.bean, *boundArgs.toTypedArray())
            } catch (e: Exception) {
                logger.error("Tool invocation failed: {}", toolName, e)
                return error(
                    request.id,
                    McpErrorCode.TOOL_EXECUTION_FAILED,
                    "Tool execution failed: ${e.cause?.message ?: e.message}"
                )
            }

            logger.debug("Tool invocation succeeded: {}", toolName)

            // Return result
            JsonRpcResponse(
                id = request.id,
                result = mapOf("content" to listOf(mapOf("type" to "text", "text" to result.toString())))
            )
        } catch (e: Exception) {
            logger.error("Error handling tool call", e)
            error(request.id, McpErrorCode.INTERNAL_ERROR, "Internal error: ${e.message}")
        }
    }

    private fun error(id: Any?, code: Int, message: String): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            error = JsonRpcError(code = code, message = message)
        )
    }
}

/**
 * Binds request parameters to method arguments.
 */
@Component
class ParameterBinder(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Bind parameters from request to method arguments.
     */
    fun bindParameters(
        method: java.lang.reflect.Method,
        arguments: Map<String, Any?>,
        context: Map<String, Any?>
    ): List<Any?> {
        val boundArgs = mutableListOf<Any?>()

        for (param in method.parameters) {
            // Check if this is a context parameter
            if (param.getAnnotation(McpContext::class.java) != null) {
                boundArgs.add(context)
                continue
            }

            // Get value from arguments
            val value = arguments[param.name]

            // Check if parameter is optional (not required and not in arguments)
            val paramAnnotation = param.getAnnotation(McpParam::class.java)
            val isOptional = paramAnnotation != null && !paramAnnotation.required
            val isProvided = arguments.containsKey(param.name)

            // Type conversion
            val convertedValue = when {
                value != null && param.type.isAssignableFrom(value.javaClass) -> value
                value != null && param.type == String::class.java -> value.toString()
                value != null && (param.type == Int::class.java || param.type == Integer::class.java) -> {
                    when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull() ?: 0
                        else -> 0
                    }
                }
                value != null && param.type == Long::class.java -> {
                    when (value) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                }
                value != null && (param.type == Double::class.java || param.type == Float::class.java) -> {
                    when (value) {
                        is Number -> value.toDouble()
                        is String -> value.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                }
                value != null && (param.type == Boolean::class.java || param.type == java.lang.Boolean::class.java) -> {
                    when (value) {
                        is Boolean -> value
                        is String -> value.equals("true", ignoreCase = true)
                        else -> false
                    }
                }
                value != null -> {
                    // Try to convert using ObjectMapper
                    try {
                        objectMapper.convertValue(value, param.type)
                    } catch (e: Exception) {
                        logger.warn("Could not convert parameter '${param.name}' to type ${param.type}", e)
                        value
                    }
                }
                // Handle missing/null values for optional parameters
                isOptional && !isProvided -> {
                    // Use sensible defaults for optional parameters not provided
                    when {
                        param.type == Boolean::class.java || param.type == java.lang.Boolean::class.java -> false
                        param.type == Int::class.java || param.type == Integer::class.java -> 0
                        param.type == Long::class.java -> 0L
                        param.type == Double::class.java -> 0.0
                        param.type == Float::class.java -> 0.0f
                        param.type == String::class.java -> ""
                        else -> null
                    }
                }
                else -> null
            }

            boundArgs.add(convertedValue)
        }

        return boundArgs
    }
}

