package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.model.AimoToolCallback

internal interface PromptBudgeter {
    fun withPromptForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
        historyProvider: (chars: Long?) -> List<AimoChatMessage>,
        execute: (promptMessages: List<AimoChatMessage>) -> AimoChatResponse,
    ): AimoChatResponse
}





