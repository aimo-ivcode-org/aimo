package org.ivcode.aimo.core.conversation

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/**
 * Unit tests for FileConversation.
 * Inherits all contract tests from ConversationContractTest and verifies
 * FileConversation satisfies the Conversation interface contract with filesystem persistence.
 */
class FileConversationTest : ConversationContractTest() {
    @TempDir
    lateinit var tempDir: File
    
    override fun createConversation(chatId: UUID): Conversation {
        return FileConversation(chatId, tempDir)
    }
}
