package org.ivcode.aimo.core.chatservice

/**
 * Context passed to system message callbacks at chat execution time.
 *
 * @property context Request context map containing:
 *           - chatId: UUID
 *           - requestId: UUID
 *           - conversation-client: Conversation
 *           Plus any caller-provided context from the chat request
 * @property chatScopeId The currently active chat scope ID (or null if not set)
 */
class SystemMessageContext (
    val context: Map<String, Any>,
    val chatScopeId: String? = null
)

