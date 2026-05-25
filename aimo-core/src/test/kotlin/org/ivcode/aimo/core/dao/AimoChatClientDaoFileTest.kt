package org.ivcode.aimo.core.dao

import org.ivcode.aimo.core.AimoToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.time.Instant
import java.util.UUID

class AimoChatClientDaoFileTest {

    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "aimo-test-${UUID.randomUUID()}")
        dir.mkdirs()
        return dir
    }

    @Test
    fun `file dao creates and retrieves conversations`() {
        val dataDir = createTempDir()
        val dao = AimoChatClientDaoFile(dataDir)

        val conversation = dao.createChatConversation(userId = "user1")
        assertNotNull(conversation)
        assertEquals("user1", conversation.userId)

        val retrieved = dao.getChatConversation(conversation.chatId)
        assertNotNull(retrieved)
        assertEquals(conversation.chatId, retrieved.chatId)
        assertEquals("user1", retrieved.userId)

        dataDir.deleteRecursively()
    }

    @Test
    fun `file dao persists conversations across instances`() {
        val dataDir = createTempDir()

        // Create and store a conversation
        val dao1 = AimoChatClientDaoFile(dataDir)
        val conversation = dao1.createChatConversation(userId = "user1")
        val chatId = conversation.chatId

        // Create a new DAO instance pointing to same directory
        val dao2 = AimoChatClientDaoFile(dataDir)
        val retrieved = dao2.getChatConversation(chatId)
        assertNotNull(retrieved)
        assertEquals(chatId, retrieved.chatId)
        assertEquals("user1", retrieved.userId)

        dataDir.deleteRecursively()
    }

    @Test
    fun `file dao handles user isolation`() {
        val dataDir = createTempDir()
        val dao = AimoChatClientDaoFile(dataDir)

        val conv1 = dao.createChatConversation(userId = "user1")
        val conv2 = dao.createChatConversation(userId = "user2")

        // User1 can only see their own conversation
        val user1Convs = dao.getChatConversations(userId = "user1")
        assertEquals(1, user1Convs.size)
        assertEquals(conv1.chatId, user1Convs.first().chatId)

        // User2 can only see their own conversation
        val user2Convs = dao.getChatConversations(userId = "user2")
        assertEquals(1, user2Convs.size)
        assertEquals(conv2.chatId, user2Convs.first().chatId)

        // Admin (null userId) can see all
        val adminConvs = dao.getChatConversations(userId = null)
        assertEquals(2, adminConvs.size)

        dataDir.deleteRecursively()
    }

    @Test
    fun `file dao stores and retrieves requests`() {
        val dataDir = createTempDir()
        val dao = AimoChatClientDaoFile(dataDir)

        val conversation = dao.createChatConversation(userId = "user1")
        val requestId = UUID.randomUUID()

        val message = ChatMessageEntity(
            requestId = requestId,
            messageId = 1,
            type = "USER",
            content = "Hello",
            thinking = null,
            toolName = null
        )

        val request = ChatRequestEntity(
            chatId = conversation.chatId,
            requestId = requestId,
            messages = listOf(message),
            requestCharacters = 5,
            createdAt = Instant.now()
        )

        dao.addChatRequest(userId = "user1", request)

        val retrieved = dao.getChatRequests(userId = "user1", chatId = conversation.chatId)
        assertEquals(1, retrieved.size)
        assertEquals(requestId, retrieved.first().requestId)

        dataDir.deleteRecursively()
    }

    @Test
    fun `file dao metadata upsert and delete`() {
        val dataDir = createTempDir()
        val dao = AimoChatClientDaoFile(dataDir)

        val conversation = dao.createChatConversation(userId = "user1")

        // Upsert metadata
        val updated = dao.upsertConversationMetadata(
            conversation.chatId,
            userId = "user1",
            metadata = mapOf("title" to "My Chat", "tags" to "important")
        )
        assertTrue(updated)

        val retrieved = dao.getChatConversation(conversation.chatId, userId = "user1")
        assertNotNull(retrieved)
        assertEquals("My Chat", retrieved.metadata["title"])
        assertEquals("important", retrieved.metadata["tags"])

        // Delete metadata
        val deleted = dao.deleteConversationMetadata(
            conversation.chatId,
            userId = "user1",
            keys = listOf("tags")
        )
        assertTrue(deleted)

        val final = dao.getChatConversation(conversation.chatId, userId = "user1")
        assertNotNull(final)
        assertEquals("My Chat", final.metadata["title"])
        assertNull(final.metadata["tags"])

        dataDir.deleteRecursively()
    }

    @Test
    fun `file dao respects character budget when retrieving requests`() {
        val dataDir = createTempDir()
        val dao = AimoChatClientDaoFile(dataDir)

        val conversation = dao.createChatConversation(userId = "user1")

        // Add 3 requests with different sizes and timestamps
        repeat(3) { i ->
            val requestId = UUID.randomUUID()
            val message = ChatMessageEntity(
                requestId = requestId,
                messageId = i + 1,
                type = "USER",
                content = "Request $i",
                thinking = null,
                toolName = null
            )
            Thread.sleep(10) // Ensure different timestamps
            dao.addChatRequest(
                userId = "user1",
                ChatRequestEntity(
                    chatId = conversation.chatId,
                    requestId = requestId,
                    messages = listOf(message),
                    requestCharacters = (i + 1) * 50,  // 50, 100, 150
                    createdAt = Instant.now()
                )
            )
        }

        // Get all requests for inspection
        val allRequests = dao.getChatRequests(userId = "user1", chatId = conversation.chatId)
        assertEquals(3, allRequests.size)

        // Request with budget of 150 should get 1 request (the newest 150-char one)
        val requests150 = dao.getChatRequests(userId = "user1", chatId = conversation.chatId, maxRequestCharacters = 150)
        assertEquals(1, requests150.size)
        assertEquals(150, requests150[0].requestCharacters)

        // With budget of 250, should get newest 150 + previous 100 = 2 requests
        val requests250 = dao.getChatRequests(userId = "user1", chatId = conversation.chatId, maxRequestCharacters = 250)
        assertEquals(2, requests250.size)

        dataDir.deleteRecursively()
    }
}
