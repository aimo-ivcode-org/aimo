package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationImplTest {

    @Test
    fun `chat metadata reads latest durable values from dao`() {
        val dao = AimoChatClientDaoMemory()
        val chatEntity = dao.createChatConversation(metadata = mapOf("title" to "first"))
        val conversation = createConversation(dao, chatEntity.chatId)

        dao.upsertConversationMetadata(chatEntity.chatId, metadata = mapOf("title" to "second", "status" to "open"))

        assertEquals("second", conversation.getChatProperty("title"))
        assertEquals(
            mapOf("title" to "second", "status" to "open"),
            conversation.getChatMetadata(),
        )
    }

    @Test
    fun `scoped conversation only sees matching metadata`() {
        val dao = AimoChatClientDaoMemory()
        val chatEntity = dao.createChatConversation(mapOf("tenant" to "acme", "title" to "scoped"))

        val scoped = ConversationImpl(chatEntity.chatId, dao, mapOf("tenant" to "acme"))
        val wrongScope = ConversationImpl(chatEntity.chatId, dao, mapOf("tenant" to "other"))

        assertEquals("scoped", scoped.getChatProperty("title"))
        assertNull(wrongScope.getChatProperty("title"))
        assertEquals(emptyMap(), wrongScope.getChatMetadata())
    }

    @Test
    fun `chat property writes and deletes are visible across conversations`() {
        val dao = AimoChatClientDaoMemory()
        val chatEntity = dao.createChatConversation()
        val firstConversation = createConversation(dao, chatEntity.chatId)
        val secondConversation = createConversation(dao, chatEntity.chatId)

        firstConversation.writeChatProperty("title", "Shared")
        assertEquals("Shared", secondConversation.getChatProperty("title"))

        assertTrue(secondConversation.deleteChatProperty("title"))
        assertNull(firstConversation.getChatProperty("title"))
    }

    private fun createConversation(
        dao: AimoChatClientDaoMemory,
        chatId: UUID,
    ): Conversation {
        dao.getChatConversation(chatId)
            ?: throw IllegalStateException("Conversation not found")
        return ConversationImpl(
            chatId = chatId,
            conversationStore = dao,
        )
    }
}
