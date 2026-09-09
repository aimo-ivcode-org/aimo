package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatServiceEntity
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.toToolCallbacks
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.AnnotatedChatServiceProvider
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManager
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManagerImpl
import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.properties.AimoProperties
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import tools.jackson.databind.ObjectMapper

/**
 * Minimal Spring configuration for ChatScope testing in aimo-core.
 * Sets up tool/system message discovery and ChatScopeProvider without requiring
 * a full application context (like model providers, DAO, etc.).
 */
@Configuration
@EnableConfigurationProperties(AimoProperties::class)
class TestChatScopeConfig {

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper()

    @Bean
    fun createControllerEntities(
        ctx: ApplicationContext,
        objectMapper: ObjectMapper,
    ): List<ChatServiceEntity> {
        val list = mutableListOf<ChatServiceEntity>()

        ctx.getBeansWithAnnotation<ChatService>().forEach { (beanName, chatService) ->
            val chatServiceAnnotation = chatService::class.java.getAnnotation(ChatService::class.java)
            val parentServiceScopes = chatServiceAnnotation.scope.toSet()

            list.add(ChatServiceEntity(
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
    fun createChatServiceProviders(
        chatServices: List<ChatServiceEntity>
    ): List<org.ivcode.aimo.core.chatservice.ChatServiceProvider> {
        return chatServices.map { entity ->
            AnnotatedChatServiceProvider(entity)
        }
    }

    @Bean
    fun createChatServiceProviderManager(
        providers: List<org.ivcode.aimo.core.chatservice.ChatServiceProvider>
    ): ChatServiceProviderManager {
        return ChatServiceProviderManagerImpl(providers)
    }

    @Bean
    fun createChatScopeProvider(
        tools: List<ToolCallback>,
        systemMessages: List<SystemMessageCallback>,
        properties: AimoProperties,
        providerManager: ChatServiceProviderManager
    ): ChatScopeProvider {
        // Build predefined scopes from YAML configuration
        val predefinedScopes = buildPredefinedScopesForTest (
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

    private fun buildPredefinedScopesForTest(
        scopeConfigs: Map<String, org.ivcode.aimo.core.properties.AimoChatScopeProperties>,
        allTools: List<ToolCallback>,
        allSystemMessages: List<SystemMessageCallback>,
        providerManager: ChatServiceProviderManager
    ): Map<String, ChatScope> {
        // Return scopes based on YAML config
        val result = mutableMapOf<String, ChatScope>()
        for ((scopeId, config) in scopeConfigs) {
            // Collect tools for this scope
            val scopedTools = mutableListOf<ToolCallback>()

            // 1. Add global tools if inheritGlobal is true
            if (config.inheritGlobal) {
                scopedTools.addAll(allTools.filter { tool ->
                    tool.scopes.isEmpty()
                })
            }

            // 2. Add tools that explicitly declare this scope (from annotations)
            scopedTools.addAll(allTools.filter { tool ->
                tool.scopes.contains(scopeId)
            })

            // 3. Add tools explicitly referenced in config (toolRefs)
            // tool-refs act as an override: explicitly include these tools regardless of their scope restrictions
            scopedTools.addAll(allTools.filter { tool ->
                config.toolRefs.contains(tool.toolDefinition.name)
            })

            // Remove duplicates by tool name
            val uniqueTools = scopedTools
                .distinctBy { it.toolDefinition.name }
                .toMutableList()

            // Collect system messages for this scope
            val systemMessagesForScope = mutableListOf<SystemMessageCallback>()

            // 1. Add global system messages if inheritGlobal is true
            if (config.inheritGlobal) {
                systemMessagesForScope.addAll(allSystemMessages.filter { msg ->
                    msg.scopes.isEmpty()
                })
            }

            // 2. Add messages that explicitly declare this scope (from annotations)
            systemMessagesForScope.addAll(allSystemMessages.filter { msg ->
                msg.scopes.contains(scopeId)
            })

            // 3. Add messages explicitly referenced in config (systemMessageRefs)
            // system-message-refs act as an override: explicitly include these messages
            // regardless of their scope restrictions
            systemMessagesForScope.addAll(allSystemMessages.filter { msg ->
                config.systemMessageRefs.contains(msg.name)
            })

            // Remove duplicates by name
            val uniqueSystemMessages = systemMessagesForScope
                .distinctBy { it.name }

            // Create inline system message callbacks from YAML system-messages field
            val inlineSystemMessages = config.systemMessages.map { (msgId, msgText) ->
                InlineSystemMessageCallback(msgId, msgText)
            }

            // Combine all system messages
            val allScopedSystemMessages = uniqueSystemMessages + inlineSystemMessages

            result[scopeId] = ChatScope(
                id = scopeId,
                displayName = config.displayName,
                description = config.description,
                providers = providerManager.getProviders(),
                tools = uniqueTools,
                systemMessages = allScopedSystemMessages
            )
        }

        return result
    }

    private class InlineSystemMessageCallback(
        val id: String,
        val messageText: String,
        override val scopes: Set<String> = emptySet()
    ) : SystemMessageCallback {
        override val name: String = id
        override fun call(context: org.ivcode.aimo.core.chatservice.SystemMessageContext): String? = messageText
    }
}




