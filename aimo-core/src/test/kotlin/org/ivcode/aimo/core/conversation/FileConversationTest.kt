package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JacksonException
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @org.junit.jupiter.api.Test
    fun addMessagesDoesNotOverwriteMalformedHistory() {
        val chatId = UUID.randomUUID()
        val conversation = FileConversation(chatId, tempDir)
        conversation.addMessages(
            UUID.randomUUID(),
            listOf(AimoChatMessage(1, AimoChatMessageType.USER, "Before", null, null, done = true))
        )

        val messagesFile = File(tempDir, "$chatId/messages.json")
        messagesFile.writeText("{ malformed")

        assertFailsWith<JacksonException> {
            conversation.addMessages(
                UUID.randomUUID(),
                listOf(AimoChatMessage(2, AimoChatMessageType.USER, "After", null, null, done = true))
            )
        }
        assertEquals("{ malformed", messagesFile.readText())
    }
}
