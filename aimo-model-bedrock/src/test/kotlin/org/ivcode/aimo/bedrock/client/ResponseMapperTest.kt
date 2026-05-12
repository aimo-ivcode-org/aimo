package org.ivcode.aimo.bedrock.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse as BedrockConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.Message as BedrockMessage
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock as BedrockContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("ResponseMapper")
class ResponseMapperTest {

    @Test
    @DisplayName("toBedrockFields maps messages to BedrockMessage with correct roles")
    fun testToBedrockFieldsRoles() {
        val request = ConverseRequest(
            model = "test-model",
            messages = listOf(
                ConverseMessage(role = "user", content = listOf(ContentBlock(text = "Hello"))),
                ConverseMessage(role = "assistant", content = listOf(ContentBlock(text = "Hi")))
            )
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertEquals(2, fields.messages.size)
        assertEquals(ConversationRole.USER, fields.messages[0].role())
        assertEquals(ConversationRole.ASSISTANT, fields.messages[1].role())
    }

    @Test
    @DisplayName("toBedrockFields maps message content with text")
    fun testToBedrockFieldsTextContent() {
        val request = ConverseRequest(
            model = "test-model",
            messages = listOf(
                ConverseMessage(role = "user", content = listOf(ContentBlock(text = "Hello")))
            )
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertEquals(1, fields.messages.size)
        assertEquals(1, fields.messages[0].content().size)
        assertEquals("Hello", fields.messages[0].content()[0].text())
    }

    @Test
    @DisplayName("toBedrockFields maps system blocks")
    fun testToBedrockFieldsSystemBlocks() {
        val request = ConverseRequest(
            model = "test-model",
            messages = emptyList(),
            system = listOf(SystemContentBlock(text = "You are helpful"))
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertNotNull(fields.system)
        assertEquals(1, fields.system!!.size)
        assertEquals("You are helpful", fields.system!![0].text())
    }

    @Test
    @DisplayName("toBedrockFields maps inference config temperature")
    fun testToBedrockFieldsTemperature() {
        val request = ConverseRequest(
            model = "test-model",
            messages = emptyList(),
            inferenceConfig = InferenceConfiguration(temperature = 0.7)
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertNotNull(fields.inferenceConfig)
        assertEquals(0.7f, fields.inferenceConfig!!.temperature())
    }

    @Test
    @DisplayName("toBedrockFields maps inference config maxTokens")
    fun testToBedrockFieldsMaxTokens() {
        val request = ConverseRequest(
            model = "test-model",
            messages = emptyList(),
            inferenceConfig = InferenceConfiguration(maxTokens = 2048)
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertNotNull(fields.inferenceConfig)
        assertEquals(2048, fields.inferenceConfig!!.maxTokens())
    }

    @Test
    @DisplayName("toBedrockFields maps inference config topP")
    fun testToBedrockFieldsTopP() {
        val request = ConverseRequest(
            model = "test-model",
            messages = emptyList(),
            inferenceConfig = InferenceConfiguration(topP = 0.9)
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertNotNull(fields.inferenceConfig)
        assertEquals(0.9f, fields.inferenceConfig!!.topP())
    }

    @Test
    @DisplayName("toBedrockFields omits null inference config")
    fun testToBedrockFieldsNullInferenceConfig() {
        val request = ConverseRequest(
            model = "test-model",
            messages = emptyList()
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertEquals(null, fields.inferenceConfig)
    }

    @Test
    @DisplayName("toBedrockFields maps toolUse content block")
    fun testToBedrockFieldsToolUseBlock() {
        val request = ConverseRequest(
            model = "test-model",
            messages = listOf(
                ConverseMessage(
                    role = "assistant",
                    content = listOf(
                        ContentBlock(
                            toolUse = ToolUse(
                                toolUseId = "call-123",
                                name = "calculator",
                                input = mapOf("a" to 1, "b" to 2)
                            )
                        )
                    )
                )
            )
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertEquals(1, fields.messages[0].content().size)
        assertNotNull(fields.messages[0].content()[0].toolUse())
        assertEquals("call-123", fields.messages[0].content()[0].toolUse().toolUseId())
        assertEquals("calculator", fields.messages[0].content()[0].toolUse().name())
    }

    @Test
    @DisplayName("toBedrockFields maps toolResult content block")
    fun testToBedrockFieldsToolResultBlock() {
        val request = ConverseRequest(
            model = "test-model",
            messages = listOf(
                ConverseMessage(
                    role = "user",
                    content = listOf(
                        ContentBlock(
                            toolResult = ToolResult(
                                toolUseId = "call-123",
                                content = listOf(ContentBlock(text = "Result: 3"))
                            )
                        )
                    )
                )
            )
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertEquals(1, fields.messages[0].content().size)
        assertNotNull(fields.messages[0].content()[0].toolResult())
        assertEquals("call-123", fields.messages[0].content()[0].toolResult().toolUseId())
        assertEquals(1, fields.messages[0].content()[0].toolResult().content().size)
    }

    @Test
    @DisplayName("toBedrockFields maps additionalModelRequestFields")
    fun testToBedrockFieldsAdditionalFields() {
        val request = ConverseRequest(
            model = "test-model",
            messages = emptyList(),
            additionalModelRequestFields = mapOf("reasoning_config" to "high")
        )

        val fields = ResponseMapper.toBedrockFields(request)
        assertNotNull(fields.additionalModelRequestFields)
        assertTrue { fields.additionalModelRequestFields!!.isMap }
    }

    @Test
    @DisplayName("toBedrockFields handles invalid role by throwing")
    fun testToBedrockFieldsInvalidRole() {
        val request = ConverseRequest(
            model = "test-model",
            messages = listOf(
                ConverseMessage(role = "invalid", content = emptyList())
            )
        )

        try {
            ResponseMapper.toBedrockFields(request)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Unsupported Bedrock message role 'invalid'. Allowed roles are: user, assistant", e.message)
        }
    }

    @Test
    @DisplayName("mapBedrockResponse extracts text content")
    fun testMapBedrockResponseText() {
        val bedrockResponse = createBedrockResponse(
            role = ConversationRole.ASSISTANT,
            textContent = "Hello user"
        )

        val response = ResponseMapper.mapBedrockResponse(bedrockResponse)
        assertEquals("assistant", response.output.message.role)
        assertEquals(1, response.output.message.content.size)
        assertEquals("Hello user", response.output.message.content[0].text)
    }

    @Test
    @DisplayName("mapBedrockResponse extracts usage tokens")
    fun testMapBedrockResponseUsage() {
        val bedrockResponse = createBedrockResponse(
            role = ConversationRole.ASSISTANT,
            textContent = "Response",
            inputTokens = 100,
            outputTokens = 50
        )

        val response = ResponseMapper.mapBedrockResponse(bedrockResponse)
        assertEquals(100, response.usage.inputTokens)
        assertEquals(50, response.usage.outputTokens)
    }

    @Test
    @DisplayName("mapBedrockResponse extracts stopReason")
    fun testMapBedrockResponseStopReason() {
        val bedrockResponse = createBedrockResponse(
            role = ConversationRole.ASSISTANT,
            textContent = "Done",
            stopReason = "end_turn"
        )

        val response = ResponseMapper.mapBedrockResponse(bedrockResponse)
        assertEquals("end_turn", response.stopReason)
    }

    // Helper methods for creating mock Bedrock responses
    private fun createBedrockResponse(
        role: ConversationRole,
        textContent: String? = null,
        inputTokens: Int = 0,
        outputTokens: Int = 0,
        stopReason: String = "end_turn"
    ): BedrockConverseResponse {
        val contentBlock = if (textContent != null) {
            BedrockContentBlock.builder().text(textContent).build()
        } else {
            BedrockContentBlock.builder().text("").build()
        }

        val message = BedrockMessage.builder()
            .role(role)
            .content(contentBlock)
            .build()

        return BedrockConverseResponse.builder()
            .output { output ->
                output.message(message)
            }
            .stopReason(stopReason)
            .usage { usage ->
                usage.inputTokens(inputTokens)
                usage.outputTokens(outputTokens)
            }
            .build()
    }
}


