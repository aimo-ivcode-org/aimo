package org.ivcode.aimo.core.dao

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Instant
import java.util.UUID

class AimoChatClientDaoMemoryTest {

    @Test
    fun `getChatRequests with maxRequestCharacters returns newest requests within budget`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        dao.addChatRequest(request(chatId, 1, 10, "r1"))
        dao.addChatRequest(request(chatId, 2, 20, "r2"))
        dao.addChatRequest(request(chatId, 3, 30, "r3"))

        val result = dao.getChatRequests(chatId, maxRequestCharacters = 50)

        assertEquals(listOf("r2", "r3"), result.map { it.messages.single().content })
    }

    @Test
    fun `getChatRequests with maxRequestCharacters returns empty for zero or negative budget`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        dao.addChatRequest(request(chatId, 1, 10, "r1"))

        assertEquals(emptyList(), dao.getChatRequests(chatId, maxRequestCharacters = 0))
        assertEquals(emptyList(), dao.getChatRequests(chatId, maxRequestCharacters = -1))
    }

    @Test
    fun `getChatRequests with maxRequestCharacters returns empty when newest exceeds budget`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        dao.addChatRequest(request(chatId, 1, 10, "r1"))
        dao.addChatRequest(request(chatId, 2, 40, "r2"))

        val result = dao.getChatRequests(chatId, maxRequestCharacters = 30)

        // Budget is strict: newest request (40 chars) doesn't fit in budget (30 chars), return empty
        assertEquals(emptyList(), result)
    }

    @Test
    fun `getChatRequests with maxRequestCharacters preserves complete requests`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId
        val requestId1 = UUID.randomUUID()
        val requestId2 = UUID.randomUUID()

        dao.addChatRequest(
            ChatRequestEntity(
                chatId = chatId,
                requestId = requestId1,
                messages = listOf(
                    ChatMessageEntity(requestId1, 1, "USER", "r1-user", null, null),
                    ChatMessageEntity(requestId1, 2, "ASSISTANT", "r1-assistant", null, null),
                ),
                requestCharacters = 8,
                createdAt = Instant.now(),
            )
        )
        dao.addChatRequest(
            ChatRequestEntity(
                chatId = chatId,
                requestId = requestId2,
                messages = listOf(
                    ChatMessageEntity(requestId2, 3, "USER", "r2-user", null, null),
                    ChatMessageEntity(requestId2, 4, "ASSISTANT", "r2-assistant", null, null),
                ),
                requestCharacters = 8,
                createdAt = Instant.now(),
            )
        )

        val result = dao.getChatRequests(chatId, maxRequestCharacters = 8)

        assertEquals(listOf(listOf("r2-user", "r2-assistant")), result.map { request -> request.messages.map { it.content } })
    }

    @Test
    fun `getChatRequests with budget 0 returns empty list`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add multiple messages
        repeat(3) { i ->
            dao.addChatRequest(request(chatId, i + 1, 10, "msg-$i"))
        }

        // Request with budget 0 should return empty
        assertEquals(emptyList(), dao.getChatRequests(chatId, maxRequestCharacters = 0))
    }

    @Test
    fun `getChatRequests with budget less than one message returns empty`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add messages of size 10
        repeat(3) { i ->
            dao.addChatRequest(request(chatId, i + 1, 10, "msg-$i"))
        }

        // Budget 5 is less than message size 10, should return empty
        assertEquals(emptyList(), dao.getChatRequests(chatId, maxRequestCharacters = 5))
    }

    @Test
    fun `getChatRequests with budget equal to one message returns exactly one message`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add messages of size 10
        repeat(3) { i ->
            dao.addChatRequest(request(chatId, i + 1, 10, "msg-$i"))
        }

        // Budget 10 should return newest message only (msg-2)
        val result = dao.getChatRequests(chatId, maxRequestCharacters = 10)
        assertEquals(listOf("msg-2"), result.map { it.messages.single().content })
    }

    @Test
    fun `getChatRequests with budget for two messages returns two messages`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add messages of size 10
        repeat(3) { i ->
            dao.addChatRequest(request(chatId, i + 1, 10, "msg-$i"))
        }

        // Budget 20 should accumulate exactly 2 newest (10 + 10 = 20)
        // Adding a third (10) would be 30 > 20, so stop
        val result = dao.getChatRequests(chatId, maxRequestCharacters = 20)
        assertEquals(listOf("msg-1", "msg-2"), result.map { it.messages.single().content })
    }

    @Test
    fun `getChatRequests with budget exceeding two messages still returns only two`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add messages of size 10
        repeat(3) { i ->
            dao.addChatRequest(request(chatId, i + 1, 10, "msg-$i"))
        }

        // Budget 25 is enough for 2 messages (20) but not 3 (30)
        val result = dao.getChatRequests(chatId, maxRequestCharacters = 25)
        assertEquals(listOf("msg-1", "msg-2"), result.map { it.messages.single().content })
    }

    @Test
    fun `getChatRequests with budget for all messages returns all messages`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add messages of size 10
        repeat(3) { i ->
            dao.addChatRequest(request(chatId, i + 1, 10, "msg-$i"))
        }

        // Budget 30 is exactly enough for all 3 messages
        val result = dao.getChatRequests(chatId, maxRequestCharacters = 30)
        assertEquals(listOf("msg-0", "msg-1", "msg-2"), result.map { it.messages.single().content })
    }

    @Test
    fun `getChatRequests respects chronological order`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add messages in order: 10, 20, 30
        dao.addChatRequest(request(chatId, 1, 10, "first"))
        dao.addChatRequest(request(chatId, 2, 20, "second"))
        dao.addChatRequest(request(chatId, 3, 30, "third"))

        // With budget 50, we take newest two (30 + 20 = 50, adding 10 would be 60 > 50)
        val result = dao.getChatRequests(chatId, maxRequestCharacters = 50)
        assertEquals(listOf("second", "third"), result.map { it.messages.single().content })

        // With budget 60, all three fit (30 + 20 + 10 = 60)
        val result2 = dao.getChatRequests(chatId, maxRequestCharacters = 60)
        assertEquals(listOf("first", "second", "third"), result2.map { it.messages.single().content })
    }

    @Test
    fun `getChatRequests with variable message sizes`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        // Add messages of varying sizes: 5, 15, 8
        dao.addChatRequest(request(chatId, 1, 5, "small"))
        dao.addChatRequest(request(chatId, 2, 15, "medium"))
        dao.addChatRequest(request(chatId, 3, 8, "tiny"))

        // Budget 20: newest is 8 (fits), next is 15 (8+15=23 > 20, doesn't fit)
        val result = dao.getChatRequests(chatId, maxRequestCharacters = 20)
        assertEquals(listOf("tiny"), result.map { it.messages.single().content })

        // Budget 23: newest is 8 (fits), next is 15 (8+15=23 fits), next is 5 (23+5=28 > 23)
        val result2 = dao.getChatRequests(chatId, maxRequestCharacters = 23)
        assertEquals(listOf("medium", "tiny"), result2.map { it.messages.single().content })
    }

    private fun request(chatId: UUID, messageId: Int, requestCharacters: Int, content: String): ChatRequestEntity {
        val requestId = UUID.randomUUID()
        return ChatRequestEntity(
            chatId = chatId,
            requestId = requestId,
            messages = listOf(
                ChatMessageEntity(
                    requestId = requestId,
                    messageId = messageId,
                    type = "USER",
                    content = content,
                    thinking = null,
                    toolName = null,
                )
            ),
            requestCharacters = requestCharacters,
            createdAt = Instant.now(),
        )
    }
}

