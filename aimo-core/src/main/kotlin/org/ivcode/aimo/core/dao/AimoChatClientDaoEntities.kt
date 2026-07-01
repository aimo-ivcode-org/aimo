package org.ivcode.aimo.core.dao

import org.ivcode.aimo.core.AimoToolCall
import java.time.Instant
import java.util.UUID

data class ChatConversationEntity (
    val chatId: UUID,
    val metadata: Map<String, Any>
)

data class ChatRequestEntity (
    val chatId: UUID,
    val requestId: UUID,
    val messages: List<ChatMessageEntity>,
    val requestCharacters: Int,
    val createdAt: Instant,
)

data class ChatMessageEntity (
    val requestId: UUID,
    val messageId: Int,
    val type: String,
    val content: String?,
    val thinking: String?,
    val toolName: String?,
    val toolCallId: String? = null,
    val toolCalls: List<AimoToolCall>? = null,
)

