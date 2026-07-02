package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoChatResponse
import org.ivcode.aimo.core.model.AimoPromptCacheUsage
import org.ivcode.aimo.core.model.AimoUsage
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertContains

class ServiceTransformersTest {

    private val mapper = ObjectMapper()

    @Test
    fun `toChatResponse includes usage when AimoChatResponse has usage`() {
        val chatId = UUID.randomUUID()
        val responseId = UUID.randomUUID()
        val now = Instant.now()

        val aimoResponse = AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.ASSISTANT,
                    content = "Hello",
                    thinking = null,
                    toolName = null,
                    toolCallId = null,
                    toolCalls = null,
                    done = true
                )
            ),
            createdAt = now,
            usage = AimoUsage(
                inputTokens = 100,
                outputTokens = 50,
                promptCache = AimoPromptCacheUsage(
                    cacheReadInputTokens = 10,
                    cacheWriteInputTokens = 5
                )
            )
        )

        val chatResponse = aimoResponse.toChatResponse()

        assertEquals(chatId, chatResponse.chatId)
        assertEquals(responseId, chatResponse.responseId)
        assertEquals(1, chatResponse.messages.size)
        assertEquals(now, chatResponse.createdAt)

        assertNotNull(chatResponse.usage)
        assertEquals(100, chatResponse.usage.inputTokens)
        assertEquals(50, chatResponse.usage.outputTokens)
        assertNotNull(chatResponse.usage.promptCache)
        assertEquals(10, chatResponse.usage.promptCache.cacheReadInputTokens)
        assertEquals(5, chatResponse.usage.promptCache.cacheWriteInputTokens)
    }

    @Test
    fun `toChatResponse serializes usage to JSON`() {
        val aimoResponse = AimoChatResponse(
            chatId = UUID.randomUUID(),
            responseId = UUID.randomUUID(),
            messages = listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.ASSISTANT,
                    content = "Test",
                    thinking = null,
                    toolName = null,
                    toolCallId = null,
                    toolCalls = null,
                    done = true
                )
            ),
            createdAt = Instant.now(),
            usage = AimoUsage(
                inputTokens = 200,
                outputTokens = 75,
            )
        )

        val chatResponse = aimoResponse.toChatResponse()
        val json = mapper.writeValueAsString(chatResponse)

        // Verify JSON contains usage fields
        assertContains(json, "\"inputTokens\":200")
        assertContains(json, "\"outputTokens\":75")
        assertContains(json, "\"usage\"")
    }

    @Test
    fun `toChatResponse handles null usage`() {
        val aimoResponse = AimoChatResponse(
            chatId = UUID.randomUUID(),
            responseId = UUID.randomUUID(),
            messages = listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.ASSISTANT,
                    content = "Test",
                    thinking = null,
                    toolName = null,
                    toolCallId = null,
                    toolCalls = null,
                    done = true
                )
            ),
            createdAt = Instant.now(),
            usage = null
        )

        val chatResponse = aimoResponse.toChatResponse()
        assertEquals(null, chatResponse.usage)
    }
}
