package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback

/**
 * Defines autonomous decision-making capabilities for a conversation.
 *
 * ChatScope contains the actual tools and system messages that are available
 * in this scope. The scope is self-contained and ready to use - no filtering needed.
 *
 * @property id Unique identifier (e.g., "global", "admin", "research")
 * @property displayName Human-readable name for UI display
 * @property description What this scope provides and is used for
 * @property tools List of tool callbacks available in this scope
 * @property systemMessages List of system message callbacks available in this scope
 */
data class ChatScope(
    val id: String,
    val displayName: String,
    val description: String,
    val tools: List<ToolCallback>,
    val systemMessages: List<SystemMessageCallback>
)

