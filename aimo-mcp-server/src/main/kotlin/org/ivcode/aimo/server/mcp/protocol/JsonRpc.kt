package org.ivcode.aimo.server.mcp.protocol

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Base class for JSON-RPC request/response objects.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class JsonRpcMessage {
    @JsonProperty("jsonrpc")
    val jsonRpc: String = "2.0"
}

/**
 * JSON-RPC request object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcRequest(
    @JsonProperty("id")
    val id: Any? = null,

    @JsonProperty("method")
    val method: String,

    @JsonProperty("params")
    val params: Map<String, Any?>? = null
 ) : JsonRpcMessage() {
    private val additionalProperties = mutableMapOf<String, Any?>()

    @JsonAnySetter
    fun setAdditionalProperty(name: String, value: Any?) {
        additionalProperties[name] = value
    }

    @JsonAnyGetter
    fun getAdditionalProperties(): Map<String, Any?> = additionalProperties
}

/**
 * JSON-RPC response object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    @JsonProperty("id")
    val id: Any? = null,

    @JsonProperty("result")
    val result: Any? = null,

    @JsonProperty("error")
    val error: JsonRpcError? = null
) : JsonRpcMessage()

/**
 * JSON-RPC error object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcError(
    @JsonProperty("code")
    val code: Int,

    @JsonProperty("message")
    val message: String,

    @JsonProperty("data")
    val data: Any? = null
)

// MCP-specific error codes
object McpErrorCode {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val SERVER_ERROR_START = -32099
    const val SERVER_ERROR_END = -32000

    // Custom MCP errors
    const val TOOL_NOT_FOUND = -32099
    const val PROMPT_NOT_FOUND = -32098
    const val INVALID_TOOL_PARAMS = -32097
    const val TOOL_EXECUTION_FAILED = -32096
}

/**
 * MCP Protocol initialization request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InitializeRequest(
    @JsonProperty("protocolVersion")
    val protocolVersion: String,

    @JsonProperty("capabilities")
    val capabilities: Map<String, Any?>,

    @JsonProperty("clientInfo")
    val clientInfo: ClientInfo
)

/**
 * Client information for initialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClientInfo(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("version")
    val version: String
)

/**
 * MCP Protocol initialization response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InitializeResponse(
    @JsonProperty("protocolVersion")
    val protocolVersion: String,

    @JsonProperty("capabilities")
    val capabilities: ServerCapabilities,

    @JsonProperty("serverInfo")
    val serverInfo: ServerInfo
)

/**
 * Server capabilities.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServerCapabilities(
    @JsonProperty("tools")
    val tools: ToolCapability? = null,

    @JsonProperty("prompts")
    val prompts: PromptCapability? = null,

    @JsonProperty("resources")
    val resources: ResourceCapability? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolCapability(
    @JsonProperty("listChanged")
    val listChanged: Boolean? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PromptCapability(
    @JsonProperty("listChanged")
    val listChanged: Boolean? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceCapability(
    @JsonProperty("subscribe")
    val subscribe: Boolean? = null,

    @JsonProperty("listChanged")
    val listChanged: Boolean? = null
)

/**
 * Server information for initialization response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServerInfo(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("version")
    val version: String
)

/**
 * Tool definition in schema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolDefinition(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("inputSchema")
    val inputSchema: ToolInputSchema? = null
)

/**
 * Tool input schema (OpenRPC-compliant).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolInputSchema(
    @JsonProperty("type")
    val type: String = "object",

    @JsonProperty("properties")
    val properties: Map<String, PropertySchema>,

    @JsonProperty("required")
    val required: List<String>? = null
)

/**
 * Property schema for tool parameters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PropertySchema(
    @JsonProperty("type")
    val type: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("items")
    val items: PropertySchema? = null,

    @JsonProperty("enum")
    val enum: List<Any>? = null
)

/**
 * Prompt definition in schema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PromptDefinition(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("arguments")
    val arguments: List<PromptArgument>? = null
)

/**
 * Prompt argument definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PromptArgument(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("required")
    val required: Boolean = false
)

/**
 * Resource definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResourceDefinition(
    @JsonProperty("uri")
    val uri: String,

    @JsonProperty("name")
    val name: String? = null,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("mimeType")
    val mimeType: String? = null
)

