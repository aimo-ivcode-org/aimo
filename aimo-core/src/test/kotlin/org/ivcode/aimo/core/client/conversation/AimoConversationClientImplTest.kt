package org.ivcode.aimo.core.client.conversation

import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoPrompt
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimoConversationClientImplTest {

     @Test
     fun `chat metadata reads latest durable values from dao`() {
         val dao = AimoChatClientDaoMemory()
         val conversation = dao.createChatConversation(mapOf("title" to "first"))
         val client = createConversationClient(dao, conversation.chatId)

         dao.upsertConversationMetadata(conversation.chatId, mapOf("title" to "second", "status" to "open"))

         assertEquals("second", client.getChatProperty("title"))
         assertEquals(
             mapOf("title" to "second", "status" to "open"),
             client.getChatMetadata(),
         )
     }

     @Test
     fun `chat property writes and deletes are visible across conversation clients`() {
         val dao = AimoChatClientDaoMemory()
         val conversation = dao.createChatConversation()
         val firstClient = createConversationClient(dao, conversation.chatId)
         val secondClient = createConversationClient(dao, conversation.chatId)

         firstClient.writeChatProperty("title", "Shared")
         assertEquals("Shared", secondClient.getChatProperty("title"))

         assertTrue(secondClient.deleteChatProperty("title"))
         assertNull(firstClient.getChatProperty("title"))
     }

     private fun createConversationClient(
         dao: AimoChatClientDaoMemory,
         chatId: UUID,
     ): AimoConversationClientImpl {
         return AimoConversationClientImpl(
             chatId = chatId,
             dao = dao,
             model = testModel(),
             tools = emptyList(),
             systemMessages = emptyList(),
         )
     }

     private fun testModel(): AimoChatModel {
         return AimoChatModel(
             name = "test",
             chatEngine = object : AimoChatEngine {
                 override val options = AimoChatOptions()

                 override fun call(prompt: AimoPrompt): AimoChatResponse {
                     throw UnsupportedOperationException("Not used in conversation client tests")
                 }

                 override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                     throw UnsupportedOperationException("Not used in conversation client tests")
                 }
             },
             options = AimoChatOptions(),
         )
     }
}

