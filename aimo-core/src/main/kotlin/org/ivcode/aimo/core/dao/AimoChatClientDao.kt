package org.ivcode.aimo.core.dao

import java.util.UUID

/**
 * Data Access Object (DAO) for managing chat conversations, requests, and messages in aimo.
 *
 * Read, update, and delete operations accept optional [scopeMetadata]. When non-empty, only
 * conversations whose stored metadata matches every entry in [scopeMetadata] are returned or
 * modified. An empty [scopeMetadata] performs no scoping.
 *
 * Use [org.ivcode.aimo.core.conversation.ConversationInterceptor]s to add or enrich metadata
 * on writes; use [scopeMetadata] on DAO calls for filtering.
 *
 * @see ConversationMetadataMatcher
 */
interface AimoChatClientDao {

    fun createChatConversation(metadata: Map<String, Any> = emptyMap()): ChatConversationEntity

    fun getChatConversations(scopeMetadata: Map<String, Any> = emptyMap()): List<ChatConversationEntity>

    fun getChatConversation(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): ChatConversationEntity?

    fun deleteChatConversation(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): Boolean

    fun addChatRequest(request: ChatRequestEntity, scopeMetadata: Map<String, Any> = emptyMap()): Boolean

    fun getChatRequests(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): List<ChatRequestEntity>

    fun getChatRequests(
        chatId: UUID,
        maxRequestCharacters: Long,
        scopeMetadata: Map<String, Any> = emptyMap(),
    ): List<ChatRequestEntity>

    fun getMessages(chatId: UUID, scopeMetadata: Map<String, Any> = emptyMap()): List<ChatMessageEntity>

    fun upsertConversationMetadata(
        chatId: UUID,
        metadata: Map<String, Any>,
        scopeMetadata: Map<String, Any> = emptyMap(),
    ): Boolean

    fun deleteConversationMetadata(
        chatId: UUID,
        keys: List<String>,
        scopeMetadata: Map<String, Any> = emptyMap(),
    ): Boolean
}
