package org.ivcode.aimo.bedrock.client

import org.slf4j.Logger
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent
import java.io.IOException
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

    fun build(callback: ChatCallback): ConverseStreamResponseHandler =
        ConverseStreamResponseHandler.builder()
            .subscriber(
                ConverseStreamResponseHandler.Visitor.builder()
                    .onMessageStart { event -> handleMessageStart(event) }
                    .onContentBlockStart { event -> handleContentBlockStart(event) }
                    .onContentBlockDelta { event -> handleContentBlockDelta(event, callback) }
                    .onContentBlockStop { event -> handleContentBlockStop(event, callback) }
                    .onMessageStop { event -> handleMessageStop(event) }
                    .onMetadata { event -> handleMetadata(event) }
                    .build()
            )
            .build()

    private fun handleMessageStart(event: MessageStartEvent) {
        role = event.roleAsString().lowercase()
        traceEvent("onMessageStart", event)
    }

    private fun handleContentBlockStart(event: ContentBlockStartEvent) {
        traceEvent("onContentBlockStart", event)

        val state = toolUseStatesByIndex.getOrPut(event.contentBlockIndex()) { ToolUseState() }
        state.mergeFrom(TypeExtractors.extractToolUseStart(event.start()))
    }

    private fun handleContentBlockDelta(event: ContentBlockDeltaEvent, callback: ChatCallback) {
        traceEvent("onContentBlockDelta", event)

        val rawText = event.delta().text().orEmpty()
        val chunk = transformer.consumeChunk(rawText)
        val reasoning = chunk.reasoning.takeIf { it.isNotBlank() }
            ?: TypeExtractors.extractReasoningText(event.delta())
        val state = toolUseStatesByIndex.getOrPut(event.contentBlockIndex()) { ToolUseState() }
        state.mergeFrom(TypeExtractors.extractToolUseDelta(event.delta()))

        emitTextChunk(chunk.text, callback)
        emitReasoningChunk(reasoning, callback)
    }

    private fun handleContentBlockStop(event: ContentBlockStopEvent, callback: ChatCallback) {
        traceEvent("onContentBlockStop", event)

        val finalized = toolUseStatesByIndex.remove(event.contentBlockIndex())?.toToolUse(mapper)
        if (finalized != null) {
            streamedToolUses += finalized
            emitToolUseChunk(finalized, callback)
        }
    }

    private fun handleMessageStop(event: MessageStopEvent) {
        stopReason = event.stopReasonAsString().lowercase()
        traceEvent("onMessageStop", event)
    }

    private fun handleMetadata(event: Any) {
        val metaUsage = TypeExtractors.invokeNoArg(event, "usage") ?: return
        usage = Usage(
            inputTokens = TypeExtractors.invokeNoArg(metaUsage, "inputTokens") as? Int
                ?: usage.inputTokens,
            outputTokens = TypeExtractors.invokeNoArg(metaUsage, "outputTokens") as? Int
                ?: usage.outputTokens,
            cacheReadInputTokens = TypeExtractors.invokeNoArg(metaUsage, "cacheReadInputTokens") as? Int
                ?: usage.cacheReadInputTokens,
            cacheWriteInputTokens = TypeExtractors.invokeNoArg(metaUsage, "cacheWriteInputTokens") as? Int
                ?: usage.cacheWriteInputTokens,
        )
        traceEvent("onMetadata", event)
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
            "Bedrock stream response modelId={}, stopReason={}, usageIn={}, " +
                "usageOut={}, textLen={}, reasoningLen={}, toolUses={}",
            modelId,
            stopReason,
            usage.inputTokens,
            usage.outputTokens,
            textBuilder.length,
            reasoningBuilder.length,
            streamedToolUses.size,
        )
    }

    private val emitTextChunk: (String, ChatCallback) -> Unit = { text, callback ->
        if (text.isNotEmpty()) {
            textBuilder.append(text)
            emitCallback(ContentBlock(text = text), callback)
        }
    }

    private val emitReasoningChunk: (String?, ChatCallback) -> Unit = { reasoning, callback ->
        if (!reasoning.isNullOrBlank()) {
            reasoningBuilder.append(reasoning)
            emitCallback(ContentBlock(reasoning = reasoning), callback)
        }
    }

    private val emitToolUseChunk: (ToolUse, ChatCallback) -> Unit = { toolUse, callback ->
        emitCallback(ContentBlock(toolUse = toolUse), callback)
    }

    private val emitCallback: (ContentBlock, ChatCallback) -> Unit = { contentBlock, callback ->
        val callbackPayload = ConverseResponse(
            output = Output(message = ConverseMessage(role = role, content = listOf(contentBlock))),
            stopReason = "streaming",
            usage = usage,
        )
        if (log.isTraceEnabled) {
            log.trace(
                "Bedrock stream callback chunk modelId={} payload={}",
                modelId,
                asLogValue(callbackPayload),
            )
        }
        callback(callbackPayload)
    }

    private val traceEvent: (String, Any?) -> Unit = { eventName, value ->
        if (log.isTraceEnabled) {
            log.trace(
                "Bedrock stream event {} modelId={} payload={}",
                eventName,
                modelId,
                asLogValue(value),
            )
        }
    }

    private val asLogValue: (Any?) -> String = { value ->
        try {
            mapper.writeValueAsString(value)
        } catch (_: IOException) {
            value?.toString() ?: "null"
        }
    }
}
