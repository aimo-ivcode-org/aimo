package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.model.AimoChatEngine
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoChatOptions
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AimoConfigPrimaryModelSelectionTest {

    private val config = AimoConfig()

    @Test
    fun `single model does not require primary`() {
        val only = model("only", isPrimary = false)
        val factories = mapOf("ollama" to factory("ollama", listOf(only)))

        val selected = config.createPrimaryAimoChatModel(factories)

        assertEquals("only", selected.name)
    }

    @Test
    fun `multiple models require exactly one global primary`() {
        val fast = model("fast")
        val smart = model("smart", isPrimary = true)
        val factories = mapOf("ollama" to factory("ollama", listOf(fast, smart)))

        val selected = config.createPrimaryAimoChatModel(factories)

        assertEquals("smart", selected.name)
    }

    @Test
    fun `multiple models without primary fail`() {
        val factories = mapOf(
            "ollama" to factory("ollama", listOf(model("fast"))),
            "bedrock" to factory("bedrock", listOf(model("smart"))),
        )

        val ex = assertFailsWith<IllegalStateException> {
            config.createPrimaryAimoChatModel(factories)
        }

        assertTrue(ex.message!!.contains("none is marked primary=true"))
    }

    @Test
    fun `multiple global primaries fail`() {
        val factories = mapOf(
            "ollama" to factory("ollama", listOf(model("fast", isPrimary = true))),
            "bedrock" to factory("bedrock", listOf(model("smart", isPrimary = true))),
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            config.createPrimaryAimoChatModel(factories)
        }

        assertTrue(ex.message!!.contains("Only one Aimo chat model can be marked primary=true"))
    }

    private fun factory(providerName: String, models: List<AimoChatModel>): AimoChatModelProviderFactory {
        return object : AimoChatModelProviderFactory {
            override val provider: String = providerName

            override fun createAimoChatModel(): AimoChatModel? =
                models.singleOrNull { it.isPrimary } ?: models.firstOrNull()

            override fun createAimoChatModel(name: String): AimoChatModel? =
                models.firstOrNull { it.name == name }

            override fun getNames(): List<String> = models.map { it.name }

            override fun getPrimaryName(): String? = models.singleOrNull { it.isPrimary }?.name
        }
    }

    private fun model(name: String, isPrimary: Boolean = false): AimoChatModel =
        AimoChatModel(
            name = name,
            chatEngine = TestEngine(),
            options = AimoChatOptions(model = name),
            isPrimary = isPrimary,
            contextSize = 8192,
        )

    private class TestEngine : AimoChatEngine {
        override val options: AimoChatOptions = AimoChatOptions()

        override fun call(prompt: AimoPrompt): AimoChatResponse {
            throw UnsupportedOperationException("Not used in config tests")
        }

        override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
            throw UnsupportedOperationException("Not used in config tests")
        }
    }
}

