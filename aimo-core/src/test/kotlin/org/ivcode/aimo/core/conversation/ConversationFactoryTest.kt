package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
import org.ivcode.aimo.core.dao.ChatMessageEntity
import org.ivcode.aimo.core.dao.ChatRequestEntity
import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.time.Instant
import java.util.UUID

class ConversationFactoryTest {

    @Test
    fun `factory creates and retrieves conversations with empty metadata`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)

        // Create a conversation with empty metadata
        val conversation = dao.createChatConversation(emptyMap())

        // Should retrieve with empty metadata
        val retrieved = factory.getConversation(conversation.chatId, emptyMap())
        assertNotNull(retrieved)
        assertEquals(conversation.chatId, retrieved.chatId)
    }

    @Test
    fun `factory creates and retrieves conversations with metadata`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)
        val metadata = mapOf("userId" to "user1", "tenant" to "acme")

        // Create a conversation with metadata
        val conversation = dao.createChatConversation(metadata)

        // Should retrieve with matching metadata
        val retrieved = factory.getConversation(conversation.chatId, metadata = metadata)
        assertNotNull(retrieved)
        assertEquals(conversation.chatId, retrieved.chatId)
    }

    @Test
    fun `factory fails to retrieve conversation with non-matching metadata`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)
        val metadata = mapOf("userId" to "user1", "tenant" to "acme")

        // Create a conversation with specific metadata
        val conversation = dao.createChatConversation(metadata)

        // Should not retrieve with different metadata
        val retrieved = factory.getConversation(conversation.chatId, metadata = mapOf("userId" to "user2"))
        assertNull(retrieved, "Should not retrieve conversation with non-matching metadata")
    }



    @Test
    fun `factory retrieves messages from conversation with metadata`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)
        val metadata = mapOf("userId" to "user1")

        val conversation = dao.createChatConversation(metadata)
        val requestId = UUID.randomUUID()

        // Add messages via DAO
        val request = ChatRequestEntity(
            chatId = conversation.chatId,
            requestId = requestId,
            messages = listOf(
                ChatMessageEntity(requestId, 1, "USER", "Hello", null, null)
            ),
            requestCharacters = 5,
            createdAt = Instant.now()
        )
        dao.addChatRequest(request, metadata)

        // Retrieve through factory with matching metadata
        val conv = factory.getConversation(conversation.chatId, metadata = metadata)
        assertNotNull(conv)
        val messages = conv.getMessages()
        assertNotNull(messages)
        assertEquals(1, messages.size)
        assertEquals("Hello", messages.first().content)
    }

    @Test
    fun `factory cannot retrieve messages with non-matching metadata`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)
        val metadata = mapOf("userId" to "user1")

        val conversation = dao.createChatConversation(metadata)
        val requestId = UUID.randomUUID()

        // Add messages via DAO with metadata
        val request = ChatRequestEntity(
            chatId = conversation.chatId,
            requestId = requestId,
            messages = listOf(
                ChatMessageEntity(requestId, 1, "USER", "Hello", null, null)
            ),
            requestCharacters = 5,
            createdAt = Instant.now()
        )
        dao.addChatRequest(request, metadata)

        // Try to retrieve with different metadata - should fail
        val conv = factory.getConversation(conversation.chatId, metadata = mapOf("userId" to "user2"))
        assertNull(conv, "Should not get conversation with non-matching metadata")
    }

    @Test
    fun `factory adds messages with matching metadata through conversation interface`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)
        val metadata = mapOf("userId" to "user1")

        val conversation = dao.createChatConversation(metadata)

        // Get conversation and add messages
        val conv = factory.getConversation(conversation.chatId, metadata = metadata)
        assertNotNull(conv)

        val requestId = UUID.randomUUID()
        val messages = listOf(
            AimoChatMessage(
                messageId = 1,
                type = AimoChatMessageType.USER,
                content = "Test message",
                thinking = null,
                toolName = null,
                toolCallId = null,
                toolCalls = null,
                done = null
            )
        )
        conv.addMessages(requestId, messages)

        // Verify messages were stored
        val retrieved = factory.getConversation(conversation.chatId, metadata = metadata)
        assertNotNull(retrieved)
        val storedMessages = retrieved.getMessages()
        assertNotNull(storedMessages)
        assertEquals(1, storedMessages.size)
        assertEquals("Test message", storedMessages.first().content)
    }

    @Test
    fun `factory with interceptor receives metadata in scope`() {
        val dao = AimoChatClientDaoMemory()
         val capturedGetMetadata = mutableListOf<Map<String, Any>>()

        val testInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                capturedGetMetadata.add(metadata.toMap())
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                return chain.proceed(chatId, metadata)
            }
        }

        val factory = ConversationFactoryImpl(dao).withInterceptor(testInterceptor)
        val metadata = mapOf("userId" to "user1", "tenant" to "acme")

        val conversation = dao.createChatConversation(metadata)
        val conv = factory.getConversation(conversation.chatId, metadata = metadata)

        // Interceptor should have received the scope metadata
        assertTrue(capturedGetMetadata.isNotEmpty(), "Interceptor should be called")
        assertTrue(capturedGetMetadata[0].containsKey("userId"), "Interceptor should receive userId in metadata")
        assertEquals("user1", capturedGetMetadata[0]["userId"], "Scope metadata should be passed through")
    }

    @Test
    fun `factory with interceptor can modify metadata before DAO call`() {
        val dao = AimoChatClientDaoMemory()
        val metadataEnricher = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                // Add required scope key that enables access
                metadata["userId"] = "user1"
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                metadata["userId"] = "user1"
                return chain.proceed(chatId, metadata)
            }
        }

        val factory = ConversationFactoryImpl(dao).withInterceptor(metadataEnricher)

        // Create conversation WITH userId scope
        val conversation = dao.createChatConversation(mapOf("userId" to "user1"))

        // Request with empty metadata (no scope)
        // Interceptor will ADD userId=user1 to metadata, making DAO lookup succeed
        val conv = factory.getConversation(conversation.chatId, metadata = emptyMap())

        // Verify conversation was retrieved because interceptor enriched metadata
        assertNotNull(conv, "Conversation should be retrieved after metadata enrichment by interceptor")
        assertEquals(conversation.chatId, conv.chatId)
    }

    @Test
    fun `factory with multiple interceptors chains correctly with metadata for get operation`() {
        val dao = AimoChatClientDaoMemory()
        val callOrder = mutableListOf<String>()

        val interceptor1 = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                callOrder.add("interceptor1_before")
                val result = chain.proceed(chatId, metadata)
                callOrder.add("interceptor1_after")
                return result
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                callOrder.add("interceptor1_delete_before")
                val result = chain.proceed(chatId, metadata)
                callOrder.add("interceptor1_delete_after")
                return result
            }
        }

        val interceptor2 = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                callOrder.add("interceptor2_before")
                val result = chain.proceed(chatId, metadata)
                callOrder.add("interceptor2_after")
                return result
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                callOrder.add("interceptor2_delete_before")
                val result = chain.proceed(chatId, metadata)
                callOrder.add("interceptor2_delete_after")
                return result
            }
        }

        val factory = ConversationFactoryImpl(dao)
            .withInterceptor(interceptor1)
            .withInterceptor(interceptor2)

        val metadata = mapOf("userId" to "user1")
        val conversation = dao.createChatConversation(metadata)
        val conv = factory.getConversation(conversation.chatId, metadata = metadata)

        // Interceptors should be called in order for get
        assertEquals(
            listOf("interceptor1_before", "interceptor2_before", "interceptor2_after", "interceptor1_after"),
            callOrder
        )
    }

    @Test
    fun `factory with multiple interceptors chains correctly with metadata for delete operation`() {
        val dao = AimoChatClientDaoMemory()
        val callOrder = mutableListOf<String>()

        val interceptor1 = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                callOrder.add("interceptor1_delete_before")
                val result = chain.proceed(chatId, metadata)
                callOrder.add("interceptor1_delete_after")
                return result
            }
        }

        val interceptor2 = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                callOrder.add("interceptor2_delete_before")
                val result = chain.proceed(chatId, metadata)
                callOrder.add("interceptor2_delete_after")
                return result
            }
        }

        val factory = ConversationFactoryImpl(dao)
            .withInterceptor(interceptor1)
            .withInterceptor(interceptor2)

        val metadata = mapOf("userId" to "user1")
        val conversation = dao.createChatConversation(metadata)

        // Delete the conversation
        val deleted = factory.deleteConversation(conversation.chatId, metadata = metadata)

        // Interceptors should be called in order for delete
        assertTrue(deleted, "Delete should succeed")
        assertEquals(
            listOf("interceptor1_delete_before", "interceptor2_delete_before", "interceptor2_delete_after", "interceptor1_delete_after"),
            callOrder
        )
    }

    @Test
    fun `factory metadata scoping filters based on AND logic`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)

        // Create three conversations with different metadata
        val conv1 = dao.createChatConversation(mapOf("userId" to "user1", "tenant" to "acme"))
        val conv2 = dao.createChatConversation(mapOf("userId" to "user2", "tenant" to "acme"))
        val conv3 = dao.createChatConversation(mapOf("userId" to "user1", "tenant" to "globex"))

        // User1 + acme should only get conv1
        val retrieved1 = factory.getConversation(conv1.chatId, mapOf("userId" to "user1", "tenant" to "acme"))
        assertNotNull(retrieved1)
        assertEquals(conv1.chatId, retrieved1.chatId)

        // User2 + acme should only get conv2
        val retrieved2 = factory.getConversation(conv2.chatId, mapOf("userId" to "user2", "tenant" to "acme"))
        assertNotNull(retrieved2)
        assertEquals(conv2.chatId, retrieved2.chatId)

        // User1 + globex should only get conv3
        val retrieved3 = factory.getConversation(conv3.chatId, mapOf("userId" to "user1", "tenant" to "globex"))
        assertNotNull(retrieved3)
        assertEquals(conv3.chatId, retrieved3.chatId)

        // Cross-scope attempts should fail
        val wrongUser = factory.getConversation(conv1.chatId, mapOf("userId" to "user2", "tenant" to "acme"))
        assertNull(wrongUser)

        val wrongTenant = factory.getConversation(conv1.chatId, mapOf("userId" to "user1", "tenant" to "globex"))
        assertNull(wrongTenant)
    }

    @Test
    fun `factory returns null for non-existent conversation even with metadata`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)

        val nonExistentId = UUID.randomUUID()
        val metadata = mapOf("userId" to "user1")

        // Try to get non-existent conversation
        val result = factory.getConversation(nonExistentId, metadata = metadata)
        assertNull(result)
    }

    @Test
    fun `deleteConversation returns false for non-existent conversation`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)

        val nonExistentId = UUID.randomUUID()
        val metadata = mapOf("userId" to "user1")

        // Try to delete non-existent conversation
        val result = factory.deleteConversation(nonExistentId, metadata = metadata)
        assertFalse(result, "Delete should return false for non-existent conversation")
    }

    @Test
    fun `deleteConversation with matching metadata succeeds`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)
        val metadata = mapOf("userId" to "user1", "tenant" to "acme")

        val conversation = dao.createChatConversation(metadata)

        // Delete with matching metadata should succeed
        val deleted = factory.deleteConversation(conversation.chatId, metadata = metadata)
        assertTrue(deleted, "Delete should succeed with matching metadata")

        // Verify conversation is gone
        val retrieved = factory.getConversation(conversation.chatId, metadata = metadata)
        assertNull(retrieved, "Conversation should be deleted")
    }

    @Test
    fun `deleteConversation with non-matching metadata fails`() {
        val dao = AimoChatClientDaoMemory()
        val factory = ConversationFactoryImpl(dao)
        val metadata = mapOf("userId" to "user1", "tenant" to "acme")

        val conversation = dao.createChatConversation(metadata)

        // Try to delete with wrong metadata
        val deleted = factory.deleteConversation(conversation.chatId, metadata = mapOf("userId" to "user2"))
        assertFalse(deleted, "Delete should fail with non-matching metadata")

        // Verify conversation still exists
        val retrieved = factory.getConversation(conversation.chatId, metadata = metadata)
        assertNotNull(retrieved, "Conversation should still exist")
    }

    @Test
    fun `factory with interceptor receives metadata in delete scope`() {
        val dao = AimoChatClientDaoMemory()
        val capturedDeleteMetadata = mutableListOf<Map<String, Any>>()

        val testInterceptor = object : ConversationInterceptor {
            override fun interceptGet(chain: ConversationInterceptor.GetChain, chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                capturedDeleteMetadata.add(metadata.toMap())
                return chain.proceed(chatId, metadata)
            }
        }

        val factory = ConversationFactoryImpl(dao).withInterceptor(testInterceptor)
        val metadata = mapOf("userId" to "user1", "tenant" to "acme")

        val conversation = dao.createChatConversation(metadata)
        val deleted = factory.deleteConversation(conversation.chatId, metadata = metadata)

        // Interceptor should have received the metadata
        assertTrue(deleted, "Delete should succeed")
        assertTrue(capturedDeleteMetadata.isNotEmpty(), "Interceptor should be called for delete")
        assertTrue(capturedDeleteMetadata[0].containsKey("userId"), "Interceptor should receive userId in metadata")
        assertEquals("user1", capturedDeleteMetadata[0]["userId"], "Delete metadata should be passed through")
    }

    @Test
    fun `factory with interceptor can prevent delete`() {
        val dao = AimoChatClientDaoMemory()

        val blockingInterceptor = object : ConversationInterceptor {
            override fun interceptGet(
                chain: ConversationInterceptor.GetChain,
                chatId: UUID,
                metadata: MutableMap<String, Any>
            ): Conversation? {
                return chain.proceed(chatId, metadata)
            }

            override fun interceptDelete(chain: ConversationInterceptor.DeleteChain, chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                // Block delete
                return false
            }
        }

        val factory = ConversationFactoryImpl(dao).withInterceptor(blockingInterceptor)
        val metadata = mapOf("userId" to "user1")

        val conversation = dao.createChatConversation(metadata)

        // Try to delete - interceptor should block it
        val deleted = factory.deleteConversation(conversation.chatId, metadata = metadata)
        assertFalse(deleted, "Delete should be blocked by interceptor")

        // Verify conversation still exists
        val retrieved = factory.getConversation(conversation.chatId, metadata = metadata)
        assertNotNull(retrieved, "Conversation should still exist after blocked delete")
    }
}
