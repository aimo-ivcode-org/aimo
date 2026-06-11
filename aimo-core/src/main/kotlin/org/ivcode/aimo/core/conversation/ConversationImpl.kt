package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.ChatConversationEntity
import org.ivcode.aimo.core.dao.ChatMessageEntity
import org.ivcode.aimo.core.dao.ChatRequestEntity
import java.time.Instant
import java.util.UUID

/**
 * Concrete implementation of [Conversation] backed by a [AimoChatClientDao].
 *
 * All operations are scoped to the specified `chatId`. The `userId` is stored for
 * use by security interceptors for access control checks.
 *
 * This implementation delegates to the DAO with the stored `chatId` for all operations.
 *
 * @property chatId The unique identifier for the conversation
 * @property conversationStore The backing storage (DAO)
 * @property userId The user who owns this conversation (used for access control)
 * @property entity Optional pre-fetched conversation entity (avoids redundant DAO fetch on initialization)
 */
class ConversationImpl(
    override val chatId: UUID,
    private val conversationStore: AimoChatClientDao,
    private val userId: String,
    entity: ChatConversationEntity? = null
) : Conversation {

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
        val entities = if (maxCacheCharacters != null && maxCacheCharacters > 0) {
            conversationStore.getChatRequests(userId, chatId, maxCacheCharacters.toInt())
        } else {
            conversationStore.getChatRequests(userId, chatId)
        }

        if (entities.isEmpty()) {
            return null
        }

        return entities.flatMap { request -> request.messages.map { it.toAimoChatMessage() } }
    }

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) {
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

        // Calculate total characters for the request
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

        conversationStore.addChatRequest(userId, request)
    }

    override fun getChatMetadata(): Map<String, Any> {
        val conversation = conversationStore.getChatConversation(chatId, userId) ?: return emptyMap()
        return conversation.metadata
    }

    override fun getChatProperty(property: String): Any? {
        val conversation = conversationStore.getChatConversation(chatId, userId) ?: return null
        return conversation.metadata[property]
    }


    override fun writeChatProperty(property: String, value: Any) {
        conversationStore.upsertConversationMetadata(chatId, userId, mapOf(property to value))
    }

    override fun deleteChatProperty(property: String): Boolean {
        return conversationStore.deleteConversationMetadata(chatId, userId, listOf(property))
    }
}

/**
 * Extension function to convert ChatMessageEntity to AimoChatMessage.
 */
private fun ChatMessageEntity.toAimoChatMessage(): AimoChatMessage {
    return AimoChatMessage(
        messageId = this.messageId,
        type = AimoChatMessageType.valueOf(this.type),
        content = this.content,
        thinking = this.thinking,
        toolName = this.toolName,
        toolCallId = this.toolCallId,
        toolCalls = this.toolCalls,
        done = null // Not available in entity
    )
}






