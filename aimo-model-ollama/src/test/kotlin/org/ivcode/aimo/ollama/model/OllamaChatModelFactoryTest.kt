package org.ivcode.aimo.ollama.model

import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.ollama.OllamaContextProperties
import org.ivcode.aimo.ollama.OllamaModelProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class OllamaChatModelFactoryTest {

    @Test
    fun `getModel maps standard and provider options`() {
        val factory = OllamaChatModelFactory(
            linkedMapOf(
                "chatbot" to OllamaModelProperties(
                    baseUrl = "http://localhost:11434",
                    primary = true,
                    context = OllamaContextProperties(size = 16_384, excludeThinking = true),
                    options = linkedMapOf(
                        "temperature" to "0.7",
                        "num_predict" to 512,
                        "top-p" to 0.95,
                        "presence_penalty" to "0.1",
                        "stop" to listOf("END", "DONE"),
                        "format" to "json",
                    ),
                )
            )
        )

        val model = assertNotNull(factory.getModel("chatbot"))

        assertEquals("chatbot", model.name)
        assertTrue(model.isPrimary)
        assertEquals(16_384, model.context.size)
        assertTrue(model.context.excludeThinking)
        assertEquals(
            AimoChatOptions(
                model = "chatbot",
                temperature = 0.7,
                maxTokens = 512,
                topP = 0.95,
                presencePenalty = 0.1,
                stopSequences = listOf("END", "DONE"),
                providerOptions = mapOf("format" to "json"),
            ),
            model.options,
        )
        assertEquals(model.options, model.chatEngine.options)
    }

    @Test
    fun `getModel honors explicit model option`() {
        val factory = OllamaChatModelFactory(
            mapOf(
                "chatbot" to OllamaModelProperties(
                    options = mapOf(
                        "model" to "llama3.1:8b",
                        "temperature" to 0.4,
                    )
                )
            )
        )

        val model = assertNotNull(factory.getModel("chatbot"))

        assertEquals("llama3.1:8b", model.options.model)
        assertEquals(0.4, model.options.temperature)
    }

    @Test
    fun `getDefaultModel returns provider primary and otherwise first configured`() {
        val primaryFactory = OllamaChatModelFactory(
            linkedMapOf(
                "fast" to OllamaModelProperties(primary = false),
                "smart" to OllamaModelProperties(primary = true),
            )
        )
        val fallbackFactory = OllamaChatModelFactory(
            linkedMapOf(
                "fast" to OllamaModelProperties(primary = false),
                "smart" to OllamaModelProperties(primary = false),
            )
        )

        assertEquals("smart", primaryFactory.getDefaultModel()?.name)
        assertEquals("smart", primaryFactory.getPrimaryName())
        assertEquals("fast", fallbackFactory.getDefaultModel()?.name)
        assertNull(fallbackFactory.getPrimaryName())
        assertEquals(listOf("fast", "smart"), fallbackFactory.getNames())
    }

    @Test
    fun `getModel returns null when the named model is missing`() {
        val factory = OllamaChatModelFactory(emptyMap())

        assertNull(factory.getModel("missing"))
        assertFalse(factory.getNames().contains("missing"))
    }

    @Test
    fun `getPrimaryName fails on multiple provider primaries`() {
        val factory = OllamaChatModelFactory(
            linkedMapOf(
                "fast" to OllamaModelProperties(primary = true),
                "smart" to OllamaModelProperties(primary = true),
            )
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            factory.getPrimaryName()
        }

        assertTrue(ex.message!!.contains("Only one Ollama model can be marked primary=true"))
    }
}
