package org.ivcode.aimo.bedrock.client

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse as BedrockConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock as BedrockContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.Message as BedrockMessage
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import tools.jackson.module.kotlin.jacksonObjectMapper

class RequestExecutorTest {

    private val mapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(RequestExecutorTest::class.java)

    @Test
    fun wrapsClientFailuresFromConverse() {
        val executor = executor(
            client = proxy(BedrockRuntimeClient::class.java) { method, _ ->
                if (method.name == "converse") {
                    throw IllegalStateException("boom")
                }
                null
            },
        )

        val exception = assertFailsWith<IllegalStateException> {
            executor.converse(sampleRequest())
        }

        assertTrue(exception.message!!.contains("Bedrock chat request failed"))
        assertTrue(exception.cause is IllegalStateException)
    }

    @Test
    fun wrapsMappingFailuresFromConverse() {
        val response = BedrockConverseResponse.builder()
            .output { output ->
                output.message(
                    BedrockMessage.builder()
                        .content(BedrockContentBlock.builder().text("hello").build())
                        .build(),
                )
            }
            .build()

        val executor = executor(
            client = proxy(BedrockRuntimeClient::class.java) { method, _ ->
                if (method.name == "converse") {
                    return@proxy response
                }
                null
            },
        )

        val exception = assertFailsWith<IllegalStateException> {
            executor.converse(sampleRequest())
        }

        assertTrue(exception.message!!.contains("Bedrock chat request failed"))
        assertTrue(exception.cause is NullPointerException)
    }

    @Test
    fun wrapsFailedStreamFuturesAndRestoresInterruptStatus() {
        val future = CompletableFuture<Any?>().apply {
            completeExceptionally(IllegalStateException("boom"))
        }

        val executor = executor(
            asyncClient = proxy(BedrockRuntimeAsyncClient::class.java) { method, _ ->
                if (method.name == "converseStream") {
                    return@proxy future
                }
                null
            },
        )

        val exception = assertFailsWith<IllegalStateException> {
            executor.converseStream(sampleRequest()) { }
        }

        assertTrue(exception.message!!.contains("Bedrock stream request failed"))
        assertTrue(exception.cause is java.util.concurrent.ExecutionException)
    }

    @Test
    fun restoresInterruptStatusWhenStreamGetIsInterrupted() {
        val future = CompletableFuture<Any?>()
        val executor = executor(
            asyncClient = proxy(BedrockRuntimeAsyncClient::class.java) { method, _ ->
                if (method.name == "converseStream") {
                    return@proxy future
                }
                null
            },
        )

        Thread.currentThread().interrupt()
        try {
            val exception = assertFailsWith<IllegalStateException> {
                executor.converseStream(sampleRequest()) { }
            }

            assertTrue(exception.message!!.contains("Bedrock stream request failed"))
            assertTrue(exception.cause is InterruptedException)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    private fun executor(
        client: BedrockRuntimeClient = proxy(BedrockRuntimeClient::class.java) { _, _ -> null },
        asyncClient: BedrockRuntimeAsyncClient = proxy(BedrockRuntimeAsyncClient::class.java) { _, _ -> null },
    ): RequestExecutor =
        RequestExecutor(
            modelId = "test-model",
            client = client,
            asyncClient = asyncClient,
            log = log,
            mapper = mapper,
        )

    private fun sampleRequest(): ConverseRequest =
        ConverseRequest(
            model = "test-model",
            messages = listOf(
                ConverseMessage(
                    role = "user",
                    content = listOf(ContentBlock(text = "hello")),
                ),
            ),
        )

    private fun <T : Any> proxy(
        type: Class<T>,
        block: (Method, Array<out Any?>?) -> Any?,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            type.classLoader,
            arrayOf(type),
            InvocationHandler { _, method, args -> block(method, args) },
        ) as T
    }
}
