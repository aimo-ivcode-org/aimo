package org.ivcode.aimo.core

import java.time.Instant
import java.util.UUID


enum class AimoChatMessageType {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

data class AimoToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AimoChatResponse (
    val chatId: UUID,
    val responseId: UUID,
    val messages: List<AimoChatMessage>,
    val createdAt: Instant,
)

data class AimoChatMessage (
    val messageId: Int,
    val type: AimoChatMessageType,
    val content: String?,
    val thinking: String?,
    val toolName: String?,
    val toolCallId: String? = null,
    val toolCalls: List<AimoToolCall>? = null,
    val done: Boolean?,
)

data class AimoChatRequest (
    val prompt: String,
    val context: Map<String, Any>,
)

data class AimoHistoryRequest (
    val chatId: UUID,
    val requestId: UUID,
    val messages: List<AimoChatMessage>,
    val createdAt: Instant,
)

data class AimoSession (
    val chatId: UUID,
    val metadata: Map<String, Any>
)
