package org.ivcode.aimo.core.model

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AimoChatModelFactoryImplTest {

    private fun createTestEngine(modelName: String): AimoChatEngine {
        return object : AimoChatEngine {
            override val options: AimoChatOptions = AimoChatOptions(model = modelName)

            override fun call(prompt: AimoPrompt): AimoChatResponse {
                return AimoChatResponse(
                    chatId = UUID.randomUUID(),
                    responseId = UUID.randomUUID(),
                    messages = emptyList(),
                    createdAt = Instant.now(),
                )
            }

            override fun call(prompt: AimoPrompt, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
                return call(prompt)
            }
        }
    }

    private fun createTestModel(name: String, isPrimary: Boolean = false): AimoChatModelConfig {
        return AimoChatModelConfig(
            name = name,
            chatEngine = createTestEngine(name),
            isPrimary = isPrimary,
            context = AimoChatContext(size = 8192),
        )
    }

    private fun createMockProviderFactory(
        provider: String,
        models: Map<String, AimoChatModelConfig>
    ): AimoChatModelProviderFactory {
        return object : AimoChatModelProviderFactory {
            override val provider: String = provider

            override fun getModels(): List<AimoChatModelConfig> = models.values.toList()

            override fun getModel(name: String): AimoChatModelConfig? = models[name]

            override fun getNames(): List<String> = models.keys.toList()

            override fun getPrimaryName(): String? = models.values.find { it.isPrimary }?.name

            override fun getDefaultModel(): AimoChatModelConfig? {
                return models.values.find { it.isPrimary } ?: models.values.firstOrNull()
            }
        }
    }

    @Test
    fun `getModel returns matching model from single provider`() {
        val model1 = createTestModel("gpt-4")
        val model2 = createTestModel("gpt-3.5-turbo")
        val provider = createMockProviderFactory("openai", mapOf("gpt-4" to model1, "gpt-3.5-turbo" to model2))

        val factory = AimoChatModelFactoryImpl(mapOf("openai" to provider))

        val found = factory.getModel("gpt-4")
        assertEquals("gpt-4", found?.name)
    }

    @Test
    fun `getModel returns null when model not found`() {
        val model1 = createTestModel("gpt-4")
        val provider = createMockProviderFactory("openai", mapOf("gpt-4" to model1))

        val factory = AimoChatModelFactoryImpl(mapOf("openai" to provider))

        val found = factory.getModel("nonexistent-model")
        assertNull(found)
    }

    @Test
    fun `getModel finds model from different provider`() {
        val gptModel = createTestModel("gpt-4")
        val claudeModel = createTestModel("claude-3")

        val openaiProvider = createMockProviderFactory("openai", mapOf("gpt-4" to gptModel))
        val anthropicProvider = createMockProviderFactory("anthropic", mapOf("claude-3" to claudeModel))

        val factory = AimoChatModelFactoryImpl(
            mapOf("openai" to openaiProvider, "anthropic" to anthropicProvider)
        )

        val found = factory.getModel("claude-3")
        assertEquals("claude-3", found?.name)
    }

    @Test
    fun `getModel throws IllegalStateException when duplicate model name exists across providers`() {
        val model1 = createTestModel("gpt-4")
        val model2 = createTestModel("gpt-4")

        val provider1 = createMockProviderFactory("provider1", mapOf("gpt-4" to model1))
        val provider2 = createMockProviderFactory("provider2", mapOf("gpt-4" to model2))

        val factory = AimoChatModelFactoryImpl(
            mapOf("provider1" to provider1, "provider2" to provider2)
        )

        val exception = assertFailsWith<IllegalStateException> {
            factory.getModel("gpt-4")
        }

        assertEquals(
            "Duplicate model name 'gpt-4' exposed by providers 'provider1' and 'provider2'. " +
                "Model names must be unique across all providers.",
            exception.message
        )
    }

    @Test
    fun `getNames returns all model names from all providers`() {
        val gptModel = createTestModel("gpt-4")
        val claudeModel = createTestModel("claude-3")

        val openaiProvider = createMockProviderFactory("openai", mapOf("gpt-4" to gptModel))
        val anthropicProvider = createMockProviderFactory("anthropic", mapOf("claude-3" to claudeModel))

        val factory = AimoChatModelFactoryImpl(
            mapOf("openai" to openaiProvider, "anthropic" to anthropicProvider)
        )

        val names = factory.getNames()
        assertEquals(setOf("gpt-4", "claude-3"), names.toSet())
    }

    @Test
    fun `getNames throws IllegalStateException when duplicate model name exists across providers`() {
        val model1 = createTestModel("gpt-4")
        val model2 = createTestModel("gpt-4")

        val provider1 = createMockProviderFactory("provider1", mapOf("gpt-4" to model1))
        val provider2 = createMockProviderFactory("provider2", mapOf("gpt-4" to model2))

        val factory = AimoChatModelFactoryImpl(
            mapOf("provider1" to provider1, "provider2" to provider2)
        )

        val exception = assertFailsWith<IllegalStateException> {
            factory.getNames()
        }

        assertEquals(
            "Duplicate model name 'gpt-4' exposed by providers 'provider1' and 'provider2'. " +
                "Model names must be unique across all providers.",
            exception.message
        )
    }

    @Test
    fun `getModels returns all models from all providers`() {
        val gptModel = createTestModel("gpt-4")
        val claudeModel = createTestModel("claude-3")

        val openaiProvider = createMockProviderFactory("openai", mapOf("gpt-4" to gptModel))
        val anthropicProvider = createMockProviderFactory("anthropic", mapOf("claude-3" to claudeModel))

        val factory = AimoChatModelFactoryImpl(
            mapOf("openai" to openaiProvider, "anthropic" to anthropicProvider)
        )

        val models = factory.getModels()
        assertEquals(2, models.size)
        assertEquals(setOf("gpt-4", "claude-3"), models.map { it.name }.toSet())
    }

    @Test
    fun `getPrimaryModel returns model marked as primary when only one exists`() {
        val primaryModel = createTestModel("gpt-4", isPrimary = true)
        val normalModel = createTestModel("gpt-3.5-turbo", isPrimary = false)

        val provider = createMockProviderFactory(
            "openai",
            mapOf("gpt-4" to primaryModel, "gpt-3.5-turbo" to normalModel)
        )

        val factory = AimoChatModelFactoryImpl(mapOf("openai" to provider))

        val primary = factory.getPrimaryModel()
        assertEquals("gpt-4", primary.name)
    }
}


