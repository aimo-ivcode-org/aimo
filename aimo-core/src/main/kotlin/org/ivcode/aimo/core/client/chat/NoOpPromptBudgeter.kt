package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
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
    override fun promptMessagesForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
        historyProvider: (Long?) -> List<AimoChatMessage>,
    ): List<AimoChatMessage> {
        val history = historyProvider(null)
        val promptMessages = systemMessages + history + prompt + taskMessages

        return promptMessages
            .let { messages ->
                if (!excludeThinking) messages
                else messages.map { message ->
                    if (message.thinking == null) message else message.copy(thinking = null)
                }
            }
            .filterNot { it.isEmptyPayload() }
    }

    private fun AimoChatMessage.isEmptyPayload(): Boolean {
        return content.isNullOrBlank() &&
            thinking.isNullOrBlank() &&
            toolCalls.isNullOrEmpty() &&
            toolName.isNullOrBlank() &&
            toolCallId.isNullOrBlank()
    }
}







