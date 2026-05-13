package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatInputTokenBudgeterTest {

    @Test
    fun `historyForPrompt keeps newest history within default budget`() {
        val budgeter = ChatInputTokenBudgeter(maxInputTokens = 5)
        val history = listOf(
            message(1, "123456789"),
            message(2, "abcdefghi"),
            message(3, "JKLMNOPQR"),
        )

        val result = budgeter.historyForPrompt(
            systemMessages = emptyList(),
            history = history,
            prompt = message(99, ""),
            taskMessages = emptyList(),
            tools = emptyList(),
        )

        assertEquals(listOf(history.last()), result)
    }

    @Test
    fun `historyForPrompt returns empty when prompt and task messages use full budget`() {
        val budgeter = ChatInputTokenBudgeter(maxInputTokens = 6)
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
    fun `recordPromptUsage refines token estimate for future requests`() {
        val budgeter = ChatInputTokenBudgeter(maxInputTokens = 4)
        val history = listOf(
            message(1, "123456789"),
            message(2, "abcdefghi"),
        )

        val beforeCalibration = budgeter.historyForPrompt(
            systemMessages = emptyList(),
            history = history,
            prompt = message(99, ""),
            taskMessages = emptyList(),
            tools = emptyList(),
        )
        assertEquals(listOf(history.last()), beforeCalibration)

        budgeter.recordPromptUsage(
            promptMessages = listOf(message(100, "123456789012345678")),
            tools = emptyList(),
            promptTokens = 2,
        )

        val afterCalibration = budgeter.historyForPrompt(
            systemMessages = emptyList(),
            history = history,
            prompt = message(99, ""),
            taskMessages = emptyList(),
            tools = emptyList(),
        )
        assertEquals(history, afterCalibration)
    }


    @Test
    fun `seeded calibration is applied in new budgeter instance`() {
        val seeded = ChatInputTokenBudgeter(
            maxInputTokens = 4,
            initialObservedPromptCharacters = 18,
            initialObservedPromptTokens = 2,
        )
        val history = listOf(
            message(1, "123456789"),
            message(2, "abcdefghi"),
        )

        val result = seeded.historyForPrompt(
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
        val budgeter = ChatInputTokenBudgeter(maxInputTokens = 3)
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
        val budgeter = ChatInputTokenBudgeter(maxInputTokens = 10, excludeThinking = false)
        val history = listOf(
            message(1, "old", thinking = "old-thought"),
        )
        val prompt = message(2, "prompt", thinking = "prompt-thought")

        val result = budgeter.promptMessagesForCall(
            systemMessages = emptyList(),
            history = history,
            prompt = prompt,
            taskMessages = emptyList(),
            tools = emptyList(),
        )

        assertEquals("old-thought", result[0].thinking)
        assertEquals("prompt-thought", result[1].thinking)
    }

    @Test
    fun `promptMessagesForCall removes thinking when exclusion is enabled`() {
        val budgeter = ChatInputTokenBudgeter(maxInputTokens = 10, excludeThinking = true)
        val history = listOf(
            message(1, "old", thinking = "old-thought"),
        )
        val prompt = message(2, "prompt", thinking = "prompt-thought")

        val result = budgeter.promptMessagesForCall(
            systemMessages = emptyList(),
            history = history,
            prompt = prompt,
            taskMessages = emptyList(),
            tools = emptyList(),
        )

        assertEquals(null, result[0].thinking)
        assertEquals(null, result[1].thinking)
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
