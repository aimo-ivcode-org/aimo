package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.core.document.Document
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse as BedrockConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.Message as BedrockMessage
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock as BedrockContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock as BedrockToolResultContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock as BedrockSystemContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration as BedrockToolConfiguration
import software.amazon.awssdk.services.bedrockruntime.model.Tool as BedrockTool
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration as BedrockInferenceConfiguration
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole

/**
 * Maps between Aimo and Bedrock SDK types, and builds Bedrock request fields.
 */
internal object ResponseMapper {

    fun mapBedrockResponse(response: BedrockConverseResponse): ConverseResponse {
        val message = ConverseMessage(
            role = response.output().message().role().toString().lowercase(),
            content = response.output().message().content().map { cb ->
                val reasoning = TypeExtractors.extractReasoningText(cb)
                when {
                    cb.text() != null -> ContentBlock(text = cb.text())
                    reasoning != null -> ContentBlock(reasoning = reasoning)
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

        val stopReason = response.stopReasonAsString()?.lowercase()?.ifBlank { null } ?: "end_turn"
        val sdkUsage = response.usage()

        // Only create Usage when SDK provides it
        val usage = if (sdkUsage != null) {
            Usage(
                inputTokens = sdkUsage.inputTokens(),
                outputTokens = sdkUsage.outputTokens(),
                cacheReadInputTokens = sdkUsage.cacheReadInputTokens() ?: 0,
                cacheWriteInputTokens = sdkUsage.cacheWriteInputTokens() ?: 0,
            )
        } else {
            null
        }

        return ConverseResponse(
            output = Output(message = message),
            stopReason = stopReason,
            usage = usage,
        )
    }

    fun toBedrockFields(request: ConverseRequest): BedrockRequestFields {
        val bedrockMessages: List<BedrockMessage> = request.messages.map { msg -> mapMessage(msg) }
        val bedrockSystemPrompt = request.system?.let { mapSystemBlocks(it, request.cachePointAfterSystem) }
        val bedrockInferenceConfig = request.inferenceConfig?.let(::mapInferenceConfig)
        val bedrockToolConfig = request.toolConfig?.let { mapToolConfiguration(it, request.cachePointAfterTools) }

        return BedrockRequestFields(
            messages = bedrockMessages,
            system = bedrockSystemPrompt,
            inferenceConfig = bedrockInferenceConfig,
            toolConfig = bedrockToolConfig,
            additionalModelRequestFields = mergeAdditionalModelRequestFields(
                request.additionalModelRequestFields,
                request.inferenceConfig?.topK,
            ),
        )
    }

    private val mapMessage: (ConverseMessage) -> BedrockMessage = { msg ->
        BedrockMessage.builder()
            .role(stringToBedrockRole(msg.role))
            .content(msg.content.map { mapContentBlock(it) })
            .build()
    }

    private val mapContentBlock: (ContentBlock) -> BedrockContentBlock = { cb ->
        when {
            cb.text != null -> BedrockContentBlock.builder()
                .text(cb.text)
                .build()

            cb.toolUse != null -> BedrockContentBlock.builder()
                .toolUse(
                    software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock.builder()
                        .toolUseId(cb.toolUse.toolUseId)
                        .name(cb.toolUse.name)
                        .input(DocumentConverter.anyToDocument(cb.toolUse.input))
                        .build(),
                )
                .build()

            cb.toolResult != null -> BedrockContentBlock.builder()
                .toolResult(
                    software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock.builder()
                        .toolUseId(cb.toolResult.toolUseId)
                        .content(
                            cb.toolResult.content.map { tc ->
                                BedrockToolResultContentBlock.builder()
                                    .text(tc.text.orEmpty())
                                    .build()
                            },
                        )
                        .build(),
                )
                .build()

            else -> throw IllegalStateException("Unknown content block type")
        }
    }

    private val mapSystemBlocks: (List<SystemContentBlock>, Boolean) -> List<BedrockSystemContentBlock> =
        { systemBlocks, cachePointAfterSystem ->
            val mappedBlocks = systemBlocks.map { mapSystemBlock(it) }
            if (cachePointAfterSystem && mappedBlocks.isNotEmpty()) {
                mappedBlocks + buildSystemCachePoint()
            } else {
                mappedBlocks
            }
        }

    private fun mapSystemBlock(
        systemBlock: SystemContentBlock,
    ): BedrockSystemContentBlock =
        BedrockSystemContentBlock.builder()
            .text(systemBlock.text)
            .build()

    private fun buildSystemCachePoint(): BedrockSystemContentBlock =
        BedrockSystemContentBlock.builder()
            .cachePoint(
                software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock.builder()
                    .type(software.amazon.awssdk.services.bedrockruntime.model.CachePointType.DEFAULT)
                    .build(),
            )
            .build()

    private fun mapInferenceConfig(
        inferenceConfig: InferenceConfiguration,
    ): BedrockInferenceConfiguration =
        BedrockInferenceConfiguration.builder().apply {
            inferenceConfig.maxTokens?.let { maxTokens(it) }
            inferenceConfig.temperature?.let { temperature(it.toFloat()) }
            inferenceConfig.topP?.let { topP(it.toFloat()) }
            inferenceConfig.stopSequences?.let { stopSequences(it) }
        }.build()

    private fun mapToolConfiguration(
        toolConfig: ToolConfiguration,
        cachePointAfterTools: Boolean,
    ): BedrockToolConfiguration {
        val mappedTools = toolConfig.tools.map { mapTool(it) }
        val toolsWithCachePoint = if (cachePointAfterTools && mappedTools.isNotEmpty()) {
            mappedTools + buildToolCachePoint()
        } else {
            mappedTools
        }

        return BedrockToolConfiguration.builder()
            .tools(toolsWithCachePoint)
            .build()
    }

    private fun mapTool(tool: Tool): BedrockTool =
        BedrockTool.builder()
            .toolSpec(
                software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification.builder().apply {
                    name(tool.toolSpec.name)
                    tool.toolSpec.description?.let { description(it) }
                    inputSchema(
                        software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema.builder()
                            .json(DocumentConverter.anyToDocument(tool.toolSpec.inputSchema.json))
                            .build(),
                    )
                }.build(),
            )
            .build()

    private fun buildToolCachePoint(): BedrockTool =
        BedrockTool.builder()
            .cachePoint(
                software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock.builder()
                    .type(software.amazon.awssdk.services.bedrockruntime.model.CachePointType.DEFAULT)
                    .build(),
            )
            .build()

    private fun mergeAdditionalModelRequestFields(
        existing: Map<String, Any?>?,
        topK: Int?,
    ): Document? {
        if (existing.isNullOrEmpty() && topK == null) return null

        val merged = LinkedHashMap<String, Any?>()
        existing?.let { merged.putAll(it) }
        if (topK != null && merged.keys.none { it.isTopKKey() }) {
            merged["top_k"] = topK
        }
        return DocumentConverter.anyToDocument(merged)
    }

    private fun String.isTopKKey(): Boolean =
        lowercase().replace("_", "").replace("-", "") == "topk"

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
