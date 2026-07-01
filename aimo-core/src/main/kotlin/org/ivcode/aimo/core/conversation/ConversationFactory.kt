package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Factory for conversation access and deletion with optional interceptors.
 *
 * Metadata passed to methods is used for DAO-level filtering and scoping.
 * Interceptors can enrich metadata or implement cross-cutting concerns for each operation.
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

    /**
     * Delete a conversation with optional metadata for DAO scoping.
     *
     * @param chatId The chat identifier
     * @param metadata Metadata for DAO scoping (e.g., tenant, user scope); interceptors may add entries
     * @return true if the conversation was successfully deleted, false if not found or access denied
     */
    fun deleteConversation(chatId: UUID, metadata: Map<String, Any> = emptyMap()): Boolean
}
