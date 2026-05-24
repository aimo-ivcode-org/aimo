package org.ivcode.aimo.bedrock.client

import org.slf4j.Logger
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest as BedrockConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest as BedrockConverseStreamRequest
import tools.jackson.databind.ObjectMapper
import org.ivcode.aimo.bedrock.client.transformer.MessageTransformerRegistry

/**
 * Executes Bedrock converse requests (sync and streaming).
 */
internal class RequestExecutor(
    private val modelId: String,
    private val client: BedrockRuntimeClient,
    private val asyncClient: BedrockRuntimeAsyncClient,
    private val log: Logger,
    private val mapper: ObjectMapper,
) {

    fun converse(request: ConverseRequest): ConverseResponse {
        val fields = ResponseMapper.toBedrockFields(request)
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
            val mapped = ResponseMapper.mapBedrockResponse(response)
            if (log.isTraceEnabled) {
                log.trace("Bedrock converse response (AIMO mapped) modelId={} payload={}", modelId, asLogValue(mapped))
            }
            val transformer = MessageTransformerRegistry.create(modelId)
            val transformed = transformer.transformFinalResponse(mapped)
            log.debug(
                "Bedrock converse response modelId={}, stopReason={}, usageIn={}, usageOut={}, contentBlocks={}",
                modelId,
                transformed.stopReason,
                transformed.usage?.inputTokens,
                transformed.usage?.outputTokens,
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

    fun converseStream(request: ConverseRequest, callback: ChatCallback): ConverseResponse {
        val fields = ResponseMapper.toBedrockFields(request)
        log.debug(
            "Bedrock stream request modelId={}, messages={}, system={}, tools={}, additionalFields={}",
            modelId,
            request.messages.size,
            request.system?.size ?: 0,
            request.toolConfig?.tools?.size ?: 0,
            request.additionalModelRequestFields?.isNotEmpty() == true,
        )

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

        val handler = StreamingResponseHandler(modelId, log, mapper)
        val builtHandler = handler.build(callback)

        return try {
            asyncClient.converseStream(streamRequest, builtHandler).get()

            val assembled = handler.assembleResponse()
            if (log.isTraceEnabled) {
                log.trace("Bedrock stream response (AIMO assembled) modelId={} payload={}", modelId, asLogValue(assembled))
            }

            val transformer = MessageTransformerRegistry.create(modelId)
            val transformed = transformer.transformFinalResponse(assembled)
            handler.logSummary()
            if (log.isTraceEnabled) {
                log.trace("Bedrock stream response (AIMO transformed) modelId={} payload={}", modelId, asLogValue(transformed))
            }
            transformed
        } catch (e: Exception) {
            log.error("Bedrock stream failed modelId={}: {}", modelId, e.message, e)
            throw IllegalStateException("Bedrock stream request failed: ${e.message}", e)
        }
    }

    private fun asLogValue(value: Any?): String {
        return try {
            mapper.writeValueAsString(value)
        } catch (_: Exception) {
            value?.toString() ?: "null"
        }
    }
}

