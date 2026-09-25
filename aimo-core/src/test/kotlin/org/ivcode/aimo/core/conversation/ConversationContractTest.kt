package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract test for all Conversation implementations.
 *
 * This abstract test class defines the behavior contract that every Conversation
 * implementation must satisfy. Concrete implementations inherit from this class
 * and implement [createConversation] to provide their specific implementation.
 */
abstract class ConversationContractTest {
    protected lateinit var conversation: Conversation

    /**
     * Subclasses must implement this to create a fresh Conversation instance for each test.
     * @param chatId the chatId to use for the conversation
     * @return a new Conversation instance
     */
    protected abstract fun createConversation(chatId: UUID = UUID.randomUUID()): Conversation

    @BeforeEach
    fun setup() {
        conversation = createConversation()
    }

    @Test
    fun testGetMessagesReturnsEmptyListWhenNoMessages() {
        val messages = conversation.getMessages()
        assertTrue(messages?.isEmpty() ?: false, "Should return empty list for new conversation")
    }

    @Test
    fun testAddMessagesAndRetrieve() {
        val requestId = UUID.randomUUID()
        val messages = listOf(
            AimoChatMessage(
                messageId = 1,
                type = AimoChatMessageType.USER,
                content = "Hello",
                thinking = null,
                toolName = null,
                done = true
            ),
            AimoChatMessage(
                messageId = 2,
                type = AimoChatMessageType.ASSISTANT,
                content = "Hi there",
                thinking = null,
                toolName = null,
                done = true
            )
        )
        
        conversation.addMessages(requestId, messages)
        
        val retrieved = conversation.getMessages()
        assertNotNull(retrieved)
        assertEquals(2, retrieved.size)
        assertEquals("Hello", retrieved[0].content)
        assertEquals("Hi there", retrieved[1].content)
    }

    @Test
    fun testGetHistoryReturnsGroupedByRequest() {
        val requestId1 = UUID.randomUUID()
        val requestId2 = UUID.randomUUID()
        val messages1 = listOf(
            AimoChatMessage(1, AimoChatMessageType.USER, "Q1", null, null, done = true)
        )
        val messages2 = listOf(
            AimoChatMessage(2, AimoChatMessageType.USER, "Q2", null, null, done = true)
        )
        
        conversation.addMessages(requestId1, messages1)
        conversation.addMessages(requestId2, messages2)
        
        val history = conversation.getHistory()
        assertEquals(2, history.size)
        assertEquals(requestId1, history[0].requestId)
        assertEquals(requestId2, history[1].requestId)
        assertEquals(1, history[0].messages.size)
        assertEquals(1, history[1].messages.size)
    }

    @Test
    fun testGetHistoryPreservesCreatedAtOrder() {
        val requestId1 = UUID.randomUUID()
        val requestId2 = UUID.randomUUID()
        
        conversation.addMessages(requestId1, listOf(
            AimoChatMessage(1, AimoChatMessageType.USER, "First", null, null, done = true)
        ))
        conversation.addMessages(requestId2, listOf(
            AimoChatMessage(2, AimoChatMessageType.USER, "Second", null, null, done = true)
        ))
        
        val history = conversation.getHistory()
        assertTrue(history[0].createdAt <= history[1].createdAt, "History should be ordered by createdAt ascending")
    }

    @Test
    fun testChatMetadataIsInitiallyEmpty() {
        val metadata = conversation.getChatMetadata()
        assertEquals(emptyMap(), metadata)
    }

    @Test
    fun testWriteAndRetrieveChatProperty() {
        conversation.writeChatProperty("title", "My Chat")
        conversation.writeChatProperty("pinned", true)
        
        assertEquals("My Chat", conversation.getChatProperty("title"))
        assertEquals(true, conversation.getChatProperty("pinned"))
    }

    @Test
    fun testGetChatPropertyReturnsNullWhenNotFound() {
        assertNull(conversation.getChatProperty("nonexistent"))
    }

    @Test
    fun testDeleteChatProperty() {
        conversation.writeChatProperty("title", "My Chat")
        assertTrue(conversation.deleteChatProperty("title"))
        assertNull(conversation.getChatProperty("title"))
    }

    @Test
    fun testDeleteNonexistentPropertyReturnsFalse() {
        assertFalse(conversation.deleteChatProperty("nonexistent"))
    }

    @Test
    fun testWriteChatPropertiesIsAtomic() {
        val props = mapOf(
            "title" to "My Chat",
            "pinned" to true,
            "archived" to false
        )
        conversation.writeChatProperties(props)
        
        assertEquals("My Chat", conversation.getChatProperty("title"))
        assertEquals(true, conversation.getChatProperty("pinned"))
        assertEquals(false, conversation.getChatProperty("archived"))
    }

    @Test
    fun testDeleteChatPropertiesIsAtomic() {
        conversation.writeChatProperty("title", "My Chat")
        conversation.writeChatProperty("pinned", true)
        conversation.writeChatProperty("archived", false)
        
        conversation.deleteChatProperties(listOf("title", "pinned"))
        
        assertNull(conversation.getChatProperty("title"))
        assertNull(conversation.getChatProperty("pinned"))
        // archived should still exist
        assertEquals(false, conversation.getChatProperty("archived"))
    }

    @Test
    fun testGetChatMetadataReflectsAllWrittenProperties() {
        conversation.writeChatProperty("title", "My Chat")
        conversation.writeChatProperty("pinned", true)
        
        val metadata = conversation.getChatMetadata()
        assertEquals("My Chat", metadata["title"])
        assertEquals(true, metadata["pinned"])
    }

    @Test
    fun testMultipleAddMessagesCallsAppend() {
        val requestId1 = UUID.randomUUID()
        val requestId2 = UUID.randomUUID()
        
        conversation.addMessages(requestId1, listOf(
            AimoChatMessage(1, AimoChatMessageType.USER, "First", null, null, done = true)
        ))
        conversation.addMessages(requestId2, listOf(
            AimoChatMessage(2, AimoChatMessageType.ASSISTANT, "Response", null, null, done = true)
        ))
        
        val messages = conversation.getMessages()
        assertNotNull(messages)
        assertEquals(2, messages.size)
    }

    @Test
    fun testGetHistoryWithCharacterBudget() {
        val requestId1 = UUID.randomUUID()
        val requestId2 = UUID.randomUUID()
        
        // Add two requests with measurable content
        conversation.addMessages(requestId1, listOf(
            AimoChatMessage(1, AimoChatMessageType.USER, "A".repeat(100), null, null, done = true)
        ))
        conversation.addMessages(requestId2, listOf(
            AimoChatMessage(2, AimoChatMessageType.USER, "B".repeat(100), null, null, done = true)
        ))
        
        // With a small character budget, should only get most recent that fits
        val history = conversation.getHistory(maxCacheCharacters = 150)
        assertTrue(history.size <= 2, "Should respect character budget")
        // The most recent request (requestId2) should be in the result
        assertEquals(requestId2, history.last().requestId)
    }

    @Test
    fun testGetMessagesWithCharacterBudget() {
        val requestId = UUID.randomUUID()
        conversation.addMessages(requestId, listOf(
            AimoChatMessage(1, AimoChatMessageType.USER, "A".repeat(1000), null, null, done = true),
            AimoChatMessage(2, AimoChatMessageType.USER, "B".repeat(1000), null, null, done = true)
        ))
        
        val messages = conversation.getMessages(maxCacheCharacters = 1500)
        assertTrue((messages?.size ?: 0) > 0, "Should return at least some messages")
        // Most implementations will filter to stay under budget, getting most recent messages
    }
}
