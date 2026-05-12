package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient
import software.amazon.awssdk.core.document.Document
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest as BedrockConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse as BedrockConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest as BedrockConverseStreamRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler
import software.amazon.awssdk.services.bedrockruntime.model.Message as BedrockMessage
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock as BedrockContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock as BedrockToolResultContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.ivcode.aimo.bedrock.client.transformer.MessageTransformerRegistry

internal class BedrockChatClient(
    val region: String = "us-east-1",
    val awsAccessKeyId: String? = null,
    val awsSecretAccessKey: String? = null
) {
    private val log = LoggerFactory.getLogger(BedrockChatClient::class.java)
    private val mapper: ObjectMapper = jacksonObjectMapper()

    val client: BedrockRuntimeClient = BedrockRuntimeClient.builder()
        .region(software.amazon.awssdk.regions.Region.of(region))
        .apply {
            if (awsAccessKeyId != null && awsSecretAccessKey != null) {
                credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                    )
                )
            }
        }
        .build()

    val asyncClient: BedrockRuntimeAsyncClient = BedrockRuntimeAsyncClient.builder()
        .region(software.amazon.awssdk.regions.Region.of(region))
        .apply {
            if (awsAccessKeyId != null && awsSecretAccessKey != null) {
                credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                    )
                )
            }
        }
        .build()

    fun converse(modelId: String, request: ConverseRequest): ConverseResponse {
        val fields = request.toBedrockFields()
        log.debug(
            "Bedrock converse request modelId={}, messages={}, system={}, tools={}, additionalFields={}",
            modelId,
            request.messages.size,
            request.system?.size ?: 0,
            request.toolConfig?.tools?.size ?: 0,
            request.additionalModelRequestFields?.isNotEmpty() == true,
        )
        val bedrockRequest = BedrockConverseRequest.builder()
            .modelId(modelId)
            .messages(fields.messages)
            .system(fields.system)
            .inferenceConfig(fields.inferenceConfig)
            .toolConfig(fields.toolConfig)
            .additionalModelRequestFields(fields.additionalModelRequestFields)
            .build()

        if (log.isTraceEnabled) {
            log.trace("Bedrock converse request (AIMO) modelId={} payload={}", modelId, asLogValue(request))
            log.trace("Bedrock converse request (SDK) modelId={} payload={}", modelId, asLogValue(bedrockRequest))
        }

        return try {
            val response = client.converse(bedrockRequest)
            if (log.isTraceEnabled) {
                log.trace("Bedrock converse response (SDK) modelId={} payload={}", modelId, asLogValue(response))
            }
            val mapped = mapBedrockResponse(response)
            if (log.isTraceEnabled) {
                log.trace("Bedrock converse response (AIMO mapped) modelId={} payload={}", modelId, asLogValue(mapped))
            }
            val transformer = MessageTransformerRegistry.create(modelId)
            val transformed = transformer.transformFinalResponse(mapped)
            log.debug(
                "Bedrock converse response modelId={}, stopReason={}, usageIn={}, usageOut={}, contentBlocks={}",
                modelId,
                transformed.stopReason,
                transformed.usage.inputTokens,
                transformed.usage.outputTokens,
                transformed.output.message.content.size,
            )
            if (log.isTraceEnabled) {
                log.trace("Bedrock converse response (AIMO transformed) modelId={} payload={}", modelId, asLogValue(transformed))
            }
            transformed
        } catch (e: Exception) {
            log.error("Bedrock converse failed modelId={}: {}", modelId, e.message, e)
            throw IllegalStateException("Bedrock chat request failed: ${e.message}", e)
        }
    }

    fun converseStream(modelId: String, request: ConverseRequest, callback: ChatCallback): ConverseResponse {
        val textBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val transformer = MessageTransformerRegistry.create(modelId)
        val streamedToolUses = mutableListOf<ToolUse>()
        val toolUseStatesByIndex = mutableMapOf<Int, ToolUseState>()
        var role = "assistant"
        var stopReason = "end_turn"
        var usage = Usage(inputTokens = 0, outputTokens = 0)

        log.debug(
            "Bedrock stream request modelId={}, messages={}, system={}, tools={}, additionalFields={}",
            modelId,
            request.messages.size,
            request.system?.size ?: 0,
            request.toolConfig?.tools?.size ?: 0,
            request.additionalModelRequestFields?.isNotEmpty() == true,
        )

        val fields = request.toBedrockFields()
        val streamRequest = BedrockConverseStreamRequest.builder()
            .modelId(modelId)
            .messages(fields.messages)
            .system(fields.system)
            .inferenceConfig(fields.inferenceConfig)
            .toolConfig(fields.toolConfig)
            .additionalModelRequestFields(fields.additionalModelRequestFields)
            .build()

        if (log.isTraceEnabled) {
            log.trace("Bedrock stream request (AIMO) modelId={} payload={}", modelId, asLogValue(request))
            log.trace("Bedrock stream request (SDK) modelId={} payload={}", modelId, asLogValue(streamRequest))
        }

        val handler = ConverseStreamResponseHandler.builder()
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
                        state.mergeFrom(extractToolUseStart(event.start()))
                    }
                    .onContentBlockDelta { event ->
                        if (log.isTraceEnabled) {
                            log.trace("Bedrock stream event onContentBlockDelta modelId={} payload={}", modelId, asLogValue(event))
                        }
                        val rawText = event.delta().text().orEmpty()
                        val chunk = transformer.consumeChunk(rawText)
                        val text = chunk.text
                        val reasoning = chunk.reasoning.takeIf { it.isNotBlank() }
                            ?: extractReasoningText(event.delta())
                        val index = event.contentBlockIndex()
                        val state = toolUseStatesByIndex.getOrPut(index) { ToolUseState() }
                        state.mergeFrom(extractToolUseDelta(event.delta()))
                        if (text.isNotEmpty()) {
                            textBuilder.append(text)
                            val callbackPayload = ConverseResponse(
                                output = Output(
                                    message = ConverseMessage(
                                        role = role,
                                        content = listOf(ContentBlock(text = text)),
                                    )
                                ),
                                stopReason = "streaming",
                                usage = usage,
                            )
                            if (log.isTraceEnabled) {
                                log.trace(
                                    "Bedrock stream callback text chunk modelId={} payload={}",
                                    modelId,
                                    asLogValue(callbackPayload),
                                )
                            }
                            callback(callbackPayload)
                        }
                        if (!reasoning.isNullOrBlank()) {
                            reasoningBuilder.append(reasoning)
                            val callbackPayload = ConverseResponse(
                                output = Output(
                                    message = ConverseMessage(
                                        role = role,
                                        content = listOf(ContentBlock(reasoning = reasoning)),
                                    )
                                ),
                                stopReason = "streaming",
                                usage = usage,
                            )
                            if (log.isTraceEnabled) {
                                log.trace(
                                    "Bedrock stream callback reasoning chunk modelId={} payload={}",
                                    modelId,
                                    asLogValue(callbackPayload),
                                )
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
                                output = Output(
                                    message = ConverseMessage(
                                        role = role,
                                        content = listOf(ContentBlock(toolUse = finalized)),
                                    )
                                ),
                                stopReason = "streaming",
                                usage = usage,
                            )
                            if (log.isTraceEnabled) {
                                log.trace(
                                    "Bedrock stream callback toolUse chunk modelId={} payload={}",
                                    modelId,
                                    asLogValue(callbackPayload),
                                )
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

        return try {
            asyncClient.converseStream(streamRequest, handler).get()
            val finalContent = buildList {
                textBuilder.toString().takeIf { it.isNotBlank() }?.let { add(ContentBlock(text = it)) }
                reasoningBuilder.toString().takeIf { it.isNotBlank() }?.let { add(ContentBlock(reasoning = it)) }
                streamedToolUses.forEach { add(ContentBlock(toolUse = it)) }
            }.ifEmpty { listOf(ContentBlock(text = "")) }

            val assembled = ConverseResponse(
                output = Output(message = ConverseMessage(role = role, content = finalContent)),
                stopReason = stopReason,
                usage = usage,
            )
            if (log.isTraceEnabled) {
                log.trace("Bedrock stream response (AIMO assembled) modelId={} payload={}", modelId, asLogValue(assembled))
            }
            val transformed = transformer.transformFinalResponse(assembled)
            log.debug(
                "Bedrock stream response modelId={}, stopReason={}, usageIn={}, usageOut={}, textLen={}, reasoningLen={}, toolUses={}",
                modelId,
                transformed.stopReason,
                transformed.usage.inputTokens,
                transformed.usage.outputTokens,
                transformed.output.message.content.sumOf { it.text?.length ?: 0 },
                transformed.output.message.content.sumOf { it.reasoning?.length ?: 0 },
                transformed.output.message.content.count { it.toolUse != null },
            )
            if (log.isTraceEnabled) {
                log.trace("Bedrock stream response (AIMO transformed) modelId={} payload={}", modelId, asLogValue(transformed))
            }
            transformed
        } catch (e: Exception) {
            log.error("Bedrock stream failed modelId={}: {}", modelId, e.message, e)
            throw IllegalStateException("Bedrock stream request failed: ${e.message}", e)
        }
    }

    private fun mapBedrockResponse(response: BedrockConverseResponse): ConverseResponse {
        val message = ConverseMessage(
            role = response.output().message().role().toString().lowercase(),
            content = response.output().message().content().map { cb ->
                when {
                    cb.text() != null -> ContentBlock(text = cb.text())
                    extractReasoningText(cb) != null -> ContentBlock(reasoning = extractReasoningText(cb))
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

    private fun String.toBedrockRole(): ConversationRole = when (lowercase()) {
        "user" -> ConversationRole.USER
        "assistant" -> ConversationRole.ASSISTANT
        else -> throw IllegalArgumentException(
            "Unsupported Bedrock message role '$this'. Allowed roles are: user, assistant"
        )
    }

    private fun extractReasoningText(source: Any?): String? {
        if (source == null) return null

        val reasoning = invokeNoArg(source, "reasoningContent")
            ?: invokeNoArg(source, "reasoning")
            ?: return null

        return invokeNoArg(reasoning, "text") as? String
            ?: (invokeNoArg(reasoning, "reasoningText")?.let { invokeNoArg(it, "text") as? String })
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return try {
            target.javaClass.methods
                .firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?.invoke(target)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractToolUseStart(source: Any?): ToolUsePartial? {
        if (source == null) return null
        val toolUse = invokeNoArg(source, "toolUse") ?: return null
        return ToolUsePartial(
            toolUseId = invokeNoArg(toolUse, "toolUseId") as? String,
            name = invokeNoArg(toolUse, "name") as? String,
            inputChunk = invokeNoArg(toolUse, "input") as? String,
            inputDocument = invokeNoArg(toolUse, "input") as? Document,
        )
    }

    private fun extractToolUseDelta(source: Any?): ToolUsePartial? {
        if (source == null) return null
        val toolUse = invokeNoArg(source, "toolUse") ?: return null
        return ToolUsePartial(
            toolUseId = invokeNoArg(toolUse, "toolUseId") as? String,
            name = invokeNoArg(toolUse, "name") as? String,
            inputChunk = invokeNoArg(toolUse, "input") as? String,
            inputDocument = invokeNoArg(toolUse, "input") as? Document,
        )
    }

    private data class ToolUsePartial(
        val toolUseId: String? = null,
        val name: String? = null,
        val inputChunk: String? = null,
        val inputDocument: Document? = null,
    )

    private class ToolUseState {
        var toolUseId: String? = null
        var name: String? = null
        val inputChunks: StringBuilder = StringBuilder()
        var inputDocument: Document? = null

        fun mergeFrom(partial: ToolUsePartial?) {
            if (partial == null) return
            if (!partial.toolUseId.isNullOrBlank()) toolUseId = partial.toolUseId
            if (!partial.name.isNullOrBlank()) name = partial.name
            if (!partial.inputChunk.isNullOrEmpty()) inputChunks.append(partial.inputChunk)
            if (partial.inputDocument != null) inputDocument = partial.inputDocument
        }

        fun toToolUse(mapper: ObjectMapper): ToolUse? {
            val resolvedId = toolUseId?.takeIf { it.isNotBlank() } ?: return null
            val resolvedName = name?.takeIf { it.isNotBlank() } ?: return null
            val input = when {
                inputDocument != null -> {
                    val unwrapped = inputDocument!!.unwrap()
                    @Suppress("UNCHECKED_CAST")
                    (unwrapped as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
                }
                inputChunks.isNotBlank() -> parseToolInput(mapper, inputChunks.toString())
                else -> emptyMap()
            }

            return ToolUse(
                toolUseId = resolvedId,
                name = resolvedName,
                input = input,
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun parseToolInput(mapper: ObjectMapper, raw: String): Map<String, Any?> {
            return try {
                mapper.readValue(raw, Map::class.java) as? Map<String, Any?> ?: mapOf("raw" to raw)
            } catch (_: Exception) {
                mapOf("raw" to raw)
            }
        }
    }

    private fun ConverseRequest.toBedrockFields(): BedrockRequestFields {
        val bedrockMessages = messages.map { msg ->
            BedrockMessage.builder()
                .role(msg.role.toBedrockRole())
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

        val bedrockSystemPrompt = system?.map { sys ->
            software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock.builder()
                .text(sys.text)
                .build()
        }

        val bedrockInferenceConfig = inferenceConfig?.let { inf ->
            software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration.builder().apply {
                inf.maxTokens?.let { maxTokens(it) }
                inf.temperature?.let { temperature(it.toFloat()) }
                inf.topP?.let { topP(it.toFloat()) }
                inf.stopSequences?.let { stopSequences(it) }
            }.build()
        }

        val bedrockToolConfig = toolConfig?.let { tc ->
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

        return BedrockRequestFields(
            messages = bedrockMessages,
            system = bedrockSystemPrompt,
            inferenceConfig = bedrockInferenceConfig,
            toolConfig = bedrockToolConfig,
            additionalModelRequestFields = additionalModelRequestFields?.let { anyToDocument(it) },
        )
    }

    private data class BedrockRequestFields(
        val messages: List<BedrockMessage>,
        val system: List<software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock>?,
        val inferenceConfig: software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration?,
        val toolConfig: software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration?,
        val additionalModelRequestFields: Document?,
    )

    private fun asLogValue(value: Any?): String {
        return try {
            mapper.writeValueAsString(value)
        } catch (_: Exception) {
            value?.toString() ?: "null"
        }
    }

    /**
     * Removes model-side control tags (for example DeepSeek's DSML function_call marker)
     * from visible assistant text. Handles both one-shot and chunk-split tags.
     */
    private class ModelControlTextFilter {
        private val markers = listOf(
            "<|DSML|function_calls",
            "<\uFF5CDSML\uFF5Cfunction_calls",
        )
        private var droppingUntilGt = false
        private var carry = ""

        fun consume(input: String): String {
            if (input.isEmpty() && carry.isEmpty()) return input

            var text = carry + input
            carry = ""

            if (droppingUntilGt) {
                val end = text.indexOf('>')
                if (end < 0) return ""
                droppingUntilGt = false
                text = text.substring(end + 1)
            }

            val out = StringBuilder()
            var index = 0
            while (index < text.length) {
                val markerMatch = findNextMarker(text, index)
                if (markerMatch == null) {
                    val tail = text.substring(index)
                    val split = splitCarryTail(tail)
                    out.append(split.visible)
                    carry = split.carry
                    break
                }

                out.append(text.substring(index, markerMatch.position))
                var afterMarker = markerMatch.position + markerMatch.marker.length

                if (afterMarker < text.length && text[afterMarker] == '>') {
                    afterMarker += 1
                    index = afterMarker
                    continue
                }

                val gt = text.indexOf('>', afterMarker)
                if (gt >= 0) {
                    index = gt + 1
                    continue
                }

                droppingUntilGt = true
                index = text.length
            }

            return out.toString()
        }

        private fun findNextMarker(text: String, start: Int): MarkerMatch? {
            var best: MarkerMatch? = null
            for (marker in markers) {
                val position = text.indexOf(marker, start)
                if (position >= 0 && (best == null || position < best.position)) {
                    best = MarkerMatch(position, marker)
                }
            }
            return best
        }

        private fun splitCarryTail(tail: String): VisibleCarry {
            if (tail.isEmpty()) return VisibleCarry("", "")
            val maxMarkerLen = markers.maxOf { it.length }
            val window = tail.takeLast(maxMarkerLen.coerceAtMost(tail.length))
            val carryLen = longestSuffixPrefix(window)
            return if (carryLen == 0) {
                VisibleCarry(tail, "")
            } else {
                val splitAt = tail.length - carryLen
                VisibleCarry(tail.substring(0, splitAt), tail.substring(splitAt))
            }
        }

        private fun longestSuffixPrefix(window: String): Int {
            var best = 0
            for (marker in markers) {
                val max = minOf(window.length, marker.length - 1)
                for (len in 1..max) {
                    if (window.endsWith(marker.substring(0, len))) {
                        if (len > best) best = len
                    }
                }
            }
            return best
        }

        private data class MarkerMatch(val position: Int, val marker: String)

        private data class VisibleCarry(val visible: String, val carry: String)
    }
}

internal typealias ChatCallback = (ConverseResponse) -> Unit



