package org.ivcode.aimo.server.mcp.handler

import org.ivcode.aimo.server.mcp.protocol.JsonRpcError
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Handles resource-related requests.
 *
 * Phase 2 stub: Returns "not implemented" for resource operations.
 * Full implementation in Phase 3.
 */
@Component
class ResourceHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Handle resources/list request.
     */
    fun handleResourcesList(request: JsonRpcRequest): JsonRpcResponse {
        logger.debug("resources/list is not yet implemented (Phase 2 stub)")
        return JsonRpcResponse(
            id = request.id,
            result = mapOf("resources" to emptyList<Any>())
        )
    }

    /**
     * Handle resources/read request.
     */
    fun handleResourcesRead(request: JsonRpcRequest): JsonRpcResponse {
        logger.debug("resources/read is not yet implemented (Phase 2 stub)")
        return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = McpErrorCode.METHOD_NOT_FOUND,
                message = "resources/read is not yet implemented"
            )
        )
    }
}

