package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatResponse
import org.ivcode.aimo.core.model.ToolCallback

internal interface PromptBudgeter {

    val maxContextSize: Long

    fun withPromptForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<ToolCallback>,
        history: List<AimoChatMessage>,
        execute: (promptMessages: List<AimoChatMessage>) -> AimoChatResponse,
    ): AimoChatResponse
}
