package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoToolCall

internal fun createUserMessage (
    messageId: Int,
    type: AimoChatMessageType = AimoChatMessageType.USER,
    content: String,
) = AimoChatMessage(
    messageId = messageId,
    type = type,
    content = content,
    thinking = null,
    toolName = null,
    toolCallId = null,
    toolCalls = null,
    done = true,
)

internal fun createToolMessage (
    messageId: Int,
    type: AimoChatMessageType = AimoChatMessageType.TOOL,
    content: String,
    toolName: String,
    toolCallId: String,
) = AimoChatMessage(
    messageId = messageId,
    type = type,
    content = content,
    thinking = null,
    toolName = toolName,
    toolCallId = toolCallId,
    toolCalls = null,
    done = true,
)

internal fun createAssistantMessage (
    messageId: Int,
    type: AimoChatMessageType = AimoChatMessageType.ASSISTANT,
    content: String?,
    thinking: String?,
    toolCalls: List<AimoToolCall>? = null,
    done: Boolean?,
) = AimoChatMessage(
    messageId = messageId,
    type = type,
    content = content,
    thinking = thinking,
    toolName = null,
    toolCallId = null,
    toolCalls = toolCalls,
    done = done,
)

internal fun createSystemMessage (
    messageId: Int,
    type: AimoChatMessageType = AimoChatMessageType.SYSTEM,
    content: String?
) = AimoChatMessage(
    messageId = messageId,
    type = type,
    content = content,
    thinking = null,
    toolName = null,
    toolCallId = null,
    toolCalls = null,
    done = true,
)
