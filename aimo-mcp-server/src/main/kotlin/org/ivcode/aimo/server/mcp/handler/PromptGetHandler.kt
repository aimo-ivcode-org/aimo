package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.annotation.McpContext
import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Handles prompts/get requests - invokes @McpPrompt methods.
 */
@Component
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
            val boundArgs = parameterBinder.bindParameters(
                method = promptRegistry.method,
                arguments = arguments,
                context = mapOf(
                    "promptName" to promptName,
                    "requestId" to request.id?.toString()
                )
            )

            // Invoke prompt
            val result = try {
                promptRegistry.method.invoke(promptRegistry.bean, *boundArgs.toTypedArray())
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

