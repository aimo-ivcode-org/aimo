package org.ivcode.aimo.core.builder.impl

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.builder.ChatClientBuilder
import org.ivcode.aimo.core.builder.ChatClientBuilderFactory
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback

/**
 * Factory for creating chat client builders.
 *
 * This factory is initialized once at application startup from properties and provider factories.
 * It acts as a singleton registry for models, tools, system messages, and default interceptors.
 *
 * @property modelProviderFactories Map of provider name → factory for creating models
 * @property toolCallbacks All registered tool callbacks from @ChatService beans
 * @property systemMessages All registered system message callbacks from @ChatService beans
 * @property defaultInterceptors Default interceptors applied to all chat clients (logging, tracing, error handling)
 */
class ChatClientBuilderFactoryImpl(
    private val modelProviderFactories: Map<String, AimoChatModelProviderFactory>,
    private val toolCallbacks: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val defaultInterceptors: List<ChatClientInterceptor> = emptyList(),
) : ChatClientBuilderFactory {

    // Cache of model name → provider that can create it
    private val modelRegistry: Map<String, AimoChatModelProviderFactory> =
        modelProviderFactories.values.flatMap { factory ->
            factory.getNames().map { name -> name to factory }
        }.toMap()

    // Primary model resolved at construction time
    private val _primaryModel: AimoChatModelConfig by lazy {
        resolvePrimaryModel()
    }

    override fun builder(): ChatClientBuilder<AimoChatClient> {
        return ChatClientBuilderImpl(
            conversation = null,
            factoryDefaultInterceptors = defaultInterceptors,
            toolCallbacks = toolCallbacks,
            systemMessages = systemMessages,
            getPrimaryModel = { _primaryModel },
            getModelByName = { name -> getModel(name) },
        )
    }

    override fun builder(conversation: Conversation): ChatClientBuilder<AimoChatClient> {
        return ChatClientBuilderImpl(
            conversation = conversation,
            factoryDefaultInterceptors = defaultInterceptors,
            toolCallbacks = toolCallbacks,
            systemMessages = systemMessages,
            getPrimaryModel = { _primaryModel },
            getModelByName = { name -> getModel(name) },
        )
    }

    override fun getAvailableModels(): List<String> {
        return modelRegistry.keys.toList()
    }

    override fun getPrimaryModel(): AimoChatModelConfig {
        return _primaryModel
    }

    override fun getModel(name: String): AimoChatModelConfig? {
        val factory = modelRegistry[name] ?: return null
        return factory.getModel(name)
    }

    override fun getAgent(agentId: String): Any? {
        // TODO: Implement agent lookup in Phase 2
        return null
    }

    /**
     * Resolves the primary model from all registered providers.
     *
     * Resolution logic:
     * 1. If exactly one provider has a primary model, use it
     * 2. If no provider has a primary model and exactly one model exists globally, use it
     * 3. Otherwise, fail with clear error message
     *
     * @throws IllegalStateException if resolution fails
     */
    private fun resolvePrimaryModel(): AimoChatModelConfig {
        // Collect provider-local primary models
        val providerPrimaries: List<AimoChatModelConfig> = modelProviderFactories.values.mapNotNull { factory ->
            factory.getPrimaryName()?.let { primaryName ->
                factory.getModel(primaryName)
                    ?: error("Provider '${factory.provider}' reported primary model '$primaryName' but could not create it")
            }
        }

        // At most one provider can have a primary model
        require(providerPrimaries.size <= 1) {
            "Only one model can be marked primary=true across all providers. Found: ${providerPrimaries.map { it.name }}"
        }

        // If we have a primary, use it
        providerPrimaries.firstOrNull()?.let { return it }

        // No explicit primary - check if exactly one model exists
        val allModelNames = getAvailableModels()
        require(allModelNames.isNotEmpty()) {
            "No models configured. At least one model is required."
        }

        if (allModelNames.size == 1) {
            return getModel(allModelNames.first())
                ?: error("Failed to create the only available model: ${allModelNames.first()}")
        }

        // Multiple models, none marked primary
        error(
            "Multiple models are configured (${allModelNames.joinToString()}) " +
                "but none is marked primary=true. Mark exactly one model as primary."
        )
    }
}

