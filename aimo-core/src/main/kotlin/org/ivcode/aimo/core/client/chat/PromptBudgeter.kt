package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.model.AimoToolCallback

internal interface PromptBudgeter {
    fun promptMessagesForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
        // Provider pulls history with an optional max-character hint.
        historyProvider: (chars: Long?) -> List<AimoChatMessage>,
    ): List<AimoChatMessage>
}





