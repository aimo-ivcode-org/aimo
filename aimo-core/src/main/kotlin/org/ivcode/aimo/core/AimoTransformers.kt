package org.ivcode.aimo.core

import org.ivcode.aimo.core.dao.ChatMessageEntity
import org.ivcode.aimo.core.dao.ChatRequestEntity
import org.ivcode.aimo.core.dao.ChatConversationEntity
import java.util.UUID

internal fun ChatConversationEntity.toAimoConversationInfo(): AimoConversationInfo = AimoConversationInfo (
    chatId = chatId,
    metadata = metadata
)

internal fun ChatRequestEntity.toAimoHistoryRequest(): AimoHistoryRequest = AimoHistoryRequest(
    chatId = chatId,
    requestId = requestId,
    messages = messages.map { it.toAimoChatMessage() },
    createdAt = createdAt
)

internal fun ChatMessageEntity.toAimoChatMessage() = AimoChatMessage (
    messageId = messageId,
    type = type.toAimoChatMessageType(),
    content = content,
    thinking = thinking,
    toolName = toolName,
    toolCallId = toolCallId,
    toolCalls = toolCalls,
    done = true,
)

private fun String.toAimoChatMessageType(): AimoChatMessageType = when(this) {
    "SYSTEM" -> AimoChatMessageType.SYSTEM
    "USER" -> AimoChatMessageType.USER
    "ASSISTANT" -> AimoChatMessageType.ASSISTANT
    "TOOL" -> AimoChatMessageType.TOOL
    else -> throw IllegalArgumentException("Unknown message type: $this")
}

internal fun AimoChatMessage.toChatMessageEntity(
    responseId: UUID
) = ChatMessageEntity (
    requestId = responseId,
    messageId = messageId,
    type = type.name,
    content = content,
    thinking = thinking,
    toolName = toolName,
    toolCallId = toolCallId,
    toolCalls = toolCalls,
)