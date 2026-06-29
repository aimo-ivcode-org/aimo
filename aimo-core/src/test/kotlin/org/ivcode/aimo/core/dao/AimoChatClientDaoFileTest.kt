package org.ivcode.aimo.core.dao

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
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

    private fun createObjectMapper(): ObjectMapper = tools.jackson.module.kotlin.jacksonObjectMapper()

    private inline fun withTempDir(testBlock: (File) -> Unit) {
        val dataDir = createTempDir()
        try {
            testBlock(dataDir)
        } finally {
            dataDir.deleteRecursively()
        }
    }

    @Test
    fun `file dao creates and retrieves conversations`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())

            val conversation = dao.createChatConversation(mapOf("title" to "My Chat"))
            assertNotNull(conversation)
            assertEquals("My Chat", conversation.metadata["title"])

            val retrieved = dao.getChatConversation(conversation.chatId)
            assertNotNull(retrieved)
            assertEquals(conversation.chatId, retrieved.chatId)
            assertEquals("My Chat", retrieved.metadata["title"])
        }
    }

    @Test
    fun `file dao persists conversations across instances`() {
        withTempDir { dataDir ->
            val dao1 = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val conversation = dao1.createChatConversation()
            val chatId = conversation.chatId

            val dao2 = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val retrieved = dao2.getChatConversation(chatId)
            assertNotNull(retrieved)
            assertEquals(chatId, retrieved.chatId)
        }
    }

    @Test
    fun `file dao lists all conversations`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())

            val conv1 = dao.createChatConversation()
            val conv2 = dao.createChatConversation()

            val allConversations = dao.getChatConversations()
            assertEquals(2, allConversations.size)
            assertEquals(setOf(conv1.chatId, conv2.chatId), allConversations.map { it.chatId }.toSet())
        }
    }

    @Test
    fun `file dao stores and retrieves requests`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())

            val conversation = dao.createChatConversation()
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

            assertTrue(dao.addChatRequest(request))

            val retrieved = dao.getChatRequests(conversation.chatId)
            assertEquals(1, retrieved.size)
            assertEquals(requestId, retrieved.first().requestId)
        }
    }

    @Test
    fun `file dao metadata upsert and delete`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())

            val conversation = dao.createChatConversation()

            val updated = dao.upsertConversationMetadata(
                conversation.chatId,
                metadata = mapOf("title" to "My Chat", "tags" to "important")
            )
            assertTrue(updated)

            val retrieved = dao.getChatConversation(conversation.chatId)
            assertNotNull(retrieved)
            assertEquals("My Chat", retrieved.metadata["title"])
            assertEquals("important", retrieved.metadata["tags"])

            val deleted = dao.deleteConversationMetadata(
                conversation.chatId,
                keys = listOf("tags")
            )
            assertTrue(deleted)

            val final = dao.getChatConversation(conversation.chatId)
            assertNotNull(final)
            assertEquals("My Chat", final.metadata["title"])
            assertNull(final.metadata["tags"])
        }
    }

    @Test
    fun `file dao respects character budget when retrieving requests`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())

            val conversation = dao.createChatConversation()

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
                Thread.sleep(10)
                assertTrue(dao.addChatRequest(
                    ChatRequestEntity(
                        chatId = conversation.chatId,
                        requestId = requestId,
                        messages = listOf(message),
                        requestCharacters = (i + 1) * 50,
                        createdAt = Instant.now()
                    )
                ))
            }

            val allRequests = dao.getChatRequests(conversation.chatId)
            assertEquals(3, allRequests.size)

            val requests150 = dao.getChatRequests(conversation.chatId, maxRequestCharacters = 150)
            assertEquals(1, requests150.size)
            assertEquals(150, requests150[0].requestCharacters)

            val requests250 = dao.getChatRequests(conversation.chatId, maxRequestCharacters = 250)
            assertEquals(2, requests250.size)
        }
    }

    @Test
    fun `file dao addChatRequest fails when conversation does not exist`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val nonExistentChatId = UUID.randomUUID()
            val requestId = UUID.randomUUID()

            val result = dao.addChatRequest(
                ChatRequestEntity(
                    chatId = nonExistentChatId,
                    requestId = requestId,
                    messages = listOf(
                        ChatMessageEntity(
                            requestId = requestId,
                            messageId = 1,
                            type = "USER",
                            content = "no-conv",
                            thinking = null,
                            toolName = null
                        )
                    ),
                    requestCharacters = 7,
                    createdAt = Instant.now()
                )
            )

            assertTrue(!result)
        }
    }

    @Test
    fun `file dao addChatRequest succeeds when conversation exists`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val conversation = dao.createChatConversation()
            val requestId = UUID.randomUUID()

            val result = dao.addChatRequest(
                ChatRequestEntity(
                    chatId = conversation.chatId,
                    requestId = requestId,
                    messages = listOf(
                        ChatMessageEntity(
                            requestId = requestId,
                            messageId = 1,
                            type = "USER",
                            content = "authorized",
                            thinking = null,
                            toolName = null
                        )
                    ),
                    requestCharacters = 10,
                    createdAt = Instant.now()
                )
            )

            assertTrue(result)
            val requests = dao.getChatRequests(conversation.chatId)
            assertEquals(1, requests.size)
            assertEquals("authorized", requests.single().messages.single().content)
        }
    }

    @Test
    fun `metadata scoping creates with metadata and retrieves with same metadata`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val metadata = mapOf("userId" to "user1", "tenant" to "acme")
            val conversation = dao.createChatConversation(metadata)

            // Should retrieve with matching metadata
            val retrieved = dao.getChatConversation(conversation.chatId, scopeMetadata = metadata)
            assertEquals(conversation.chatId, retrieved?.chatId)
            assertEquals(metadata, retrieved?.metadata)
        }
    }

    @Test
    fun `metadata scoping creates with metadata, retrieves with different metadata returns null`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val metadata = mapOf("userId" to "user1", "tenant" to "acme")
            val conversation = dao.createChatConversation(metadata)

            // Should not retrieve with non-matching metadata
            val retrieved = dao.getChatConversation(conversation.chatId, scopeMetadata = mapOf("userId" to "user2"))
            assertNull(retrieved, "Should not retrieve conversation with non-matching metadata")
        }
    }


    @Test
    fun `metadata scoping creates with empty metadata, retrieves with empty scope succeeds`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val conversation = dao.createChatConversation(emptyMap())

            // Should retrieve with empty scope metadata
            val retrieved = dao.getChatConversation(conversation.chatId, scopeMetadata = emptyMap())
            assertEquals(conversation.chatId, retrieved?.chatId)
        }
    }

    @Test
    fun `metadata scoping multiple conversations with different metadata are scoped correctly`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val conv1 = dao.createChatConversation(mapOf("userId" to "user1", "tenant" to "acme"))
            val conv2 = dao.createChatConversation(mapOf("userId" to "user2", "tenant" to "acme"))
            val conv3 = dao.createChatConversation(mapOf("userId" to "user1", "tenant" to "globex"))

            // User1+acme should only retrieve conv1
            val user1Acme = dao.getChatConversation(conv1.chatId, mapOf("userId" to "user1", "tenant" to "acme"))
            assertEquals(conv1.chatId, user1Acme?.chatId)

            val user2Acme = dao.getChatConversation(conv2.chatId, mapOf("userId" to "user2", "tenant" to "acme"))
            assertEquals(conv2.chatId, user2Acme?.chatId)

            val user1Globex = dao.getChatConversation(conv3.chatId, mapOf("userId" to "user1", "tenant" to "globex"))
            assertEquals(conv3.chatId, user1Globex?.chatId)

            // Cross-scope attempts should fail
            assertNull(dao.getChatConversation(conv1.chatId, mapOf("userId" to "user2", "tenant" to "acme")))
            assertNull(dao.getChatConversation(conv2.chatId, mapOf("userId" to "user1", "tenant" to "acme")))
        }
    }


    @Test
    fun `metadata scoping getChatConversations filters by metadata`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val conv1 = dao.createChatConversation(mapOf("userId" to "user1", "tenant" to "acme"))
            val conv2 = dao.createChatConversation(mapOf("userId" to "user2", "tenant" to "acme"))
            val conv3 = dao.createChatConversation(mapOf("userId" to "user1", "tenant" to "globex"))

            // Get all conversations for user1+acme
            val user1Acme = dao.getChatConversations(mapOf("userId" to "user1", "tenant" to "acme"))
            assertEquals(listOf(conv1.chatId), user1Acme.map { it.chatId })

            // Get all conversations for user2+acme
            val user2Acme = dao.getChatConversations(mapOf("userId" to "user2", "tenant" to "acme"))
            assertEquals(listOf(conv2.chatId), user2Acme.map { it.chatId })

            // Get all conversations for user1+globex
            val user1Globex = dao.getChatConversations(mapOf("userId" to "user1", "tenant" to "globex"))
            assertEquals(listOf(conv3.chatId), user1Globex.map { it.chatId })

            // Empty scope retrieves all
            val all = dao.getChatConversations(emptyMap())
            assertEquals(3, all.size)
        }
    }

    @Test
    fun `metadata scoping addChatRequest respects scope metadata`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val metadata = mapOf("userId" to "user1")
            val conversation = dao.createChatConversation(metadata)

            // Adding request with matching scope should succeed
            val requestId1 = UUID.randomUUID()
            val result1 = dao.addChatRequest(
                ChatRequestEntity(
                    chatId = conversation.chatId,
                    requestId = requestId1,
                    messages = listOf(
                        ChatMessageEntity(requestId1, 1, "USER", "msg1", null, null)
                    ),
                    requestCharacters = 10,
                    createdAt = Instant.now()
                ),
                metadata
            )
            assertTrue(result1, "Should add request with matching scope metadata")

            // Adding request with non-matching scope should fail
            val requestId2 = UUID.randomUUID()
            val result2 = dao.addChatRequest(
                ChatRequestEntity(
                    chatId = conversation.chatId,
                    requestId = requestId2,
                    messages = listOf(
                        ChatMessageEntity(requestId2, 2, "USER", "msg2", null, null)
                    ),
                    requestCharacters = 10,
                    createdAt = Instant.now()
                ),
                mapOf("userId" to "user2")
            )
            assertFalse(result2, "Should not add request with non-matching scope metadata")

            // Verify only first request was added
            val requests = dao.getChatRequests(conversation.chatId, metadata)
            assertEquals(1, requests.size)
            assertEquals("msg1", requests.single().messages.single().content)
        }
    }

    @Test
    fun `metadata scoping upsertConversationMetadata respects scope metadata`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val metadata = mapOf("userId" to "user1")
            val conversation = dao.createChatConversation(metadata)

            // Upsert with matching scope should succeed
            val result1 = dao.upsertConversationMetadata(
                conversation.chatId,
                mapOf("title" to "My Chat"),
                scopeMetadata = metadata
            )
            assertTrue(result1, "Should upsert metadata with matching scope")

            // Verify metadata was updated
            val updated = dao.getChatConversation(conversation.chatId, metadata)
            assertEquals("My Chat", updated?.metadata?.get("title"))

            // Upsert with non-matching scope should fail
            val result2 = dao.upsertConversationMetadata(
                conversation.chatId,
                mapOf("title" to "Hacked"),
                scopeMetadata = mapOf("userId" to "user2")
            )
            assertFalse(result2, "Should not upsert metadata with non-matching scope")

            // Verify metadata wasn't changed
            val unchanged = dao.getChatConversation(conversation.chatId, metadata)
            assertEquals("My Chat", unchanged?.metadata?.get("title"))
        }
    }

    @Test
    fun `metadata scoping deleteConversationMetadata respects scope metadata`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val metadata = mapOf("userId" to "user1")
            val conversation = dao.createChatConversation(mapOf("userId" to "user1", "title" to "Chat"))

            // Delete with matching scope should succeed
            val result1 = dao.deleteConversationMetadata(
                conversation.chatId,
                keys = listOf("title"),
                scopeMetadata = metadata
            )
            assertTrue(result1, "Should delete metadata with matching scope")

            // Verify metadata was deleted
            val updated = dao.getChatConversation(conversation.chatId, metadata)
            assertNull(updated?.metadata?.get("title"))

            // Add metadata back
            dao.upsertConversationMetadata(conversation.chatId, mapOf("title" to "Chat"), metadata)

            // Delete with non-matching scope should fail
            val result2 = dao.deleteConversationMetadata(
                conversation.chatId,
                keys = listOf("title"),
                scopeMetadata = mapOf("userId" to "user2")
            )
            assertFalse(result2, "Should not delete metadata with non-matching scope")

            // Verify metadata is still there
            val unchanged = dao.getChatConversation(conversation.chatId, metadata)
            assertEquals("Chat", unchanged?.metadata?.get("title"))
        }
    }

    @Test
    fun `metadata scoping deleteChatConversation respects scope metadata`() {
        withTempDir { dataDir ->
            val dao = AimoChatClientDaoFile(dataDir, createObjectMapper())
            val metadata = mapOf("userId" to "user1")
            val conversation = dao.createChatConversation(metadata)

            // Delete with non-matching scope should fail
            val result1 = dao.deleteChatConversation(conversation.chatId, mapOf("userId" to "user2"))
            assertFalse(result1, "Should not delete conversation with non-matching scope")

            // Conversation should still exist
            val stillThere = dao.getChatConversation(conversation.chatId, metadata)
            assertNotNull(stillThere)

            // Delete with matching scope should succeed
            val result2 = dao.deleteChatConversation(conversation.chatId, metadata)
            assertTrue(result2, "Should delete conversation with matching scope")

            // Conversation should be gone
            val gone = dao.getChatConversation(conversation.chatId, metadata)
            assertNull(gone)
        }
    }
}
