package org.ivcode.aimo.mcpclient.protocol.jsonrpc

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode

/**
 * JSON-RPC 2.0 request message.
 *
 * @param jsonrpc must be "2.0"
 * @param method The method name
 * @param params Optional parameters (can be object or array)
 * @param id Request ID for tracking responses (null for notifications)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonNode? = null,
    val id: String? = null,
)

/**
 * JSON-RPC 2.0 response message.
 *
 * @param jsonrpc must be "2.0"
 * @param result The result (present if successful)
 * @param error The error (present if failed)
 * @param id The ID from the request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonNode? = null,
    val error: JsonRpcError? = null,
    val id: String,
)

/**
 * JSON-RPC 2.0 error object.
 *
 * @param code Error code (negative integer)
 * @param message Error message
 * @param data Optional error data
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonNode? = null,
)

/**
 * JSON-RPC 2.0 notification message (no response expected).
 *
 * @param jsonrpc must be "2.0"
 * @param method The method name
 * @param params Optional parameters
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonNode? = null,
)

/**
 * Represents either a request or notification in incoming stream.
 */
sealed class JsonRpcMessage {
    data class Request(val request: JsonRpcRequest) : JsonRpcMessage()
    data class Notification(val notification: JsonRpcNotification) : JsonRpcMessage()
}

/**
 * Standard JSON-RPC 2.0 error codes.
 */
object JsonRpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val SERVER_ERROR_START = -32099
    const val SERVER_ERROR_END = -32000
}
