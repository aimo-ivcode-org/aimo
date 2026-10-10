package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JacksonException
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileConversationFactoryTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `empty metadata is persisted and remains accessible`() {
        val factory = FileConversationFactory(tempDir)
        val conversation = factory.createConversation(emptyMap())

        assertTrue(File(tempDir, "${conversation.chatId}/metadata.json").isFile)
        assertNotNull(factory.getConversation(conversation.chatId, emptyMap()))
        assertEquals(listOf(conversation.chatId), factory.getConversations(emptyMap()).map { it.chatId })
        assertTrue(factory.deleteConversation(conversation.chatId, emptyMap()))
    }

    @Test
    fun `unreadable metadata prevents access and deletion`() {
        val factory = FileConversationFactory(tempDir)
        val conversation = factory.createConversation(emptyMap())
        val conversationDir = File(tempDir, conversation.chatId.toString())
        File(conversationDir, "metadata.json").writeText("{ malformed")

        assertFailsWith<JacksonException> {
            factory.getConversation(conversation.chatId, emptyMap())
        }
        assertFailsWith<JacksonException> {
            factory.getConversations(emptyMap())
        }
        assertFailsWith<JacksonException> {
            factory.deleteConversation(conversation.chatId, emptyMap())
        }
        assertTrue(conversationDir.exists())
    }

    @Test
    fun `conversation persists across factory instances and enforces scope`() {
        val firstFactory = FileConversationFactory(tempDir)
        val created = firstFactory.createConversation(mapOf("tenant" to "a"))
        created.addMessages(
            UUID.randomUUID(),
            listOf(
                AimoChatMessage(
                    messageId = 1,
                    type = AimoChatMessageType.USER,
                    content = "persisted",
                    thinking = null,
                    toolName = null,
                    done = true
                )
            )
        )

        val secondFactory = FileConversationFactory(tempDir)
        val restored = secondFactory.getConversation(created.chatId, mapOf("tenant" to "a"))

        assertEquals("persisted", restored?.getMessages()?.single()?.content)
        assertEquals(emptyList(), secondFactory.getConversations(mapOf("tenant" to "b")))
        assertTrue(secondFactory.deleteConversation(created.chatId, mapOf("tenant" to "a")))
        assertEquals(null, secondFactory.getConversation(created.chatId, mapOf("tenant" to "a")))
    }

    @Test
    fun `concurrent instances preserve all writes for the same conversation`() {
        val factory = FileConversationFactory(tempDir)
        val created = factory.createConversation(emptyMap())
        val first = factory.getConversation(created.chatId, emptyMap())!!
        val second = factory.getConversation(created.chatId, emptyMap())!!
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val writes = listOf(first, second).mapIndexed { instance, conversation ->
                executor.submit {
                    start.await()
                    repeat(20) { index ->
                        conversation.addMessages(
                            requestId = UUID.randomUUID(),
                            messages = listOf(
                                AimoChatMessage(
                                    messageId = instance * 20 + index,
                                    type = AimoChatMessageType.USER,
                                    content = "message",
                                    thinking = null,
                                    toolName = null,
                                    done = true
                                )
                            )
                        )
                    }
                }
            }

            start.countDown()
            writes.forEach { it.get(30, TimeUnit.SECONDS) }

            assertEquals(40, factory.getConversation(created.chatId, emptyMap())?.getHistory()?.size)
        } finally {
            executor.shutdownNow()
        }
    }
}
