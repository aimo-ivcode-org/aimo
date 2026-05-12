package org.ivcode.aimo.bedrock.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/* References:
 * - https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
 * - https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ConverseRequest(
    val model: String,
    val messages: List<ConverseMessage>,
    val system: List<SystemContentBlock>? = null,
    val inferenceConfig: InferenceConfiguration? = null,
    val toolConfig: ToolConfiguration? = null,
    val additionalModelRequestFields: Map<String, Any?>? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ConverseMessage(
    val role: String,
    val content: List<ContentBlock>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SystemContentBlock(
    val text: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ContentBlock(
    val text: String? = null,
    val reasoning: String? = null,
    @field:JsonProperty("toolUse")
    val toolUse: ToolUse? = null,
    @field:JsonProperty("toolResult")
    val toolResult: ToolResult? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ToolUse(
    val toolUseId: String,
    val name: String,
    val input: Map<String, Any?>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ToolResult(
    val toolUseId: String,
    val content: List<ContentBlock>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class InferenceConfiguration(
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val stopSequences: List<String>? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ToolConfiguration(
    val tools: List<Tool>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Tool(
    val toolSpec: ToolSpec,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ToolSpec(
    val name: String,
    val description: String?,
    val inputSchema: InputSchema,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class InputSchema(
    val json: Map<String, Any?>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ConverseResponse(
    val output: Output,
    val stopReason: String,
    val usage: Usage,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Output(
    val message: ConverseMessage,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Usage(
    val inputTokens: Int,
    val outputTokens: Int,
)

