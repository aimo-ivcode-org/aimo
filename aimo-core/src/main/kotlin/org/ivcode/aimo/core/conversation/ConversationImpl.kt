package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.ChatMessageEntity
import org.ivcode.aimo.core.dao.ChatRequestEntity
import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import java.time.Instant
import java.util.UUID

/**
 * [Conversation] backed by [AimoChatClientDao]. All DAO calls pass [scopeMetadata] for filtering.
 */
class ConversationImpl(
    override val chatId: UUID,
    private val conversationStore: AimoChatClientDao,
    private val scopeMetadata: Map<String, Any> = emptyMap(),
) : Conversation {

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
        val entities = if (maxCacheCharacters != null && maxCacheCharacters > 0) {
            conversationStore.getChatRequests(chatId, maxCacheCharacters, scopeMetadata)
        } else {
            conversationStore.getChatRequests(chatId, scopeMetadata)
        }

        if (entities.isEmpty()) {
            return null
        }

        return entities.flatMap { request -> request.messages.map { it.toAimoChatMessage() } }
    }

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) {
        require(messages.isNotEmpty()) { "Cannot add empty messages list to conversation" }

        val messageEntities = messages.map { message ->
            ChatMessageEntity(
                requestId = requestId,
                messageId = message.messageId,
                type = message.type.name,
                content = message.content,
                thinking = message.thinking,
                toolName = message.toolName,
                toolCallId = message.toolCallId,
                toolCalls = message.toolCalls
            )
        }

        val requestCharacters = messageEntities.sumOf {
            (it.content?.length ?: 0) + (it.thinking?.length ?: 0)
        }

        val request = ChatRequestEntity(
            chatId = chatId,
            requestId = requestId,
            messages = messageEntities,
            requestCharacters = requestCharacters,
            createdAt = Instant.now()
        )

        val success = conversationStore.addChatRequest(request, scopeMetadata)
        check(success) { "Failed to persist conversation messages for chat $chatId" }
    }

    override fun getChatMetadata(): Map<String, Any> {
        val conversation = conversationStore.getChatConversation(chatId, scopeMetadata) ?: return emptyMap()
        return conversation.metadata
    }

    override fun getChatProperty(property: String): Any? {
        val conversation = conversationStore.getChatConversation(chatId, scopeMetadata) ?: return null
        return conversation.metadata[property]
    }

    override fun writeChatProperty(property: String, value: Any) {
        val success = conversationStore.upsertConversationMetadata(chatId, mapOf(property to value), scopeMetadata)
        check(success) { "Failed to persist conversation metadata for chat $chatId" }
    }

    override fun deleteChatProperty(property: String): Boolean {
        return conversationStore.deleteConversationMetadata(chatId, listOf(property), scopeMetadata)
    }
}

private fun ChatMessageEntity.toAimoChatMessage(): AimoChatMessage {
    return AimoChatMessage(
        messageId = this.messageId,
        type = AimoChatMessageType.valueOf(this.type),
        content = this.content,
        thinking = this.thinking,
        toolName = this.toolName,
        toolCallId = this.toolCallId,
        toolCalls = this.toolCalls,
        done = null
    )
}
