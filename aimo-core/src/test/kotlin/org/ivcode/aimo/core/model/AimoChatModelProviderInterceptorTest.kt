package org.ivcode.aimo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        assertEquals(false, other.isPrimary)
    }

    @Test
    fun `engine call returns response with ids and timestamp`() {
        val model = createTestModel("gpt-4")
        val prompt = AimoPrompt(messages = emptyList())
        val resp = model.chatEngine.call(prompt)
        assertNotNull(resp.chatId)
        assertNotNull(resp.responseId)
        assertNotNull(resp.createdAt)
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


