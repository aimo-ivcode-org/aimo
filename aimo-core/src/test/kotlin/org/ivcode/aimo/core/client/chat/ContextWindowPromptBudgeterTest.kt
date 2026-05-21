package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextWindowPromptBudgeterTest {

    @Test
    fun `historyForPrompt keeps newest history within default budget`() {
        val budgeter = ContextWindowPromptBudgeter(maxInputTokens = 10, charsPerToken = 1.0)
        val history = listOf(
            message(1, "12345"),
            message(2, "67890"),
            message(3, "ABCDE"),
        )

        val result = budgeter.historyForPrompt(
            systemMessages = emptyList(),
            history = history,
            prompt = message(99, "xx"),
            taskMessages = emptyList(),
            tools = emptyList(),
        )

        assertEquals(listOf(history.last()), result)
    }

    @Test
    fun `historyForPrompt returns empty when prompt and task messages use full budget`() {
        val budgeter = ContextWindowPromptBudgeter(maxInputTokens = 6)
        val history = listOf(message(1, "123456789"))

        val result = budgeter.historyForPrompt(
            systemMessages = emptyList(),
            history = history,
            prompt = message(99, "123456789"),
            taskMessages = listOf(message(100, "abcdefghi")),
            tools = emptyList(),
        )

        assertEquals(emptyList(), result)
    }

    @Test
    fun `historyForPrompt includes all history when budget allows`() {
        val budgeter = ContextWindowPromptBudgeter(maxInputTokens = 20, charsPerToken = 4.0)
        val history = listOf(
            message(1, "123456789"),
            message(2, "abcdefghi"),
        )

        val result = budgeter.historyForPrompt(
            systemMessages = emptyList(),
            history = history,
            prompt = message(99, ""),
            taskMessages = emptyList(),
            tools = emptyList(),
        )

        assertEquals(history, result)
    }

    @Test
    fun `historyForPrompt trims flat history messages from the oldest side`() {
        val budgeter = ContextWindowPromptBudgeter(maxInputTokens = 3)
        val history = listOf(
            message(1, "1234"),
            message(2, "1234"),
            message(3, "abcd"),
            message(4, "abcd"),
        )

        val result = budgeter.historyForPrompt(
            systemMessages = emptyList(),
            history = history,
            prompt = message(99, ""),
            taskMessages = emptyList(),
            tools = emptyList(),
        )

        assertEquals(listOf(history[1], history[2], history[3]), result)
    }

    @Test
    fun `promptMessagesForCall keeps thinking when exclusion is disabled`() {
        val budgeter = ContextWindowPromptBudgeter(maxInputTokens = 10, excludeThinking = false)
        val history = listOf(
            message(1, "old", thinking = "old-thought"),
        )
        val prompt = message(2, "prompt", thinking = "prompt-thought")

        budgeter.withPromptForCall(
            systemMessages = emptyList(),
            prompt = prompt,
            taskMessages = emptyList(),
            tools = emptyList(),
            historyProvider = { history },
            execute = { result ->
                assertEquals("old-thought", result[0].thinking)
                assertEquals("prompt-thought", result[1].thinking)
                AimoChatResponse(
                    chatId = java.util.UUID.randomUUID(),
                    responseId = java.util.UUID.randomUUID(),
                    messages = emptyList(),
                    createdAt = java.time.Instant.now(),
                )
            }
        )
    }

    @Test
    fun `promptMessagesForCall removes thinking when exclusion is enabled`() {
        val budgeter = ContextWindowPromptBudgeter(maxInputTokens = 10, excludeThinking = true)
        val history = listOf(
            message(1, "old", thinking = "old-thought"),
        )
        val prompt = message(2, "prompt", thinking = "prompt-thought")

        budgeter.withPromptForCall(
            systemMessages = emptyList(),
            prompt = prompt,
            taskMessages = emptyList(),
            tools = emptyList(),
            historyProvider = { history },
            execute = { result ->
                assertEquals(null, result[0].thinking)
                assertEquals(null, result[1].thinking)
                AimoChatResponse(
                    chatId = java.util.UUID.randomUUID(),
                    responseId = java.util.UUID.randomUUID(),
                    messages = emptyList(),
                    createdAt = java.time.Instant.now(),
                )
            }
        )
    }


    private fun message(id: Int, content: String, thinking: String? = null) = AimoChatMessage(
        messageId = id,
        type = AimoChatMessageType.USER,
        content = content,
        thinking = thinking,
        toolName = null,
        done = true,
    )
}



