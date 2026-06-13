package org.ivcode.aimo.core.client.conversation

import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.conversation.ConversationImpl
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
        val chatEntity = dao.createChatConversation(userId = "user1", metadata = mapOf("title" to "first"))
        val conversation = createConversation(dao, chatEntity.chatId, "user1")

        dao.upsertConversationMetadata(chatEntity.chatId, userId = "user1", metadata = mapOf("title" to "second", "status" to "open"))

        assertEquals("second", conversation.getChatProperty("title"))
        assertEquals(
            mapOf("title" to "second", "status" to "open"),
            conversation.getChatMetadata(),
        )
    }

    @Test
    fun `chat property writes and deletes are visible across conversations`() {
        val dao = AimoChatClientDaoMemory()
        val chatEntity = dao.createChatConversation("user1")
        val firstConversation = createConversation(dao, chatEntity.chatId, "user1")
        val secondConversation = createConversation(dao, chatEntity.chatId, "user1")

         firstConversation.writeChatProperty("title", "Shared")
         assertEquals("Shared", secondConversation.getChatProperty("title"))

         assertTrue(secondConversation.deleteChatProperty("title"))
         assertNull(firstConversation.getChatProperty("title"))
     }

     private fun createConversation(
         dao: AimoChatClientDaoMemory,
         chatId: UUID,
         userId: String,
     ): Conversation {
        dao.getChatConversation(chatId, userId)
            ?: throw IllegalStateException("Conversation not found")
        return ConversationImpl(
            chatId = chatId,
            conversationStore = dao,
            userId = userId,
        )
     }
}

