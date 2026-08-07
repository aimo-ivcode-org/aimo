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
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.kotlinFunction

/**
 * Handles tool/call requests - invokes @McpTool methods with parameter binding.
 */
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
            val bindingResult = try {
                parameterBinder.bindParameters(
                    method = toolRegistry.method,
                    arguments = arguments,
                    context = mapOf(
                        "toolName" to toolName,
                        "requestId" to request.id?.toString()
                    )
                )
            } catch (e: ParameterBindingException) {
                logger.warn("Parameter binding failed for tool '{}': {}", toolName, e.message)
                return error(
                    request.id,
                    McpErrorCode.INVALID_PARAMS,
                    e.message ?: "Invalid parameter"
                )
            }

            // Invoke tool. Prefer Kotlin callBy when available so we can respect Kotlin default parameters
            val result = try {
                val kotlinFn = toolRegistry.method.kotlinFunction
                if (kotlinFn != null) {
                    // Build callBy map of KParameter -> value. Include instance parameter.
                    val callByMap = mutableMapOf<KParameter, Any?>()
                    kotlinFn.instanceParameter?.let { callByMap[it] = toolRegistry.bean }

                    for (kp in kotlinFn.parameters) {
                        if (kp == kotlinFn.instanceParameter) continue

                        val name = kp.name
                        if (name != null && bindingResult.provided.contains(name)) {
                            callByMap[kp] = bindingResult.values[name]
                        } else {
                            // If the Java parameter has @McpContext, inject context even if not provided
                            val javaParam = toolRegistry.method.parameters.firstOrNull { it.name == name }
                            if (javaParam != null && javaParam.getAnnotation(McpContext::class.java) != null) {
                                callByMap[kp] = bindingResult.context
                            }
                        }
                    }

                    kotlinFn.isAccessible = true
                    kotlinFn.callBy(callByMap)
                } else {
                    // Fallback to Java reflection: build args in declaration order
                    val javaArgs = toolRegistry.method.parameters.map { p -> bindingResult.values[p.name] }
                    toolRegistry.method.invoke(toolRegistry.bean, *javaArgs.toTypedArray())
                }
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
 * Exception thrown when parameter binding fails.
 */
class ParameterBindingException(
    val parameterName: String,
    val expectedType: Class<*>,
    val providedValue: Any?,
    message: String
) : Exception(message)

/**
 * Binds request parameters to method arguments.
 */
class ParameterBinder(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class BindingResult(
        val values: Map<String, Any?>,
        val provided: Set<String>,
        val context: Map<String, Any?>
    )

    /**
     * Bind parameters from request to method arguments.
     * @throws ParameterBindingException if parameter conversion fails
     */
    fun bindParameters(
        method: java.lang.reflect.Method,
        arguments: Map<String, Any?>,
        context: Map<String, Any?>
    ): BindingResult {
        val values = mutableMapOf<String, Any?>()
        val provided = mutableSetOf<String>()

        for (param in method.parameters) {
            val name = param.name ?: throw ParameterBindingException(
                parameterName = "<unknown>",
                expectedType = param.type,
                providedValue = null,
                message = "Unable to bind parameter with no name"
            )

            // Check if this is a context parameter
            if (param.getAnnotation(McpContext::class.java) != null) {
                values[name] = context
                provided.add(name)
                continue
            }

            // Get value from arguments
            val value = arguments[name]

            // Check if parameter is optional (not required and not in arguments)
            val paramAnnotation = param.getAnnotation(McpParam::class.java)
            val isOptional = paramAnnotation != null && !paramAnnotation.required
            val isProvided = arguments.containsKey(name)

            if (!isOptional && !isProvided) {
                throw ParameterBindingException(
                    parameterName = name,
                    expectedType = param.type,
                    providedValue = null,
                    message = "Missing required parameter '$name'"
                )
            }

            // Type conversion for provided non-null values
            val convertedValue: Any? = when {
                value != null && param.type.isAssignableFrom(value.javaClass) -> value
                value != null && param.type == String::class.java -> value.toString()
                value != null && (param.type == Int::class.java || param.type == Integer::class.java) -> {
                    when (value) {
                        is Number -> value.toInt()
                        is String -> {
                            value.toIntOrNull() ?: throw ParameterBindingException(
                                parameterName = name,
                                expectedType = param.type,
                                providedValue = value,
                                message = "Parameter '$name' expects an integer but got invalid value: '$value'"
                            )
                        }
                        else -> throw ParameterBindingException(
                            parameterName = name,
                            expectedType = param.type,
                            providedValue = value,
                            message = "Parameter '$name' expects an integer but got ${value.javaClass.simpleName}: $value"
                        )
                    }
                }
                value != null && param.type == Long::class.java -> {
                    when (value) {
                        is Number -> value.toLong()
                        is String -> {
                            value.toLongOrNull() ?: throw ParameterBindingException(
                                parameterName = name,
                                expectedType = param.type,
                                providedValue = value,
                                message = "Parameter '$name' expects a long but got invalid value: '$value'"
                            )
                        }
                        else -> throw ParameterBindingException(
                            parameterName = name,
                            expectedType = param.type,
                            providedValue = value,
                            message = "Parameter '$name' expects a long but got ${value.javaClass.simpleName}: $value"
                        )
                    }
                }
                value != null && (param.type == Double::class.java || param.type == Float::class.java) -> {
                    when (value) {
                        is Number -> value.toDouble()
                        is String -> {
                            value.toDoubleOrNull() ?: throw ParameterBindingException(
                                parameterName = name,
                                expectedType = param.type,
                                providedValue = value,
                                message = "Parameter '$name' expects a decimal number but got invalid value: '$value'"
                            )
                        }
                        else -> throw ParameterBindingException(
                            parameterName = name,
                            expectedType = param.type,
                            providedValue = value,
                            message = "Parameter '$name' expects a decimal number but got ${value.javaClass.simpleName}: $value"
                        )
                    }
                }
                value != null && (param.type == Boolean::class.java || param.type == java.lang.Boolean::class.java) -> {
                    when (value) {
                        is Boolean -> value
                        is String -> value.equals("true", ignoreCase = true)
                        else -> throw ParameterBindingException(
                            parameterName = name,
                            expectedType = param.type,
                            providedValue = value,
                            message = "Parameter '$name' expects a boolean but got ${value.javaClass.simpleName}: $value"
                        )
                    }
                }
                value != null -> {
                    // Try to convert using ObjectMapper
                    try {
                        objectMapper.convertValue(value, param.type)
                    } catch (e: Exception) {
                        throw ParameterBindingException(
                            parameterName = name,
                            expectedType = param.type,
                            providedValue = value,
                            message = "Could not convert parameter '$name' to type ${param.type.simpleName}: ${e.message}"
                        )
                    }
                }
                isProvided && value == null -> {
                    // Explicitly provided null
                    null
                }
                else -> null
            }

            values[name] = convertedValue
            if (isProvided) provided.add(name)
        }

        return BindingResult(values = values, provided = provided, context = context)
    }
}

