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
    fun `getChatRequests with maxRequestCharacters keeps oversized newest request`() {
        val dao = AimoChatClientDaoMemory()
        val chatId = dao.createChatConversation().chatId

        dao.addChatRequest(request(chatId, 1, 10, "r1"))
        dao.addChatRequest(request(chatId, 2, 40, "r2"))

        val result = dao.getChatRequests(chatId, maxRequestCharacters = 30)

        assertEquals(listOf("r2"), result.map { it.messages.single().content })
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

