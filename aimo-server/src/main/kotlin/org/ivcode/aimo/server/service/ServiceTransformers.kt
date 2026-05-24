package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoHistoryRequest
import org.ivcode.aimo.core.AimoConversationInfo
import org.ivcode.aimo.core.AimoUsage
import org.ivcode.aimo.core.AimoPromptCacheUsage
import org.ivcode.aimo.server.model.ChatHistoryRequest
import org.ivcode.aimo.server.model.ChatMessage
import org.ivcode.aimo.server.model.ChatRequest
import org.ivcode.aimo.server.model.ChatResponse
import org.ivcode.aimo.server.model.ChatConversationInfo
import org.ivcode.aimo.server.model.ToolCall
import org.ivcode.aimo.server.model.ChatUsage
import org.ivcode.aimo.server.model.ChatPromptCacheUsage

internal fun ChatRequest.toAimoChatRequest(context: Map<String, Any> = emptyMap()) = AimoChatRequest (
    prompt = prompt,
    context = context
)

internal fun AimoChatResponse.toChatResponse(): ChatResponse = ChatResponse (
    chatId = chatId,
    responseId = responseId,
    messages = messages.map { it.toChatMessage() },
    createdAt = createdAt,
    usage = usage?.toChatUsage()
)

internal fun AimoChatMessage.toChatMessage() = ChatMessage(
    messageId = messageId,
    type = type.toRole(),
    content = content,
    thinking = thinking,
    toolName = toolName,
    toolCallId = toolCallId,
    toolCalls = toolCalls?.map { ToolCall(id = it.id, name = it.name, arguments = it.arguments) },
    done = done,
)

internal fun AimoChatMessageType.toRole(): ChatMessage.Role = when (this) {
    AimoChatMessageType.USER -> ChatMessage.Role.USER
    AimoChatMessageType.ASSISTANT -> ChatMessage.Role.ASSISTANT
    AimoChatMessageType.SYSTEM -> ChatMessage.Role.SYSTEM
    AimoChatMessageType.TOOL -> ChatMessage.Role.TOOL
}

internal fun AimoHistoryRequest.toChatHistoryRequest() = ChatHistoryRequest(
    chatId = chatId,
    requestId = requestId,
    messages = messages.map { it.toChatMessage() },
    createdAt = createdAt
)

internal fun AimoConversationInfo.toChatConversationInfo() = ChatConversationInfo(
    chatId = chatId,
)

internal fun AimoUsage.toChatUsage() = ChatUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    promptCache = promptCache?.toChatPromptCacheUsage()
)

internal fun AimoPromptCacheUsage.toChatPromptCacheUsage() = ChatPromptCacheUsage(
    cacheReadInputTokens = cacheReadInputTokens,
    cacheWriteInputTokens = cacheWriteInputTokens
)

