package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.annotation.McpContext
import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.slf4j.LoggerFactory
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.kotlinFunction
import org.springframework.stereotype.Component

/**
 * Handles prompts/get requests - invokes @McpPrompt methods.
 */
class PromptGetHandler(
    private val serviceRegistry: McpServiceRegistry,
    private val parameterBinder: ParameterBinder,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle a prompt get request.
     *
     * Expects params to contain:
     * - name: prompt name (or "beanName:promptName")
     * - arguments: optional map of prompt parameters
     */
    fun handle(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            val params = request.params ?: return error(
                request.id,
                McpErrorCode.INVALID_PARAMS,
                "Missing 'params' in request"
            )

            val promptName = params["name"]?.toString() ?: return error(
                request.id,
                McpErrorCode.INVALID_PARAMS,
                "Missing 'name' parameter"
            )

            val arguments = (params["arguments"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                ?: emptyMap<String, Any?>()

            // Look up prompt
            val promptRegistry = serviceRegistry.getPrompt(promptName) ?: return error(
                request.id,
                McpErrorCode.PROMPT_NOT_FOUND,
                "Prompt '$promptName' not found"
            )

            logger.debug("Invoking prompt: {}", promptName)

            // Bind parameters
            val bindingResult = parameterBinder.bindParameters(
                method = promptRegistry.method,
                arguments = arguments,
                context = mapOf(
                    "promptName" to promptName,
                    "requestId" to request.id?.toString()
                )
            )

            // Invoke prompt (prefer Kotlin callBy to respect defaults)
            val result = try {
                val kotlinFn = promptRegistry.method.kotlinFunction
                if (kotlinFn != null) {
                    val callByMap = mutableMapOf<KParameter, Any?>()
                    kotlinFn.instanceParameter?.let { callByMap[it] = promptRegistry.bean }

                    for (kp in kotlinFn.parameters) {
                        if (kp == kotlinFn.instanceParameter) continue
                        val name = kp.name
                        if (name != null && bindingResult.provided.contains(name)) {
                            callByMap[kp] = bindingResult.values[name]
                        } else {
                            val javaParam = promptRegistry.method.parameters.firstOrNull { it.name == name }
                            if (javaParam != null && javaParam.getAnnotation(McpContext::class.java) != null) {
                                callByMap[kp] = bindingResult.context
                            }
                        }
                    }

                    kotlinFn.isAccessible = true
                    kotlinFn.callBy(callByMap)
                } else {
                    val javaArgs = promptRegistry.method.parameters.map { p -> bindingResult.values[p.name] }
                    promptRegistry.method.invoke(promptRegistry.bean, *javaArgs.toTypedArray())
                }
            } catch (e: Exception) {
                logger.error("Prompt invocation failed: {}", promptName, e)
                return error(
                    request.id,
                    McpErrorCode.TOOL_EXECUTION_FAILED,
                    "Prompt execution failed: ${e.cause?.message ?: e.message}"
                )
            }

            logger.debug("Prompt invocation succeeded: {}", promptName)

            // Return result
            JsonRpcResponse(
                id = request.id,
                result = mapOf(
                    "messages" to listOf(
                        mapOf(
                            "role" to "user",
                            "content" to result?.toString()
                        )
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling prompt get", e)
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

