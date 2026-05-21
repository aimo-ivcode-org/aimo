package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.model.AimoToolCallback

/**
 * Prompt budgeter that performs no token-based truncation.
 *
 * It composes the full prompt in canonical order and optionally strips thinking
 * content when configured.
 */
internal class NoOpPromptBudgeter(
    private val excludeThinking: Boolean = false,
) : PromptBudgeter {
    override fun withPromptForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
        historyProvider: (Long?) -> List<AimoChatMessage>,
        execute: (promptMessages: List<AimoChatMessage>) -> AimoChatResponse,
    ): AimoChatResponse {
        val history = historyProvider(null)
        val promptMessages = systemMessages + history + prompt + taskMessages

        val filteredMessages = promptMessages
            .let { messages ->
                if (!excludeThinking) messages
                else messages.map { message ->
                    if (message.thinking == null) message else message.copy(thinking = null)
                }
            }
            .filterNot { it.isEmptyPayload() }

        return execute(filteredMessages)
    }

    private fun AimoChatMessage.isEmptyPayload(): Boolean {
        return content.isNullOrBlank() &&
            thinking.isNullOrBlank() &&
            toolCalls.isNullOrEmpty() &&
            toolName.isNullOrBlank() &&
            toolCallId.isNullOrBlank()
    }
}







