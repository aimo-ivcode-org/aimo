package org.ivcode.aimo.ollama

import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OllamaModelsPropertiesTest {

    @Test
    fun `binder maps nested ollama model properties`() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "aimo.model.ollama.chatbot.base-url" to "http://localhost:11434",
                "aimo.model.ollama.chatbot.primary" to "true",
                "aimo.model.ollama.chatbot.context.size" to "16384",
                "aimo.model.ollama.chatbot.context.exclude-thinking" to "true",
                "aimo.model.ollama.chatbot.options.temperature" to "0.7",
                "aimo.model.ollama.chatbot.options.top-p" to "0.9",
                "aimo.model.ollama.chatbot.options.stop" to "END,DONE",
            )
        )

        val bound = Binder(source)
            .bind("aimo.model", Bindable.of(OllamaModelsProperties::class.java))
            .orElseThrow { IllegalStateException("Failed to bind Ollama model properties") }

        val chatbot = bound.ollama.getValue("chatbot")
        assertEquals("http://localhost:11434", chatbot.baseUrl)
        assertTrue(chatbot.primary)
        assertEquals(16_384, chatbot.context.size)
        assertTrue(chatbot.context.excludeThinking)
        assertEquals("0.7", chatbot.options["temperature"])
        assertEquals("0.9", chatbot.options["top-p"])
        assertEquals("END,DONE", chatbot.options["stop"])
    }

    @Test
    fun `model properties expose documented defaults`() {
        val properties = OllamaModelProperties()

        assertEquals("http://localhost:11434", properties.baseUrl)
        assertFalse(properties.primary)
        assertEquals(8192, properties.context.size)
        assertFalse(properties.context.excludeThinking)
        assertTrue(properties.options.isEmpty())
    }
}


