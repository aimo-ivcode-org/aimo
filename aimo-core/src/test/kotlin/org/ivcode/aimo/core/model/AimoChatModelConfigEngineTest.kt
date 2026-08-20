package org.ivcode.aimo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [AimoChatModelConfig] creation and engine response behavior.
 *
 * Verifies that model configurations correctly capture names and primary flags,
 * and that engines produce responses with proper UUIDs and timestamps.
 */
class AimoChatModelConfigEngineTest {

    @Test
    fun `createTestModel produces config with matching name and primary flag`() {
        val model = createTestModel("gpt-4")
        assertEquals("gpt-4", model.name)
        assertEquals(true, model.isPrimary)
        val other = createTestModel("gpt-3.5-turbo")
        assertEquals("gpt-3.5-turbo", other.name)
        assertEquals(false, other.isPrimary)
    }

    @Test
    fun `engine call returns response with unique ids and valid timestamp`() {
        val model = createTestModel("gpt-4")
        val prompt = AimoPrompt(messages = emptyList())
        val resp = model.chatEngine.call(prompt)

        // Verify UUIDs are present and not nil
        assertNotNull(resp.chatId)
        assertNotNull(resp.responseId)
        assertNotNull(resp.createdAt)

        // Verify IDs are distinct
        assertTrue(resp.chatId != resp.responseId, "ChatId and ResponseId must be different")

        // Verify timestamp is recent (within last minute)
        val now = java.time.Instant.now()
        val oneMinuteAgo = now.minusSeconds(60)
        assertTrue(resp.createdAt.isAfter(oneMinuteAgo), "Timestamp should be recent")
        assertTrue(resp.createdAt.isBefore(now.plusSeconds(1)), "Timestamp should not be in the future")
    }

    // Helper used by tests (kept in-file for convenience)
    private fun createTestModel(name: String): AimoChatModelConfig {
        val engine = object : AimoChatEngine {
            override val options: AimoChatOptions = AimoChatOptions(model = name)
            override fun call(prompt: AimoPrompt): AimoChatResponse {
                return AimoChatResponse(
                    chatId = java.util.UUID.randomUUID(),
                    responseId = java.util.UUID.randomUUID(),
                    messages = emptyList(),
                    createdAt = java.time.Instant.now(),
                )
            }

            override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                return call(prompt)
            }
        }

        return AimoChatModelConfig(
            name = name,
            chatEngine = engine,
            isPrimary = name == "gpt-4",
            context = AimoChatContext(size = 8192),
        )
    }
}

