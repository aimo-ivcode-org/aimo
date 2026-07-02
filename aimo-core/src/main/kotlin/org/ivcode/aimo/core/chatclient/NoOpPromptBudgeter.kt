package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.model.ToolCallback

/**
 * Prompt budgeter that performs no token-based truncation.
 *
 * It composes the full prompt in canonical order and optionally strips thinking
 * content when configured.
 */
internal class NoOpPromptBudgeter(
    private val excludeThinking: Boolean = false,
) : PromptBudgeter {
    override val maxContextSize: Long = Long.MAX_VALUE

    override fun withPromptForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<ToolCallback>,
        history: List<AimoChatMessage>,
        execute: (promptMessages: List<AimoChatMessage>) -> AimoChatResponse,
    ): AimoChatResponse {
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