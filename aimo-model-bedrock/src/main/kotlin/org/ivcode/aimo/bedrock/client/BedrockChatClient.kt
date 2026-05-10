package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.core.document.Document
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest as BedrockConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse as BedrockConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.Message as BedrockMessage
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock as BedrockContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock as BedrockToolResultContentBlock
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

internal class BedrockChatClient(
    val region: String = "us-east-1",
    val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    val client: BedrockRuntimeClient = BedrockRuntimeClient.builder()
        .region(software.amazon.awssdk.regions.Region.of(region))
        .build()

    fun converse(modelId: String, request: ConverseRequest): ConverseResponse {
        val bedrockMessages = request.messages.map { msg ->
            BedrockMessage.builder()
                .role(software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.fromValue(msg.role))
                .content(msg.content.map { cb ->
                    when {
                        cb.text != null -> BedrockContentBlock.builder()
                            .text(cb.text)
                            .build()
                        cb.toolUse != null -> BedrockContentBlock.builder()
                            .toolUse(software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock.builder()
                                .toolUseId(cb.toolUse.toolUseId)
                                .name(cb.toolUse.name)
                                .input(anyToDocument(cb.toolUse.input))
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
                                        .json(anyToDocument(tool.toolSpec.inputSchema.json))
                                        .build()
                                )
                            }.build()
                        )
                        .build()
                })
                .build()
        }

        val bedrockRequest = BedrockConverseRequest.builder()
            .modelId(modelId)
            .messages(bedrockMessages)
            .system(bedrockSystemPrompt)
            .inferenceConfig(bedrockInferenceConfig)
            .toolConfig(bedrockToolConfig)
            .build()

        return try {
            val response = client.converse(bedrockRequest)
            mapBedrockResponse(response)
        } catch (e: Exception) {
            throw IllegalStateException("Bedrock chat request failed: ${e.message}", e)
        }
    }

    private fun mapBedrockResponse(response: BedrockConverseResponse): ConverseResponse {
        val message = ConverseMessage(
            role = response.output().message().role().toString().lowercase(),
            content = response.output().message().content().map { cb ->
                when {
                    cb.text() != null -> ContentBlock(text = cb.text())
                    cb.toolUse() != null -> ContentBlock(toolUse = ToolUse(
                        toolUseId = cb.toolUse().toolUseId(),
                        name = cb.toolUse().name(),
                        input = documentToMap(cb.toolUse().input())
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

    private fun documentToMap(document: Document): Map<String, Any?> {
        val unwrapped = unwrapDocument(document)
        @Suppress("UNCHECKED_CAST")
        return when (unwrapped) {
            is Map<*, *> -> unwrapped.entries.associate { (k, v) -> k.toString() to v }
            else -> mapOf("raw" to unwrapped)
        }
    }

    private fun anyToDocument(value: Any?): Document = when (value) {
        null -> Document.fromNull()
        is Document -> value
        is String -> Document.fromString(value)
        is Boolean -> Document.fromBoolean(value)
        is Int -> Document.fromNumber(value)
        is Long -> Document.fromNumber(value)
        is Float -> Document.fromNumber(value)
        is Double -> Document.fromNumber(value)
        is Number -> Document.fromNumber(value.toDouble())
        is Map<*, *> -> Document.fromMap(value.entries.associate { (k, v) -> k.toString() to anyToDocument(v) })
        is Iterable<*> -> Document.fromList(value.map { anyToDocument(it) })
        is Array<*> -> Document.fromList(value.map { anyToDocument(it) })
        else -> Document.fromString(value.toString())
    }

    private fun unwrapDocument(document: Document): Any? = when {
        document.isNull -> null
        document.isString -> document.asString()
        document.isBoolean -> document.asBoolean()
        document.isNumber -> document.asNumber().toDouble()
        document.isMap -> document.asMap().mapValues { (_, v) -> unwrapDocument(v) }
        document.isList -> document.asList().map { unwrapDocument(it) }
        else -> document.unwrap()
    }
}

internal typealias ChatCallback = (ConverseResponse) -> Unit



