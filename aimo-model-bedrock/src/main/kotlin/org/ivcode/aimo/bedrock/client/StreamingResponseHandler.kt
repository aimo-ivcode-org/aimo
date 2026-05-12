package org.ivcode.aimo.bedrock.client

import org.slf4j.Logger
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler
import tools.jackson.databind.ObjectMapper
import org.ivcode.aimo.bedrock.client.transformer.MessageTransformerRegistry

/**
 * Handles Bedrock streaming events, accumulates chunks, and emits callbacks.
 */
internal class StreamingResponseHandler(
    private val modelId: String,
    private val log: Logger,
    private val mapper: ObjectMapper,
) {
    private val textBuilder = StringBuilder()
    private val reasoningBuilder = StringBuilder()
    private val transformer = MessageTransformerRegistry.create(modelId)
    private val streamedToolUses = mutableListOf<ToolUse>()
    private val toolUseStatesByIndex = mutableMapOf<Int, ToolUseState>()

    private var role = "assistant"
    private var stopReason = "end_turn"
    private var usage = Usage(inputTokens = 0, outputTokens = 0)

    fun build(callback: ChatCallback): ConverseStreamResponseHandler {
        return ConverseStreamResponseHandler.builder()
            .subscriber(
                ConverseStreamResponseHandler.Visitor.builder()
                    .onMessageStart { event ->
                        role = event.roleAsString().lowercase()
                        if (log.isTraceEnabled) {
                            log.trace("Bedrock stream event onMessageStart modelId={} payload={}", modelId, asLogValue(event))
                        }
                    }
                    .onContentBlockStart { event ->
                        if (log.isTraceEnabled) {
                            log.trace("Bedrock stream event onContentBlockStart modelId={} payload={}", modelId, asLogValue(event))
                        }
                        val index = event.contentBlockIndex()
                        val state = toolUseStatesByIndex.getOrPut(index) { ToolUseState() }
                        state.mergeFrom(TypeExtractors.extractToolUseStart(event.start()))
                    }
                    .onContentBlockDelta { event ->
                        if (log.isTraceEnabled) {
                            log.trace("Bedrock stream event onContentBlockDelta modelId={} payload={}", modelId, asLogValue(event))
                        }
                        val rawText = event.delta().text().orEmpty()
                        val chunk = transformer.consumeChunk(rawText)
                        val text = chunk.text
                        val reasoning = chunk.reasoning.takeIf { it.isNotBlank() }
                            ?: TypeExtractors.extractReasoningText(event.delta())
                        val index = event.contentBlockIndex()
                        val state = toolUseStatesByIndex.getOrPut(index) { ToolUseState() }
                        state.mergeFrom(TypeExtractors.extractToolUseDelta(event.delta()))

                        if (text.isNotEmpty()) {
                            textBuilder.append(text)
                            val callbackPayload = ConverseResponse(
                                output = Output(message = ConverseMessage(role = role, content = listOf(ContentBlock(text = text)))),
                                stopReason = "streaming",
                                usage = usage,
                            )
                            if (log.isTraceEnabled) {
                                log.trace("Bedrock stream callback text chunk modelId={} payload={}", modelId, asLogValue(callbackPayload))
                            }
                            callback(callbackPayload)
                        }
                        if (!reasoning.isNullOrBlank()) {
                            reasoningBuilder.append(reasoning)
                            val callbackPayload = ConverseResponse(
                                output = Output(message = ConverseMessage(role = role, content = listOf(ContentBlock(reasoning = reasoning)))),
                                stopReason = "streaming",
                                usage = usage,
                            )
                            if (log.isTraceEnabled) {
                                log.trace("Bedrock stream callback reasoning chunk modelId={} payload={}", modelId, asLogValue(callbackPayload))
                            }
                            callback(callbackPayload)
                        }
                    }
                    .onContentBlockStop { event ->
                        if (log.isTraceEnabled) {
                            log.trace("Bedrock stream event onContentBlockStop modelId={} payload={}", modelId, asLogValue(event))
                        }
                        val index = event.contentBlockIndex()
                        val finalized = toolUseStatesByIndex.remove(index)?.toToolUse(mapper)
                        if (finalized != null) {
                            streamedToolUses += finalized
                            val callbackPayload = ConverseResponse(
                                output = Output(message = ConverseMessage(role = role, content = listOf(ContentBlock(toolUse = finalized)))),
                                stopReason = "streaming",
                                usage = usage,
                            )
                            if (log.isTraceEnabled) {
                                log.trace("Bedrock stream callback toolUse chunk modelId={} payload={}", modelId, asLogValue(callbackPayload))
                            }
                            callback(callbackPayload)
                        }
                    }
                    .onMessageStop { event ->
                        stopReason = event.stopReasonAsString().lowercase()
                        if (log.isTraceEnabled) {
                            log.trace("Bedrock stream event onMessageStop modelId={} payload={}", modelId, asLogValue(event))
                        }
                    }
                    .onMetadata { event ->
                        val metaUsage = event.usage()
                        usage = Usage(
                            inputTokens = metaUsage?.inputTokens() ?: usage.inputTokens,
                            outputTokens = metaUsage?.outputTokens() ?: usage.outputTokens,
                        )
                        if (log.isTraceEnabled) {
                            log.trace("Bedrock stream event onMetadata modelId={} payload={}", modelId, asLogValue(event))
                        }
                    }
                    .build()
            )
            .build()
    }

    fun assembleResponse(): ConverseResponse {
        val finalContent = buildList {
            textBuilder.toString().takeIf { it.isNotBlank() }?.let { add(ContentBlock(text = it)) }
            reasoningBuilder.toString().takeIf { it.isNotBlank() }?.let { add(ContentBlock(reasoning = it)) }
            streamedToolUses.forEach { add(ContentBlock(toolUse = it)) }
        }.ifEmpty { listOf(ContentBlock(text = "")) }

        return ConverseResponse(
            output = Output(message = ConverseMessage(role = role, content = finalContent)),
            stopReason = stopReason,
            usage = usage,
        )
    }

    fun logSummary() {
        log.debug(
            "Bedrock stream response modelId={}, stopReason={}, usageIn={}, usageOut={}, textLen={}, reasoningLen={}, toolUses={}",
            modelId,
            stopReason,
            usage.inputTokens,
            usage.outputTokens,
            textBuilder.length,
            reasoningBuilder.length,
            streamedToolUses.size,
        )
    }

    private fun asLogValue(value: Any?): String {
        return try {
            mapper.writeValueAsString(value)
        } catch (_: Exception) {
            value?.toString() ?: "null"
        }
    }
}


