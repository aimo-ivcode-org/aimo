package org.ivcode.aimo.core.conversation

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MemoryConversationFactoryTest {
    @Test
    fun `lifecycle operations enforce metadata scope`() {
        val factory = MemoryConversationFactory()
        val tenantA = factory.createConversation(mapOf("tenant" to "a"))
        val tenantB = factory.createConversation(mapOf("tenant" to "b"))

        assertNotNull(factory.getConversation(tenantA.chatId, mapOf("tenant" to "a")))
        assertNull(factory.getConversation(tenantA.chatId, mapOf("tenant" to "b")))
        assertEquals(
            listOf(tenantA.chatId),
            factory.getConversations(mapOf("tenant" to "a")).map { it.chatId }
        )
        assertFalse(factory.deleteConversation(tenantA.chatId, mapOf("tenant" to "b")))
        assertNotNull(factory.getConversation(tenantA.chatId, mapOf("tenant" to "a")))
        assertTrue(factory.deleteConversation(tenantA.chatId, mapOf("tenant" to "a")))
        assertNull(factory.getConversation(tenantA.chatId, mapOf("tenant" to "a")))
        assertNotNull(factory.getConversation(tenantB.chatId, mapOf("tenant" to "b")))
    }

    @Test
    fun `interceptors execute in registration order for lifecycle operations`() {
        val events = mutableListOf<String>()
        val factory = MemoryConversationFactory()
            .withInterceptor(RecordingInterceptor("first", events))
            .withInterceptor(RecordingInterceptor("second", events))

        val conversation = factory.createConversation()
        assertEquals(
            listOf("first:create:before", "second:create:before", "second:create:after", "first:create:after"),
            events
        )

        events.clear()
        assertNotNull(factory.getConversation(conversation.chatId))
        assertEquals(
            listOf("first:get:before", "second:get:before", "second:get:after", "first:get:after"),
            events
        )

        events.clear()
        assertEquals(listOf(conversation.chatId), factory.getConversations().map { it.chatId })
        assertEquals(
            listOf("first:list:before", "second:list:before", "second:list:after", "first:list:after"),
            events
        )

        events.clear()
        assertTrue(factory.deleteConversation(conversation.chatId))
        assertEquals(
            listOf("first:delete:before", "second:delete:before", "second:delete:after", "first:delete:after"),
            events
        )
    }

    private class RecordingInterceptor(
        private val name: String,
        private val events: MutableList<String>
    ) : ConversationInterceptor {
        override fun interceptCreate(
            chain: ConversationInterceptor.CreateChain,
            metadata: MutableMap<String, Any>
        ): Conversation {
            events.add("$name:create:before")
            return chain.proceed(metadata).also { events.add("$name:create:after") }
        }

        override fun interceptGet(
            chain: ConversationInterceptor.GetChain,
            chatId: UUID,
            metadata: MutableMap<String, Any>
        ): Conversation? {
            events.add("$name:get:before")
            return chain.proceed(chatId, metadata).also { events.add("$name:get:after") }
        }

        override fun interceptList(
            chain: ConversationInterceptor.ListChain,
            metadata: MutableMap<String, Any>
        ): List<Conversation> {
            events.add("$name:list:before")
            return chain.proceed(metadata).also { events.add("$name:list:after") }
        }

        override fun interceptDelete(
            chain: ConversationInterceptor.DeleteChain,
            chatId: UUID,
            metadata: MutableMap<String, Any>
        ): Boolean {
            events.add("$name:delete:before")
            return chain.proceed(chatId, metadata).also { events.add("$name:delete:after") }
        }
    }
}
