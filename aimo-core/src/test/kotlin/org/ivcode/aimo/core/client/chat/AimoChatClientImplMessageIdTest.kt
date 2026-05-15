package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.AimoToolCall
import org.ivcode.aimo.core.controller.Tool
import org.ivcode.aimo.core.controller.toAimoToolCallbacks
import org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoChatContext
import org.ivcode.aimo.core.model.AimoPrompt
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

class AimoChatClientImplMessageIdTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `chat persists sequential message ids across requests`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(engine = FixedResponseEngine(simpleResponse())),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        client.chat(AimoChatRequest(prompt = "first request", context = emptyMap()))
        client.chat(AimoChatRequest(prompt = "second request", context = emptyMap()))

        val requestMessageIds = dao.getChatRequests(chatId).map { request -> request.messages.map { it.messageId } }
        assertEquals(listOf(listOf(1, 2), listOf(1, 2)), requestMessageIds)
    }

    @Test
    fun `chat persists sequential ids even when history lookup returns empty`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(engine = FixedResponseEngine(simpleResponse()), contextSize = 0),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        client.chat(AimoChatRequest(prompt = "first request", context = emptyMap()))
        client.chat(AimoChatRequest(prompt = "second request", context = emptyMap()))

        val requestMessageIds = dao.getChatRequests(chatId).map { request -> request.messages.map { it.messageId } }
        assertEquals(listOf(listOf(1, 2), listOf(1, 2)), requestMessageIds)
    }

    @Test
    fun `chat with tool call persists messages in expected order`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(
                engine = SequencedResponseEngine(
                    listOf(
                        responseWithToolCall(toolName = "echo", arguments = "{\"value\":\"hello\"}"),
                        simpleResponse(),
                    )
                ),
                contextSize = 4000,
            ),
            tools = toAimoToolCallbacks(TestTools(), objectMapper),
            systemMessages = emptyList(),
        )

        val response = client.chat(AimoChatRequest(prompt = "use the tool", context = emptyMap()))

        assertEquals(listOf(2, 3, 4), response.messages.map { it.messageId })
        assertEquals(listOf("ASSISTANT", "TOOL", "ASSISTANT"), response.messages.map { it.type.name })

        val persisted = dao.getMessages(chatId)
        assertEquals(listOf(1, 2, 3, 4), persisted.map { it.messageId })
        assertEquals(listOf("USER", "ASSISTANT", "TOOL", "ASSISTANT"), persisted.map { it.type })
    }

    @Test
    fun `chat persists thinking from assistant response`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(engine = FixedResponseEngine(responseWithThinking("I thought about it", "the answer")), contextSize = 4000),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        client.chat(AimoChatRequest(prompt = "think about it", context = emptyMap()))

        val assistantMessages = dao.getMessages(chatId).filter { it.type == "ASSISTANT" }
        assertEquals(1, assistantMessages.size)
        assertEquals("I thought about it", assistantMessages.single().thinking)
        assertEquals("the answer", assistantMessages.single().content)
    }

    @Test
    fun `chat drops empty assistant response from persistence and return payload`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(engine = FixedResponseEngine(simpleResponse(content = "")), contextSize = 4000),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        val response = client.chat(AimoChatRequest(prompt = "empty", context = emptyMap()))

        assertTrue(response.messages.isEmpty())
        val persisted = dao.getMessages(chatId)
        assertEquals(1, persisted.size)
        assertEquals("USER", persisted.single().type)
    }

    @Test
    fun `chatStream persists thinking from streamed assistant response`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(
                engine = StreamingResponseEngine(listOf(
                    responseWithThinking("I thought about it", ""),
                    responseWithThinking("", " the answer"),
                    simpleResponse(),
                )),
                contextSize = 4000,
            ),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        client.chatStream(AimoChatRequest(prompt = "think about it", context = emptyMap())) {}

        val assistantMessages = dao.getMessages(chatId).filter { it.type == "ASSISTANT" }
        assertEquals(1, assistantMessages.size)
        assertEquals("I thought about it", assistantMessages.single().thinking)
    }

    @Test
    fun `thinking-only assistant history is not replayed when context excludes thinking`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val capturedPrompts = mutableListOf<List<AimoChatMessage>>()

        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(
                engine = StreamingResponseEngine(
                    listOf(responseWithThinking("I thought about it", "")),
                    capturedPrompts = capturedPrompts,
                ),
                contextSize = 4000,
                excludeThinking = true,
            ),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        client.chatStream(AimoChatRequest(prompt = "first", context = emptyMap())) {}
        client.chat(AimoChatRequest(prompt = "second", context = emptyMap()))

        assertTrue(capturedPrompts.size >= 2)
        val secondPromptMessages = capturedPrompts[1]
        assertTrue(
            secondPromptMessages.none {
                it.type == AimoChatMessageType.ASSISTANT && it.content.isNullOrBlank() && it.toolCalls.isNullOrEmpty()
            },
            "Thinking-only assistant messages should not be replayed as empty assistant turns"
        )
    }

    @Test
    fun `chat persists tool message again when same tool call id appears in a later assistant turn`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(
                engine = SequencedResponseEngine(
                    listOf(
                        responseWithToolCall(toolName = "echo", arguments = "{\"value\":\"hello\"}", toolCallId = "call-1"),
                        responseWithToolCall(toolName = "echo", arguments = "{\"value\":\"hello\"}", toolCallId = "call-1"),
                        simpleResponse(),
                    )
                ),
                contextSize = 4000,
            ),
            tools = toAimoToolCallbacks(TestTools(), objectMapper),
            systemMessages = emptyList(),
        )

        client.chat(AimoChatRequest(prompt = "use the tool", context = emptyMap()))

        val toolMessages = dao.getMessages(chatId).filter { it.type == "TOOL" }
        assertEquals(2, toolMessages.size)
        assertEquals(listOf("echo", "echo"), toolMessages.map { it.toolName })
        assertTrue((toolMessages.first().content ?: "").contains("echo:hello"))
        assertTrue((toolMessages.last().content ?: "").contains("echo:hello"))
    }

    @Test
    fun `chat de-dupes duplicate tool call ids within the same assistant turn`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(
                engine = SequencedResponseEngine(
                    listOf(
                        responseWithToolCalls(
                            listOf(
                                AimoToolCall(id = "call-1", name = "echo", arguments = "{\"value\":\"hello\"}"),
                                AimoToolCall(id = "call-1", name = "echo", arguments = "{\"value\":\"hello\"}"),
                            )
                        ),
                        simpleResponse(),
                    )
                ),
                contextSize = 4000,
            ),
            tools = toAimoToolCallbacks(TestTools(), objectMapper),
            systemMessages = emptyList(),
        )

        client.chat(AimoChatRequest(prompt = "use the tool", context = emptyMap()))

        val toolMessages = dao.getMessages(chatId).filter { it.type == "TOOL" }
        assertEquals(1, toolMessages.size)
        assertEquals("echo", toolMessages.single().toolName)
        assertTrue((toolMessages.single().content ?: "").contains("echo:hello"))
    }

    @Test
    fun `chatStream done callback includes aggregated content for same message id`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(
                engine = StreamingResponseEngine(listOf(
                    responseWithThinking("", "hello"),
                    responseWithThinking("", " world"),
                )),
                contextSize = 4000,
            ),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        val callbackResponses = mutableListOf<AimoChatResponse>()
        val returnedResponse = client.chatStream(AimoChatRequest(prompt = "stream", context = emptyMap())) { response ->
            callbackResponses.add(response)
        }

        val assistantEvents = callbackResponses
            .flatMap { it.messages }
            .filter { it.type.name == "ASSISTANT" && it.messageId == 2 }

        val doneEvent = assistantEvents.lastOrNull()
        assertNotNull(doneEvent)
        assertEquals(true, doneEvent.done)
        assertEquals("hello world", doneEvent.content)
    }

    @Test
    fun `chatStream emits final aggregated done callback when provider chunks never mark done`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val client = AimoChatClientImpl(
            chatId = chatId,
            conversation = TestSessionClient(chatId),
            dao = dao,
            model = testModel(
                engine = StreamingResponseEngine(listOf(
                    responseWithThinkingDone(thinking = "", content = "hello", done = false),
                    responseWithThinkingDone(thinking = "", content = " world", done = false),
                )),
                contextSize = 4000,
            ),
            tools = emptyList(),
            systemMessages = emptyList(),
        )

        val callbackResponses = mutableListOf<AimoChatResponse>()
        val returnedResponse = client.chatStream(AimoChatRequest(prompt = "stream", context = emptyMap())) { response ->
            callbackResponses.add(response)
        }

        val assistantEvents = callbackResponses
            .flatMap { it.messages }
            .filter { it.type == AimoChatMessageType.ASSISTANT && it.messageId == 2 }

        assertTrue(assistantEvents.isNotEmpty())
        assertEquals(true, assistantEvents.last().done)
        assertEquals("hello world", assistantEvents.last().content)

        val returnedAssistant = returnedResponse.messages.last { it.type == AimoChatMessageType.ASSISTANT }
        assertEquals(true, returnedAssistant.done)
        assertEquals("hello world", returnedAssistant.content)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun testModel(
        engine: AimoChatEngine,
        contextSize: Int = 1,
        excludeThinking: Boolean = false,
    ): AimoChatModel = AimoChatModel(
        name = "test",
        chatEngine = engine,
        options = AimoChatOptions(),
        context = AimoChatContext(
            size = contextSize,
            excludeThinking = excludeThinking,
        ),
    )

    private fun simpleResponse(content: String = "ok"): AimoChatResponse = AimoChatResponse(
        chatId = UUID.randomUUID(),
        responseId = UUID.randomUUID(),
        messages = listOf(AimoChatMessage(
            messageId = 0,
            type = AimoChatMessageType.ASSISTANT,
            content = content,
            thinking = null,
            toolName = null,
            done = true,
        )),
        createdAt = Instant.now(),
    )

    private fun responseWithToolCall(
        toolName: String,
        arguments: String,
        toolCallId: String = "call-1",
    ): AimoChatResponse = AimoChatResponse(
        chatId = UUID.randomUUID(),
        responseId = UUID.randomUUID(),
        messages = listOf(AimoChatMessage(
            messageId = 0,
            type = AimoChatMessageType.ASSISTANT,
            content = "calling tool",
            thinking = null,
            toolName = null,
            toolCallId = null,
            toolCalls = listOf(AimoToolCall(id = toolCallId, name = toolName, arguments = arguments)),
            done = true,
        )),
        createdAt = Instant.now(),
    )

    private fun responseWithToolCalls(toolCalls: List<AimoToolCall>): AimoChatResponse = AimoChatResponse(
        chatId = UUID.randomUUID(),
        responseId = UUID.randomUUID(),
        messages = listOf(AimoChatMessage(
            messageId = 0,
            type = AimoChatMessageType.ASSISTANT,
            content = "calling tool",
            thinking = null,
            toolName = null,
            toolCallId = null,
            toolCalls = toolCalls,
            done = true,
        )),
        createdAt = Instant.now(),
    )

    private fun responseWithThinking(thinking: String, content: String): AimoChatResponse = AimoChatResponse(
        chatId = UUID.randomUUID(),
        responseId = UUID.randomUUID(),
        messages = listOf(AimoChatMessage(
            messageId = 0,
            type = AimoChatMessageType.ASSISTANT,
            content = content.ifBlank { null },
            thinking = thinking.ifBlank { null },
            toolName = null,
            done = true,
        )),
        createdAt = Instant.now(),
    )

    private fun responseWithThinkingDone(thinking: String, content: String, done: Boolean): AimoChatResponse = AimoChatResponse(
        chatId = UUID.randomUUID(),
        responseId = UUID.randomUUID(),
        messages = listOf(AimoChatMessage(
            messageId = 0,
            type = AimoChatMessageType.ASSISTANT,
            content = content.ifBlank { null },
            thinking = thinking.ifBlank { null },
            toolName = null,
            done = done,
        )),
        createdAt = Instant.now(),
    )

    private class FixedResponseEngine(
        private val response: AimoChatResponse,
    ) : AimoChatEngine {
        override val options = AimoChatOptions()
        override fun call(prompt: AimoPrompt) = response
        override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
            callback(response)
            return response
        }
    }

    private class SequencedResponseEngine(
        private val responses: List<AimoChatResponse>,
    ) : AimoChatEngine {
        override val options = AimoChatOptions()
        private var index = 0
        override fun call(prompt: AimoPrompt): AimoChatResponse {
            val r = responses.getOrElse(index) { responses.last() }
            index++
            return r
        }
        override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
            val r = call(prompt)
            callback(r)
            return r
        }
    }

    private class StreamingResponseEngine(
        private val chunks: List<AimoChatResponse>,
        private val capturedPrompts: MutableList<List<AimoChatMessage>>? = null,
    ) : AimoChatEngine {
        override val options = AimoChatOptions()
        override fun call(prompt: AimoPrompt): AimoChatResponse {
            capturedPrompts?.add(prompt.messages.toList())
            return chunks.last()
        }
        override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
            capturedPrompts?.add(prompt.messages.toList())
            chunks.forEach { callback(it) }
            return chunks.last()
        }
    }

    private class TestTools {
        @Tool(description = "Echo value")
        fun echo(value: String): String = "echo:$value"
    }

    private class TestSessionClient(
        override val chatId: UUID,
    ) : AimoConversationClient {
        private val metadata = mutableMapOf<String, Any>()
        private val runtimeMetadata = mutableMapOf<String, Any>()

        override fun createChatClient(): AimoChatClient = throw UnsupportedOperationException()
        override fun addMessages(messages: List<AimoChatMessage>) = throw UnsupportedOperationException()
        override fun getChatMetadata(): Map<String, Any> = metadata.toMap()
        override fun readChatMetadata(): Map<String, Any> = metadata.toMap()
        override fun getChatProperty(property: String): Any? = metadata[property]
        override fun readChatProperty(property: String): Any? = metadata[property]
        override fun writeChatProperty(property: String, value: Any) { metadata[property] = value }
        override fun deleteChatProperty(property: String): Boolean = metadata.remove(property) != null
        override fun getRuntimeMetadata(): Map<String, Any> = runtimeMetadata.toMap()
        override fun getRuntimeProperty(property: String): Any? = runtimeMetadata[property]
        override fun writeRuntimeProperty(property: String, value: Any) { runtimeMetadata[property] = value }
        override fun deleteRuntimeProperty(property: String): Boolean = runtimeMetadata.remove(property) != null

    }
}
