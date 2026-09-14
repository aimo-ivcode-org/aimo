package org.ivcode.aimo.core.model

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AimoChatModelConfigTest {

    @Test
    fun `AimoChatModelConfig stores chatEngine with options`() {
        val engineOptions = AimoChatOptions(model = "engine-default")
        val engine = object : AimoChatEngine {
            override val options: AimoChatOptions = engineOptions

            override fun call(prompt: AimoPrompt): AimoChatResponse {
                return AimoChatResponse(
                    chatId = UUID.randomUUID(),
                    responseId = UUID.randomUUID(),
                    messages = emptyList(),
                    createdAt = Instant.parse("2026-05-06T00:00:00Z"),
                )
            }

            override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                return call(prompt)
            }
        }

        val model = AimoChatModelConfig(
            name = "chatbot",
            chatEngine = engine,
            isPrimary = false,
            context = AimoChatContext(size = 4096),
        )

        assertEquals("chatbot", model.name)
        assertEquals(engine, model.chatEngine)
        assertEquals("engine-default", model.chatEngine.options.model)
        assertFalse(model.isPrimary)
        assertEquals(4096, model.context.size)
    }
}

