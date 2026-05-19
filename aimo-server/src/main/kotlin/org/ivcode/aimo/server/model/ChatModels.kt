package org.ivcode.aimo.server.model

import java.time.Instant
import java.util.UUID

data class ChatRequest (
    val prompt: String,
    val stream: Boolean = false
)

data class ChatResponse (
    val chatId: UUID,
    val responseId: UUID,
    val messages: List<ChatMessage>,
    val createdAt: Instant,
    val usage: ChatUsage? = null,
)

data class ChatMessage (
    val messageId: Int,
    val type: Role,
    val content: String?,
    val thinking: String?,
    val toolName: String?,
    val done: Boolean?,
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }
}

data class ChatHistoryRequest (
    val chatId: UUID,
    val requestId: UUID,
    val messages: List<ChatMessage>,
    val createdAt: Instant,
)

data class ChatUsage (
    val inputTokens: Int?,
    val outputTokens: Int?,
    val promptCache: ChatPromptCacheUsage? = null,
)

data class ChatPromptCacheUsage (
    val cacheReadInputTokens: Int = 0,
    val cacheWriteInputTokens: Int = 0,
)

