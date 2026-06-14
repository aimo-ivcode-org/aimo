package org.ivcode.aimo.core.builder.impl

import org.ivcode.aimo.core.builder.ChatClientBuilder
import org.ivcode.aimo.core.builder.ChatClientBuilderFactory
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback

/**
 * Factory for creating chat client builders.
 *
 * This factory is initialized once at application startup from properties and provider factories.
 * It acts as a singleton registry for models, tools, system messages, chat scopes, and default interceptors.
 *
 * @property modelProviderFactories Map of provider name → factory for creating models
 * @property toolCallbacks All registered tool callbacks from @ChatService beans
 * @property systemMessages All registered system message callbacks from @ChatService beans
 * @property chatScopeProvider Provider for retrieving available chat scopes
 * @property defaultInterceptors Default interceptors applied to all chat clients (logging, tracing, error handling)
 */
class ChatClientBuilderFactoryImpl(
    private val modelProviderFactories: Map<String, AimoChatModelProviderFactory>,
    private val toolCallbacks: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val chatScopeProvider: ChatScopeProvider,
    private val defaultInterceptors: List<ChatClientInterceptor> = emptyList(),
) : ChatClientBuilderFactory {

    // ...existing code...

    // Cache of model name → provider that can create it
    // Detect duplicate model names across providers and fail-fast
    private val modelRegistry: Map<String, AimoChatModelProviderFactory> = run {
        val modelToProvider = mutableMapOf<String, AimoChatModelProviderFactory>()
        val duplicates = mutableMapOf<String, MutableList<String>>()

        modelProviderFactories.values.forEach { factory ->
            factory.getNames().forEach { modelName ->
                val existing = modelToProvider[modelName]
                if (existing != null) {
                    // Duplicate detected - track it
                    duplicates.getOrPut(modelName) { mutableListOf(existing.provider) }
                        .add(factory.provider)
                } else {
                    modelToProvider[modelName] = factory
                }
            }
        }

        // Fail-fast if duplicates found
        require(duplicates.isEmpty()) {
            val details = duplicates.entries.joinToString("\n") { (name, providers) ->
                "  - '$name' in providers: ${providers.joinToString(", ")}"
            }
            "Duplicate model names detected across providers. Model names must be unique.\n$details"
        }

        modelToProvider
    }

    // Primary model resolved at construction time
    private val _primaryModel: AimoChatModelConfig by lazy {
        resolvePrimaryModel()
    }

    override fun builder(): ChatClientBuilder {
        return ChatClientBuilderImpl(
            conversation = null,
            factoryDefaultInterceptors = defaultInterceptors,
            toolCallbacks = toolCallbacks,
            systemMessages = systemMessages,
            chatScopeProvider = chatScopeProvider,
            getPrimaryModel = { _primaryModel },
            getModelByName = { name -> getModel(name) },
        )
    }

    override fun builder(conversation: Conversation): ChatClientBuilder {
        return ChatClientBuilderImpl(
            conversation = conversation,
            factoryDefaultInterceptors = defaultInterceptors,
            toolCallbacks = toolCallbacks,
            systemMessages = systemMessages,
            chatScopeProvider = chatScopeProvider,
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
        // Deprecated - use getChatScope instead
        return getChatScope(agentId, emptyMap())
    }

    override fun getChatScopes(context: Map<String, Any>): List<ChatScope> {
        return chatScopeProvider.getScopes(context)
    }

    override fun getChatScope(id: String, context: Map<String, Any>): ChatScope? {
        return chatScopeProvider.getScope(id, context)
    }

    override fun getGlobalChatScope(): ChatScope {
        return chatScopeProvider.getGlobalScope()
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

