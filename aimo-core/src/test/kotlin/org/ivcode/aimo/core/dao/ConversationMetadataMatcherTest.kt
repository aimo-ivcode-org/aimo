package org.ivcode.aimo.core.dao

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationMetadataMatcherTest {

    @Test
    fun `empty scope matches any metadata`() {
        // Empty scope = unrestricted access
        assertTrue(ConversationMetadataMatcher.matches(mapOf("tenant" to "acme"), emptyMap()))
        assertTrue(ConversationMetadataMatcher.matches(emptyMap(), emptyMap()))
    }

    @Test
    fun `scope requires all keys to match stored values`() {
        val stored = mapOf("tenant" to "acme", "env" to "prod")

        // Exact match
        assertTrue(ConversationMetadataMatcher.matches(stored, mapOf("tenant" to "acme", "env" to "prod")))

        // Partial match - only checking keys provided in scope
        assertTrue(ConversationMetadataMatcher.matches(stored, mapOf("tenant" to "acme")))

        // Mismatch
        assertFalse(ConversationMetadataMatcher.matches(stored, mapOf("tenant" to "other")))
    }

    @Test
    fun `scope with single key`() {
        val stored = mapOf("userId" to "user1", "tenant" to "acme")

        // Scope with single key that matches stored
        assertTrue(ConversationMetadataMatcher.matches(stored, mapOf("userId" to "user1")))

        // Scope with single key that doesn't match
        assertFalse(ConversationMetadataMatcher.matches(stored, mapOf("userId" to "user2")))
    }
}

class AimoChatClientDaoMetadataScopeTest {

    @Test
    fun `memory dao scopes list read update and delete`() {
        val dao = AimoChatClientDaoMemory()
        val acme = dao.createChatConversation(mapOf("tenant" to "acme"))
        val other = dao.createChatConversation(mapOf("tenant" to "other"))

        assertEquals(2, dao.getChatConversations().size)
        assertEquals(listOf(acme.chatId), dao.getChatConversations(mapOf("tenant" to "acme")).map { it.chatId })

        assertNotNull(dao.getChatConversation(acme.chatId, mapOf("tenant" to "acme")))
        assertNull(dao.getChatConversation(acme.chatId, mapOf("tenant" to "other")))

        assertTrue(dao.upsertConversationMetadata(acme.chatId, mapOf("title" to "A"), mapOf("tenant" to "acme")))
        assertFalse(dao.upsertConversationMetadata(acme.chatId, mapOf("title" to "B"), mapOf("tenant" to "other")))

        assertTrue(dao.deleteChatConversation(other.chatId, mapOf("tenant" to "other")))
        assertFalse(dao.deleteChatConversation(acme.chatId, mapOf("tenant" to "other")))
    }
}
