package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Factory for creating [Conversation] instances with optional interceptors.
 *
 * Metadata passed to [getConversation] is used for DAO-level filtering and scoping.
 * Interceptors can enrich the metadata before DAO operations.
 */
interface ConversationFactory {
    fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory

    /**
     * Get a conversation with optional metadata for DAO scoping.
     *
     * @param chatId The chat identifier
     * @param metadata Metadata for DAO scoping (e.g., tenant, user scope); interceptors may add entries
     * @return The conversation instance, or null if not found or access denied
     */
    fun getConversation(chatId: UUID, metadata: Map<String, Any> = emptyMap()): Conversation?
}
