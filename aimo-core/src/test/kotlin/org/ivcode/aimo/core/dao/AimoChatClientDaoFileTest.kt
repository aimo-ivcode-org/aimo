package org.ivcode.aimo.core.dao

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimoChatClientDaoFileTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var dao: AimoChatClientDaoFile

    @BeforeEach
    fun setUp() {
        dao = AimoChatClientDaoFile(tempDir)
    }

    @Test
    fun `createChatConversation without metadata`() {
        val conversation = dao.createChatConversation()

        assertNotNull(conversation)
        assertEquals(emptyMap(), conversation.metadata)

        val retrieved = dao.getChatConversation(conversation.chatId)
        assertNotNull(retrieved)
        assertEquals(conversation.chatId, retrieved.chatId)
    }

    @Test
    fun `createChatConversation with metadata`() {
        val metadata = mapOf("user" to "isaiah", "tenant" to "acme")
        val conversation = dao.createChatConversation(metadata)

        assertNotNull(conversation)
        assertEquals(metadata, conversation.metadata)

        val retrieved = dao.getChatConversation(conversation.chatId)
        assertNotNull(retrieved)
        assertEquals(metadata, retrieved.metadata)
    }

    @Test
    fun `getChatConversation with metadata filter`() {
        val metadata = mapOf("user" to "isaiah", "tenant" to "acme")
        val conversation = dao.createChatConversation(metadata)

        // Should match with matching metadata
        val retrieved1 = dao.getChatConversation(conversation.chatId, mapOf("user" to "isaiah"))
        assertNotNull(retrieved1)

        // Should not match with non-matching metadata
        val retrieved2 = dao.getChatConversation(conversation.chatId, mapOf("user" to "alice"))
        assertNull(retrieved2)
    }

    @Test
    fun `deleteChatConversation`() {
        val conversation = dao.createChatConversation()

        val deleted = dao.deleteChatConversation(conversation.chatId)
        assertTrue(deleted)

        val retrieved = dao.getChatConversation(conversation.chatId)
        assertNull(retrieved)
    }

    @Test
    fun `addChatRequest and getChatRequests`() {
        val chatId = UUID.randomUUID()
        val message = ChatMessageEntity(
            requestId = UUID.randomUUID(),
            messageId = 1,
            type = "assistant",
            content = "Hello",
            thinking = null,
            toolName = null
        )
        val request = ChatRequestEntity(
            chatId = chatId,
            requestId = UUID.randomUUID(),
            messages = listOf(message),
            requestCharacters = 100,
            createdAt = Instant.now()
        )

        dao.addChatRequest(request)

        val retrieved = dao.getChatRequests(chatId)
        assertEquals(1, retrieved.size)
        assertEquals(request.requestId, retrieved[0].requestId)
    }

    @Test
    fun `getChatRequests with character budget`() {
        val chatId = UUID.randomUUID()
        val requests = (0..4).map { i ->
            ChatRequestEntity(
                chatId = chatId,
                requestId = UUID.randomUUID(),
                messages = emptyList(),
                requestCharacters = 10,
                createdAt = Instant.now()
            )
        }

        requests.forEach { dao.addChatRequest(it) }

        // Budget of 25 should fit 2 requests (most recent first)
        val retrieved = dao.getChatRequests(chatId, 25)
        assertEquals(2, retrieved.size)

        // Budget of 0 should return empty
        val empty = dao.getChatRequests(chatId, 0)
        assertEquals(0, empty.size)
    }

    @Test
    fun `getMessages flattens messages from all requests`() {
        val chatId = UUID.randomUUID()
        val request1 = ChatRequestEntity(
            chatId = chatId,
            requestId = UUID.randomUUID(),
            messages = listOf(
                ChatMessageEntity(UUID.randomUUID(), 1, "user", "Hi", null, null),
                ChatMessageEntity(UUID.randomUUID(), 2, "assistant", "Hello", null, null)
            ),
            requestCharacters = 100,
            createdAt = Instant.now()
        )
        val request2 = ChatRequestEntity(
            chatId = chatId,
            requestId = UUID.randomUUID(),
            messages = listOf(
                ChatMessageEntity(UUID.randomUUID(), 3, "user", "How are you?", null, null)
            ),
            requestCharacters = 50,
            createdAt = Instant.now()
        )

        dao.addChatRequest(request1)
        dao.addChatRequest(request2)

        val messages = dao.getMessages(chatId)
        assertEquals(3, messages.size)
    }

    @Test
    fun `upsertConversationMetadata`() {
        val conversation = dao.createChatConversation(mapOf("user" to "isaiah"))

        val success = dao.upsertConversationMetadata(conversation.chatId, mapOf("locale" to "en-US"))
        assertTrue(success)

        val retrieved = dao.getChatConversation(conversation.chatId)
        assertNotNull(retrieved)
        assertEquals("isaiah", retrieved.metadata["user"])
        assertEquals("en-US", retrieved.metadata["locale"])
    }

    @Test
    fun `deleteConversationMetadata`() {
        val metadata = mapOf("user" to "isaiah", "locale" to "en-US", "theme" to "dark")
        val conversation = dao.createChatConversation(metadata)

        val success = dao.deleteConversationMetadata(conversation.chatId, listOf("locale", "theme"))
        assertTrue(success)

        val retrieved = dao.getChatConversation(conversation.chatId)
        assertNotNull(retrieved)
        assertEquals("isaiah", retrieved.metadata["user"])
        assertNull(retrieved.metadata["locale"])
        assertNull(retrieved.metadata["theme"])
    }

    @Test
    fun `getChatConversations returns all conversations`() {
        val conv1 = dao.createChatConversation()
        val conv2 = dao.createChatConversation(mapOf("user" to "alice"))
        val conv3 = dao.createChatConversation(mapOf("user" to "bob"))

        val all = dao.getChatConversations()
        assertEquals(3, all.size)
        assertTrue(all.map { it.chatId }.containsAll(listOf(conv1.chatId, conv2.chatId, conv3.chatId)))
    }
}

