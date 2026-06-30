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
    /**
     * Token usage reported by the model for this call.
     * `null` when the provider did not supply usage information.
     */
    val usage: AimoUsage? = null,
)

/**
 * Token usage for a single model call.
 *
 * @property inputTokens  Total prompt/input tokens charged to this call.
 * @property outputTokens Tokens generated in the response.
 * @property promptCache  Populated only when the provider supports prompt caching and
 *                        a cache point was active during this call.
 */
data class AimoUsage (
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val promptCache: AimoPromptCacheUsage? = null,
)

/**
 * Per-call prompt-cache token breakdown.
 *
 * @property cacheReadInputTokens  Tokens served from the provider's cache (not reprocessed).
 * @property cacheWriteInputTokens Tokens written into the provider's cache during this call.
 */
data class AimoPromptCacheUsage(
    val cacheReadInputTokens: Int = 0,
    val cacheWriteInputTokens: Int = 0,
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

data class AimoConversationInfo (
    val chatId: UUID,
    val metadata: Map<String, Any>
)
