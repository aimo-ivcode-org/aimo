package org.ivcode.aimo.core.model

import org.ivcode.aimo.core.AimoChatResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AimoChatModelTest {

    @Test
    fun `AimoChatModel stores AimoChatOptions directly`() {
        val engine = object : AimoChatEngine {
            override val options: AimoChatOptions = AimoChatOptions(model = "engine-default")

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
        val options = AimoChatOptions(
            model = "configured-model",
            temperature = 0.6,
            providerOptions = mapOf("format" to "json"),
        )

        val model = AimoChatModel(
            name = "chatbot",
            chatEngine = engine,
            options = options,
            isPrimary = false,
            contextSize = 4096,
        )

        assertEquals("chatbot", model.name)
        assertEquals(AimoChatOptions::class, model.options::class)
        assertEquals("configured-model", model.options.model)
        assertEquals(0.6, model.options.temperature)
        assertEquals("json", model.options.providerOptions["format"])
        assertFalse(model.isPrimary)
        assertEquals(4096, model.contextSize)
    }
}

