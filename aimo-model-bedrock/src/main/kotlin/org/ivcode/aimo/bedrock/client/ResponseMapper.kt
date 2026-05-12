package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.core.document.Document
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse as BedrockConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.Message as BedrockMessage
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock as BedrockContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock as BedrockToolResultContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole

/**
 * Maps between Aimo and Bedrock SDK types, and builds Bedrock request fields.
 */
internal object ResponseMapper {

    fun mapBedrockResponse(response: BedrockConverseResponse): ConverseResponse {
        val message = ConverseMessage(
            role = response.output().message().role().toString().lowercase(),
            content = response.output().message().content().map { cb ->
                when {
                    cb.text() != null -> ContentBlock(text = cb.text())
                    TypeExtractors.extractReasoningText(cb) != null ->
                        ContentBlock(reasoning = TypeExtractors.extractReasoningText(cb))
                    cb.toolUse() != null -> ContentBlock(toolUse = ToolUse(
                        toolUseId = cb.toolUse().toolUseId(),
                        name = cb.toolUse().name(),
                        input = DocumentConverter.documentToMap(cb.toolUse().input())
                    ))
                    cb.toolResult() != null -> ContentBlock(toolResult = ToolResult(
                        toolUseId = cb.toolResult().toolUseId(),
                        content = cb.toolResult().content().map { tc ->
                            ContentBlock(text = tc.text())
                        }
                    ))
                    else -> ContentBlock(text = "")
                }
            }
        )

        return ConverseResponse(
            output = Output(message = message),
            stopReason = response.stopReason().toString(),
            usage = Usage(
                inputTokens = response.usage().inputTokens(),
                outputTokens = response.usage().outputTokens()
            )
        )
    }

    fun toBedrockFields(request: ConverseRequest): BedrockRequestFields {
        val bedrockMessages = request.messages.map { msg ->
            BedrockMessage.builder()
                .role(stringToBedrockRole(msg.role))
                .content(msg.content.map { cb ->
                    when {
                        cb.text != null -> BedrockContentBlock.builder()
                            .text(cb.text)
                            .build()
                        cb.toolUse != null -> BedrockContentBlock.builder()
                            .toolUse(software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock.builder()
                                .toolUseId(cb.toolUse.toolUseId)
                                .name(cb.toolUse.name)
                                .input(DocumentConverter.anyToDocument(cb.toolUse.input))
                                .build())
                            .build()
                        cb.toolResult != null -> BedrockContentBlock.builder()
                            .toolResult(software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock.builder()
                                .toolUseId(cb.toolResult.toolUseId)
                                .content(cb.toolResult.content.map { tc ->
                                    BedrockToolResultContentBlock.builder()
                                        .text(tc.text.orEmpty())
                                        .build()
                                })
                                .build())
                            .build()
                        else -> throw IllegalStateException("Unknown content block type")
                    }
                })
                .build()
        }

        val bedrockSystemPrompt = request.system?.map { sys ->
            software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock.builder()
                .text(sys.text)
                .build()
        }

        val bedrockInferenceConfig = request.inferenceConfig?.let { inf ->
            software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration.builder().apply {
                inf.maxTokens?.let { maxTokens(it) }
                inf.temperature?.let { temperature(it.toFloat()) }
                inf.topP?.let { topP(it.toFloat()) }
                inf.stopSequences?.let { stopSequences(it) }
            }.build()
        }

        val bedrockToolConfig = request.toolConfig?.let { tc ->
            software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration.builder()
                .tools(tc.tools.map { tool ->
                    software.amazon.awssdk.services.bedrockruntime.model.Tool.builder()
                        .toolSpec(
                            software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification.builder().apply {
                                name(tool.toolSpec.name)
                                tool.toolSpec.description?.let { description(it) }
                                inputSchema(
                                    software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema.builder()
                                        .json(DocumentConverter.anyToDocument(tool.toolSpec.inputSchema.json))
                                        .build()
                                )
                            }.build()
                        )
                        .build()
                })
                .build()
        }

        return BedrockRequestFields(
            messages = bedrockMessages,
            system = bedrockSystemPrompt,
            inferenceConfig = bedrockInferenceConfig,
            toolConfig = bedrockToolConfig,
            additionalModelRequestFields = request.additionalModelRequestFields?.let { DocumentConverter.anyToDocument(it) },
        )
    }

    private fun stringToBedrockRole(role: String): ConversationRole = when (role.lowercase()) {
        "user" -> ConversationRole.USER
        "assistant" -> ConversationRole.ASSISTANT
        else -> throw IllegalArgumentException(
            "Unsupported Bedrock message role '$role'. Allowed roles are: user, assistant"
        )
    }
}

internal data class BedrockRequestFields(
    val messages: List<BedrockMessage>,
    val system: List<software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock>?,
    val inferenceConfig: software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration?,
    val toolConfig: software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration?,
    val additionalModelRequestFields: Document?,
)

