package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.chatclient.ChatClientProvider
import org.ivcode.aimo.core.chatclient.ChatClientProviderImpl
import org.ivcode.aimo.core.chatclient.ChatClientInterceptor
import org.springframework.core.annotation.AnnotationUtils

import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.ChatServiceEntity
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.ivcode.aimo.core.chatservice.toToolCallbacks
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import org.ivcode.aimo.core.chatservice.AnnotatedChatServiceProvider
import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManager
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManagerImpl
import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.chatscope.ChatScopeProviderImpl
import org.ivcode.aimo.core.conversation.ConversationFactory
import org.ivcode.aimo.core.conversation.ConversationFactoryImpl
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoChatModelFactory
import org.ivcode.aimo.core.model.AimoChatModelFactoryImpl
import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.properties.AimoProperties
import org.ivcode.aimo.core.chatservice.ChatServiceProviderRegistry
import org.ivcode.aimo.core.properties.AimoChatScopeProperties
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableConfigurationProperties(AimoProperties::class)
class AimoConfig {

    @Bean
    fun createControllerEntities(
        ctx: ApplicationContext,
        objectMapper: ObjectMapper,
    ): List<ChatServiceEntity> {
        val list = mutableListOf<ChatServiceEntity>()

        ctx.getBeansWithAnnotation<ChatService>().forEach {(beanName, chatService) ->
            // Extract parent @ChatService scopes for scope validation
            val chatServiceAnnotation = AnnotationUtils.getAnnotation(chatService.javaClass, ChatService::class.java)!!
            val parentServiceScopes = chatServiceAnnotation.scope.toSet()

            list.add(ChatServiceEntity (
                name = beanName,
                clazz = chatService.javaClass,
                instance = chatService,
                tools = toToolCallbacks(chatService, objectMapper, parentServiceScopes),
                systemMessages = toSystemMessageCallbacks(chatService, parentServiceScopes),
            ))
        }

        return list
    }

    @Bean
    fun createToolCallbacks(chatServices: List<ChatServiceEntity>): List<ToolCallback> {
        return chatServices.flatMap { it.tools }
    }

    @Bean
    fun createSystemMessageCallbacks(chatServices: List<ChatServiceEntity>): List<SystemMessageCallback> {
        return chatServices.flatMap { it.systemMessages }
    }

    @Bean
    fun annotatedChatServiceProviderRegistry(
        chatServices: List<ChatServiceEntity>
    ): ChatServiceProviderRegistry {
        val log = org.slf4j.LoggerFactory.getLogger(javaClass)
        log.debug("Creating annotated ChatServiceProviderRegistry with ${chatServices.size} service(s)")

        return object : ChatServiceProviderRegistry {
            override fun getProviders(): List<ChatServiceProvider> {
                val providers = chatServices.map { entity ->
                    AnnotatedChatServiceProvider(entity).also {
                        log.debug("Created annotated provider: id=${it.id}")
                    }
                }
                log.debug("Annotated registry returning ${providers.size} provider(s)")
                return providers
            }
        }
    }

    @Bean
    fun createChatServiceProviderManager(
        registries: List<ChatServiceProviderRegistry>
    ): ChatServiceProviderManager {
        // Flatten all providers from all registry sources
        val log = org.slf4j.LoggerFactory.getLogger(javaClass)
        log.debug("ChatServiceProviderManager: injected ${registries.size} registries")

        val allProviders = mutableListOf<ChatServiceProvider>()
        registries.forEachIndexed { idx, registry ->
            val registryProviders = registry.getProviders()
            log.debug("Registry[$idx]: ${registryProviders.size} provider(s) - ${registryProviders.map { it.id }}")
            allProviders.addAll(registryProviders)
        }

        log.debug("ChatServiceProviderManager: total ${allProviders.size} provider(s) - ${allProviders.map { it.id }}")
        return ChatServiceProviderManagerImpl(allProviders)
    }

      @Bean
      fun createChatScopeProvider(
          tools: List<ToolCallback>,
          systemMessages: List<SystemMessageCallback>,
          properties: AimoProperties,
          providerManager: ChatServiceProviderManager
      ): ChatScopeProvider {
         // Build predefined scopes from YAML configuration
         val predefinedScopes = buildPredefinedScopes(
              scopeConfigs = properties.scope,
              allTools = tools,
              allSystemMessages = systemMessages,
              providerManager = providerManager,
          )

            return ChatScopeProviderImpl(
               allTools = tools,
               allSystemMessages = systemMessages,
               predefinedScopes = predefinedScopes,
               providerManager = providerManager
            )
      }

       private fun buildPredefinedScopes(
            scopeConfigs: Map<String, AimoChatScopeProperties>,
            allTools: List<ToolCallback>,
            allSystemMessages: List<SystemMessageCallback>,
            providerManager: ChatServiceProviderManager
        ): Map<String, ChatScope> {
            // Discover all scope IDs from annotations and YAML config
            val allScopeIds = discoverScopeIds(allTools, allSystemMessages, scopeConfigs.keys)

            // Build sets of available tool and system message names for validation
            val availableToolNames = buildAvailableToolNames(allTools, providerManager)
            val availableSystemMessageNames = buildAvailableSystemMessageNames(allSystemMessages, providerManager)

            // Create ChatScope for each discovered/configured scope
            return allScopeIds.associateWith { scopeId ->
                buildChatScopeForId(
                    scopeId,
                    scopeConfigs,
                    allTools,
                    allSystemMessages,
                    availableToolNames,
                    availableSystemMessageNames,
                    providerManager
                )
            }
        }

        private fun discoverScopeIds(
            allTools: List<ToolCallback>,
            allSystemMessages: List<SystemMessageCallback>,
            yamlScopeIds: Set<String>
        ): Set<String> {
            val annotationScopes = allTools.flatMap { it.scopes } + allSystemMessages.flatMap { it.scopes }
            return (annotationScopes.toSet() + yamlScopeIds) - "global"
        }

        private fun buildAvailableToolNames(
            allTools: List<ToolCallback>,
            providerManager: ChatServiceProviderManager
        ): Set<String> = (
            allTools.map { it.toolDefinition.name } +
            providerManager.getProviders().flatMap { p -> p.getTools().map { it.toolDefinition.name } }
        ).toSet()

        private fun buildAvailableSystemMessageNames(
            allSystemMessages: List<SystemMessageCallback>,
            providerManager: ChatServiceProviderManager
        ): Set<String> = (
            allSystemMessages.map { it.name } +
            providerManager.getProviders().flatMap { p -> p.getSystemMessages().map { it.name } }
        ).toSet()

        private fun buildChatScopeForId(
            scopeId: String,
            scopeConfigs: Map<String, AimoChatScopeProperties>,
            allTools: List<ToolCallback>,
            allSystemMessages: List<SystemMessageCallback>,
            availableToolNames: Set<String>,
            availableSystemMessageNames: Set<String>,
            providerManager: ChatServiceProviderManager
        ): ChatScope {
            val config = scopeConfigs[scopeId] ?: defaultScopeConfig(scopeId)
            validateScopeReferences(scopeId, config, availableToolNames, availableSystemMessageNames)

            val scopedTools = collectToolsForScope(scopeId, config, allTools)
            val systemMessages = collectSystemMessagesForScope(scopeId, config, allSystemMessages)

            return ChatScope(
                id = scopeId,
                displayName = config.displayName,
                description = config.description,
                providers = providerManager.getProviders(),
                tools = scopedTools,
                systemMessages = systemMessages
            )
        }

        private fun defaultScopeConfig(scopeId: String) = AimoChatScopeProperties(
            displayName = scopeId.replaceFirstChar { it.uppercase() },
            description = "Scope: $scopeId",
            inheritGlobal = true,
            toolRefs = emptyList(),
            systemMessages = emptyMap(),
            systemMessageRefs = emptyList()
        )

        private fun validateScopeReferences(
            scopeId: String,
            config: AimoChatScopeProperties,
            availableToolNames: Set<String>,
            availableSystemMessageNames: Set<String>
        ) {
            val unknownToolRefs = config.toolRefs.filterNot { availableToolNames.contains(it) }
            require(unknownToolRefs.isEmpty()) {
                "Scope '$scopeId' references unknown tools: ${unknownToolRefs.joinToString(", ")}. " +
                "Available tools: ${availableToolNames.sorted().joinToString(", ")}"
            }

            val unknownMessageRefs = config.systemMessageRefs.filterNot { availableSystemMessageNames.contains(it) }
            require(unknownMessageRefs.isEmpty()) {
                "Scope '$scopeId' references unknown system messages: ${unknownMessageRefs.joinToString(", ")}. " +
                "Available system messages: ${availableSystemMessageNames.sorted().joinToString(", ")}"
            }
        }

        private fun collectToolsForScope(
            scopeId: String,
            config: AimoChatScopeProperties,
            allTools: List<ToolCallback>
        ): List<ToolCallback> {
            val scopedTools = mutableListOf<ToolCallback>()

            if (config.inheritGlobal) {
                scopedTools.addAll(allTools.filter { it.scopes.isEmpty() })
            }

            scopedTools.addAll(allTools.filter { it.scopes.contains(scopeId) })
            scopedTools.addAll(allTools.filter { config.toolRefs.contains(it.toolDefinition.name) })

            return scopedTools.distinctBy { it.toolDefinition.name }
        }

        private fun collectSystemMessagesForScope(
            scopeId: String,
            config: AimoChatScopeProperties,
            allSystemMessages: List<SystemMessageCallback>
        ): List<SystemMessageCallback> {
            val messages = mutableListOf<SystemMessageCallback>()

            if (config.inheritGlobal) {
                messages.addAll(allSystemMessages.filter { it.scopes.isEmpty() })
            }

            messages.addAll(allSystemMessages.filter { it.scopes.contains(scopeId) })
            messages.addAll(allSystemMessages.filter { config.systemMessageRefs.contains(it.name) })

            val uniqueMessages = messages.distinctBy { it.name }
            val inlineMessages = config.systemMessages.map { (msgId, msgText) ->
                InlineSystemMessageCallback(msgId, msgText, emptySet())
            }

            return uniqueMessages + inlineMessages
        }

    /**
     * Inline system message callback created from YAML configuration.
     * Returns the static message text provided in config.
     */
    private class InlineSystemMessageCallback(
        val id: String,
        val messageText: String,
        override val scopes: Set<String> = emptySet()
    ) : SystemMessageCallback {
        override val name: String = id
        override fun call(context: SystemMessageContext): String? = messageText
    }

    @Bean
    fun createConversationFactory(
        conversationStore: AimoChatClientDao,
    ): ConversationFactory {
        return ConversationFactoryImpl(conversationStore)
    }


    @Bean
    fun createAimoChatModelFactory(
        chatModelFactories: Map<String, AimoChatModelProviderFactory>
    ): AimoChatModelFactory {
        return AimoChatModelFactoryImpl(chatModelFactories)
    }

    /**
     * Creates the chat client provider with library-user-supplied default interceptors.
     *
     * The [defaultInterceptors] list is populated via Spring's auto-collection of all
     * [ChatClientInterceptor] beans registered by library users. This enables extensibility:
     * library users can define their own @Bean implementations (e.g., logging, tracing, retry)
     * to inject default behavior into all chat clients created by the provider.
     *
     * When no interceptors are registered, Spring provides an empty list.
     */
    @Bean
    fun createChatClientProvider(
        chatScopeProvider: ChatScopeProvider,
        defaultInterceptors: List<ChatClientInterceptor>, // Spring auto-collects; empty list if none registered
    ): ChatClientProvider {
        return ChatClientProviderImpl(
            chatScopeProvider = chatScopeProvider,
            defaultInterceptors = defaultInterceptors,
        )
    }
}

