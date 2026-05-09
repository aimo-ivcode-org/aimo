package org.ivcode.aimo.ollama.model

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoToolDefinition
import org.ivcode.aimo.ollama.client.ChatRequest
import org.ivcode.aimo.ollama.client.OllamaChatClient
import org.ivcode.aimo.ollama.client.ChatResponse
import org.ivcode.aimo.ollama.client.Message
import org.ivcode.aimo.ollama.client.ToolCall
import org.ivcode.aimo.ollama.client.ToolCallFunction
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OllamaChatEngineImplTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `buildRequest merges options and response mapping preserves tool metadata`() {
        val engine = OllamaChatEngineImpl(
            client = OllamaChatClient("http://localhost:11434"),
            modelName = "fallback-model",
            options = AimoChatOptions(
                model = "configured-model",
                temperature = 0.2,
                maxTokens = 128,
                topP = 0.9,
                stopSequences = listOf("STOP"),
                providerOptions = mapOf("format" to "json"),
            ),
        )

        val prompt = AimoPrompt(
            options = AimoChatOptions(
                temperature = 0.7,
                topK = 25,
                stopSequences = emptyList(),
            ),
            messages = listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.USER,
                    content = "What is the weather?",
                    thinking = null,
                    toolName = null,
                    done = null,
                )
            ),
            tools = listOf(
                AimoToolDefinition(
                    name = "lookupWeather",
                    description = "Look up current weather",
                    inputSchema = mapper.readTree(
                        """
                        {
                          "type": "object",
                          "required": ["city"],
                          "properties": {
                            "city": {
                              "type": "string",
                              "description": "City name"
                            },
                            "units": {
                              "type": "string",
                              "enum": ["metric", "imperial"]
                            },
                            "tags": {
                              "type": "array",
                              "items": {
                                "type": "string"
                              }
                            }
                          }
                        }
                        """.trimIndent()
                    ),
                )
            ),
        )

        val request = buildRequest(engine, prompt)
        val requestJson = mapper.readTree(mapper.writeValueAsString(request))

        assertEquals("configured-model", requestJson["model"].getTextValue())
        assertEquals("user", requestJson["messages"][0]["role"].getTextValue())
        assertEquals("What is the weather?", requestJson["messages"][0]["content"].getTextValue())
        assertEquals(0.7, requestJson["options"]["temperature"].asDouble())
        assertEquals(128, requestJson["options"]["num_predict"].asInt())
        assertEquals(0.9, requestJson["options"]["top_p"].asDouble())
        assertEquals(25, requestJson["options"]["top_k"].asInt())
        assertEquals("STOP", requestJson["options"]["stop"][0].getTextValue())
        assertEquals("json", requestJson["options"]["format"].getTextValue())

        val toolJson = requestJson["tools"][0]["function"]
        assertEquals("lookupWeather", toolJson["name"].getTextValue())
        assertEquals("Look up current weather", toolJson["description"].getTextValue())
        assertEquals("object", toolJson["parameters"]["type"].getTextValue().lowercase())
        assertEquals("city", toolJson["parameters"]["required"][0].getTextValue())
        assertEquals("City name", toolJson["parameters"]["properties"]["city"]["description"].getTextValue())
        assertEquals("metric", toolJson["parameters"]["properties"]["units"]["enum"][0].getTextValue())
        assertEquals("string", toolJson["parameters"]["properties"]["tags"]["items"]["type"].getTextValue().lowercase())

        val mappedResponse = mapResponse(
            engine = engine,
            response = ChatResponse(
                model = "configured-model",
                createdAt = Instant.parse("2026-05-06T00:00:00Z"),
                message = Message(
                    role = "assistant",
                    content = "answer",
                    thinking = "internal reasoning",
                    toolCalls = listOf(
                        ToolCall(
                            function = ToolCallFunction(
                                name = "lookupWeather",
                                arguments = mapOf("city" to "Boston", "units" to "metric"),
                            )
                        )
                    ),
                    toolName = null,
                ),
                done = true,
                doneReason = null,
                totalDuration = null,
                loadDuration = null,
                promptEvalCount = null,
                promptEvalDuration = null,
                evalCount = null,
                evalDuration = null,
                logProbs = null,
            ),
            done = true,
        )

        val responseMessage = mappedResponse.messages.single()
        assertEquals("answer", responseMessage.content)
        assertEquals("internal reasoning", responseMessage.thinking)
        assertNotNull(responseMessage.toolCalls)
        assertEquals(1, responseMessage.toolCalls!!.size)
        assertEquals("lookupWeather", responseMessage.toolCalls!!.single().name)
        val toolArguments = mapper.readTree(responseMessage.toolCalls!!.single().arguments)
        assertEquals("Boston", toolArguments["city"].getTextValue())
        assertEquals("metric", toolArguments["units"].getTextValue())
    }

    @Test
    fun `streaming call forwards chunks and increments message ids`() {
        val responseBody = listOf(
            responseLine(content = "Hel", done = false),
            responseLine(content = "lo", done = true),
        ).joinToString(separator = "\n", postfix = "\n")

        StubOllamaServer(listOf(StubResponse(statusCode = 200, body = responseBody))).use { server ->
            val engine = OllamaChatEngineImpl(
                client = OllamaChatClient(server.baseUrl),
                modelName = "stream-model",
                options = AimoChatOptions(),
            )

            val callbacks = mutableListOf<AimoChatResponse>()
            val finalResponse = engine.call(
                AimoPrompt(
                    messages = listOf(
                        AimoChatMessage(
                            messageId = 0,
                            type = AimoChatMessageType.USER,
                            content = "Hello",
                            thinking = null,
                            toolName = null,
                            done = null,
                        )
                    )
                )
            ) { callbacks += it }

            assertEquals(2, callbacks.size)
            assertEquals(0, callbacks[0].messages.single().messageId)
            assertEquals("Hel", callbacks[0].messages.single().content)
            assertEquals(false, callbacks[0].messages.single().done)
            assertEquals(1, callbacks[1].messages.single().messageId)
            assertEquals("lo", callbacks[1].messages.single().content)
            assertEquals(true, callbacks[1].messages.single().done)

            assertEquals("Hello", finalResponse.messages.single().content)
            assertEquals(true, finalResponse.messages.single().done)
        }
    }

    @Test
    fun `client throws clear error for non-2xx response bodies`() {
        StubOllamaServer(
            listOf(
                StubResponse(
                    statusCode = 500,
                    body = "Internal server exploded: upstream returned HTML, not JSON",
                )
            )
        ).use { server ->
            val client = OllamaChatClient(server.baseUrl)
            val request = ChatRequest(
                model = "test-model",
                messages = listOf(
                    Message(
                        role = "user",
                        content = "Hello",
                    )
                ),
            )

            val error = assertFailsWith<IllegalStateException> {
                client.chat(request)
            }

            assertTrue(error.message!!.contains("HTTP 500"))
            assertTrue(error.message!!.contains("Internal server exploded"))
            assertTrue(error.message!!.contains("not JSON"))
        }
    }

    @Test
    fun `buildRequest forwards provider options even without standard ollama options`() {
        val engine = OllamaChatEngineImpl(
            client = OllamaChatClient("http://localhost:11434"),
            modelName = "fallback-model",
            options = AimoChatOptions(
                providerOptions = mapOf(
                    "format" to "json",
                    "keep_alive" to "30m",
                ),
            ),
        )

        val prompt = AimoPrompt(
            messages = listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.USER,
                    content = "Hello",
                    thinking = null,
                    toolName = null,
                    done = null,
                )
            ),
        )

        val request = buildRequest(engine, prompt)
        val requestJson = mapper.readTree(mapper.writeValueAsString(request))

        assertEquals("json", requestJson["options"]["format"].getTextValue())
        assertEquals("30m", requestJson["options"]["keep_alive"].getTextValue())
    }

    private fun responseLine(
        content: String,
        done: Boolean,
        thinking: String? = null,
        toolCallsJson: String? = null,
    ): String {
        val thinkingPart = thinking?.let { ",\"thinking\":${mapper.writeValueAsString(it)}" } ?: ""
        val toolCallsPart = toolCallsJson?.let { ",\"tool_calls\":$it" } ?: ""
        return """
            {"model":"test-model","created_at":"2026-05-06T00:00:00Z","message":{"role":"assistant","content":${mapper.writeValueAsString(content)}$thinkingPart$toolCallsPart},"done":$done,"done_reason":null,"total_duration":null,"load_duration":null,"prompt_eval_count":null,"prompt_eval_duration":null,"eval_count":null,"eval_duration":null,"logprobs":null}
        """.trimIndent()
    }

    private fun buildRequest(engine: OllamaChatEngineImpl, prompt: AimoPrompt): ChatRequest {
        val method = OllamaChatEngineImpl::class.java.getDeclaredMethod(
            "buildRequest",
            AimoPrompt::class.java,
            Boolean::class.javaObjectType,
        )
        method.isAccessible = true
        return method.invoke(engine, prompt, null) as ChatRequest
    }

    private fun mapResponse(
        engine: OllamaChatEngineImpl,
        response: ChatResponse,
        done: Boolean,
        messageId: Int = 0,
    ): AimoChatResponse {
        val method = OllamaChatEngineImpl::class.java.getDeclaredMethod(
            "toAimoChatResponse",
            ChatResponse::class.java,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(engine, response, done, messageId) as AimoChatResponse
    }

    @Suppress("DEPRECATION")
    private fun tools.jackson.databind.JsonNode.getTextValue(): String = textValue()

    private class StubOllamaServer(
        responses: List<StubResponse>,
    ) : AutoCloseable {
        private val server = ServerSocket(0)
        val baseUrl: String = "http://127.0.0.1:${server.localPort}"
        val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private var failure: Throwable? = null
        private val worker = thread(start = true, isDaemon = true) {
            try {
                responses.forEach { body ->
                    server.accept().use { socket ->
                        val input = BufferedInputStream(socket.getInputStream())
                        val headers = readHeaders(input)
                        val contentLength = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                            .find(headers)
                            ?.groupValues
                            ?.get(1)
                            ?.toInt()
                        val bodyBytes = when {
                            contentLength != null -> readBytes(input, contentLength)
                            headers.contains("Transfer-Encoding: chunked", ignoreCase = true) -> readChunkedBody(input)
                            else -> ByteArray(0)
                        }
                        requests += String(bodyBytes, StandardCharsets.UTF_8)

                        val responseBytes = body.body.toByteArray(StandardCharsets.UTF_8)
                        val output = socket.getOutputStream()
                        output.write(
                            (
                                "HTTP/1.1 ${body.statusCode} ${body.reasonPhrase()}\r\n" +
                                    "Content-Type: application/x-ndjson\r\n" +
                                    "Content-Length: ${responseBytes.size}\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                                ).toByteArray(StandardCharsets.UTF_8)
                        )
                        output.write(responseBytes)
                        output.flush()
                    }
                }
            } catch (t: Throwable) {
                failure = t
            }
        }

        override fun close() {
            server.close()
            worker.join(2_000)
            failure?.let { throw AssertionError("Stub Ollama server failed", it) }
        }

        private fun readHeaders(input: BufferedInputStream): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val next = input.read()
                check(next >= 0) { "Unexpected EOF while reading request headers" }
                bytes.write(next)
                val data = bytes.toByteArray()
                val size = data.size
                if (size >= 4 &&
                    data[size - 4] == '\r'.code.toByte() &&
                    data[size - 3] == '\n'.code.toByte() &&
                    data[size - 2] == '\r'.code.toByte() &&
                    data[size - 1] == '\n'.code.toByte()
                ) {
                    return bytes.toString(StandardCharsets.UTF_8.name())
                }
            }
        }

        private fun readBytes(input: BufferedInputStream, length: Int): ByteArray {
            val result = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = input.read(result, offset, length - offset)
                check(read >= 0) { "Unexpected EOF while reading request body" }
                offset += read
            }
            return result
        }

        private fun readChunkedBody(input: BufferedInputStream): ByteArray {
            val output = ByteArrayOutputStream()
            while (true) {
                val sizeLine = readLine(input).trim()
                val size = sizeLine.substringBefore(';').trim().toInt(16)
                if (size == 0) {
                    readLine(input)
                    return output.toByteArray()
                }
                output.write(readBytes(input, size))
                readLine(input)
            }
        }

        private fun readLine(input: BufferedInputStream): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val next = input.read()
                check(next >= 0) { "Unexpected EOF while reading request line" }
                if (next == '\n'.code) {
                    return bytes.toString(StandardCharsets.UTF_8.name()).trimEnd('\r')
                }
                bytes.write(next)
            }
        }
    }

    private data class StubResponse(
        val statusCode: Int,
        val body: String,
    ) {
        fun reasonPhrase(): String = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> ""
        }
    }
}




