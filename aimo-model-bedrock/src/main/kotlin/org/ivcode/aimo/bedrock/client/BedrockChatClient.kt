package org.ivcode.aimo.bedrock.client

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Thin facade that delegates to specialized request executors.
 *
 * Responsible for:
 * - AWS SDK client lifecycle (sync/async)
 * - Delegating requests to RequestExecutor
 */
internal class BedrockChatClient(
    val region: String = "us-east-1",
    val awsAccessKeyId: String? = null,
    val awsSecretAccessKey: String? = null
) {
    private val log = LoggerFactory.getLogger(BedrockChatClient::class.java)
    private val mapper = jacksonObjectMapper()

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
        val executor = RequestExecutor(modelId, client, asyncClient, log, mapper)
        return executor.converse(request)
    }

    fun converseStream(modelId: String, request: ConverseRequest, callback: ChatCallback): ConverseResponse {
        val executor = RequestExecutor(modelId, client, asyncClient, log, mapper)
        return executor.converseStream(request, callback)
    }
}

internal typealias ChatCallback = (ConverseResponse) -> Unit



