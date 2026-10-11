package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Unit tests for MemoryConversation.
 * Inherits all contract tests from ConversationContractTest and verifies
 * MemoryConversation satisfies the Conversation interface contract.
 */
class MemoryConversationTest : ConversationContractTest() {
    override fun createConversation(chatId: UUID): Conversation {
        return MemoryConversation(chatId)
    }
}
