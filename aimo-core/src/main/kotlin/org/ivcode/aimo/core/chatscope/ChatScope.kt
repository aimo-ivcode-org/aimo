package org.ivcode.aimo.core.chatscope

/**
 * Defines autonomous decision-making capabilities for a conversation.
 *
 * ChatScope is purely metadata - it identifies which tools and system messages
 * apply to a conversation. System messages are invoked at chat time with
 * context-of-the-moment. ChatScope filtering ensures only scoped tools and
 * system messages are passed to AimoChatClientImpl.
 *
 * @property id Unique identifier (e.g., "global", "admin", "research")
 * @property displayName Human-readable name for UI display
 * @property description What this scope provides and is used for
 * @property toolNames Set of tool names available in this scope (tool definition name)
 * @property systemMessageNames Set of system message callback names available (by index as string)
 */
data class ChatScope(
    val id: String,
    val displayName: String,
    val description: String,
    val toolNames: Set<String>,
    val systemMessageNames: Set<String>
)

