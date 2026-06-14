package org.ivcode.aimo.core.conversation

private const val CHAT_SCOPE_ID_KEY = "aimo.chatScopeId"

/**
 * Get the selected chat scope ID for this conversation.
 *
 * @return Chat scope ID or null if not set (defaults to "global")
 */
fun Conversation.getSelectedChatScope(): String? {
    return this.getChatProperty(CHAT_SCOPE_ID_KEY) as? String
}

/**
 * Set the chat scope for this conversation.
 *
 * @param chatScopeId The scope ID to set
 */
fun Conversation.setSelectedChatScope(chatScopeId: String) {
    this.writeChatProperty(CHAT_SCOPE_ID_KEY, chatScopeId)
}

/**
 * Clear the chat scope selection (reverts to global).
 *
 * @return true if the property was deleted, false if it didn't exist
 */
fun Conversation.clearSelectedChatScope(): Boolean {
    return this.deleteChatProperty(CHAT_SCOPE_ID_KEY)
}

