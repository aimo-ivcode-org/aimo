package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatServiceEntity
import org.ivcode.aimo.core.chatservice.ScopedSystemMessageCallbackWithName
import org.ivcode.aimo.core.chatservice.ScopedToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.toAimoToolCallbacks
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.properties.AimoProperties
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import org.ivcode.aimo.core.chatservice.ChatService

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
                tools = toAimoToolCallbacks(chatService, objectMapper, parentServiceScopes),
                systemMessages = toSystemMessageCallbacks(chatService, parentServiceScopes),
            ))
        }

        return list
    }

    @Bean
    fun createToolCallbacks(chatServices: List<ChatServiceEntity>): List<AimoToolCallback> {
        return chatServices.flatMap { it.tools.map { scoped -> scoped.callback } }
    }

    @Bean
    fun createScopedToolCallbacks(chatServices: List<ChatServiceEntity>): List<ScopedToolCallback> {
        return chatServices.flatMap { it.tools }
    }

    @Bean
    fun createToolScopeMap(scopedTools: List<ScopedToolCallback>): Map<String, Set<String>> {
        return scopedTools.associate { scoped ->
            scoped.callback.toolDefinition.name to scoped.scopes
        }
    }

    @Bean
    fun createSystemMessageCallbacks(chatServices: List<ChatServiceEntity>): List<SystemMessageCallback> {
        return chatServices.flatMap { it.systemMessages.map { scoped -> scoped.callback } }
    }

    @Bean
    fun createScopedSystemMessageCallbacks(chatServices: List<ChatServiceEntity>): List<ScopedSystemMessageCallbackWithName> {
        return chatServices.flatMap { it.systemMessages }
    }

    @Bean
    fun createSystemMessageScopeMap(scopedSystemMessages: List<ScopedSystemMessageCallbackWithName>): Map<String, Set<String>> {
        return scopedSystemMessages.associate { scoped ->
            scoped.name to scoped.scopes
        }
    }

    @Bean
    fun createChatScopeProvider(
        scopedTools: List<ScopedToolCallback>,
        scopedSystemMessages: List<ScopedSystemMessageCallbackWithName>,
        tools: List<AimoToolCallback>,
        systemMessages: List<SystemMessageCallback>,
        properties: AimoProperties
    ): ChatScopeProvider {
        val toolScopeMap = scopedTools.associate { scoped ->
            scoped.callback.toolDefinition.name to scoped.scopes
        }

        val systemMessageScopeMap = scopedSystemMessages.associate { scoped ->
            scoped.name to scoped.scopes
        }

        // Build predefined scopes from YAML configuration
        val predefinedScopes = buildPredefinedScopesForTest(
            scopeConfigs = properties.scope,
            allTools = tools,
            allSystemMessages = systemMessages,
            toolScopeMap = toolScopeMap,
            systemMessageScopeMap = systemMessageScopeMap,
            scopedSystemMessages = scopedSystemMessages
        )

         return ChatScopeProviderImpl(
             allTools = tools,
             allSystemMessages = scopedSystemMessages,
             predefinedScopes = predefinedScopes,
             toolScopeMap = toolScopeMap,
             systemMessageScopeMap = systemMessageScopeMap
         )
    }

    private fun buildPredefinedScopesForTest(
        scopeConfigs: Map<String, org.ivcode.aimo.core.properties.AimoChatScopeProperties>,
        allTools: List<AimoToolCallback>,
        allSystemMessages: List<SystemMessageCallback>,
        toolScopeMap: Map<String, Set<String>>,
        systemMessageScopeMap: Map<String, Set<String>>,
        scopedSystemMessages: List<ScopedSystemMessageCallbackWithName>
    ): Map<String, ChatScope> {
        // Build a map from callback to name for system messages
        val callbackToName = mutableMapOf<SystemMessageCallback, String>()
        for (scoped in scopedSystemMessages) {
            callbackToName[scoped.callback] = scoped.name
        }

        // Return scopes based on YAML config
        val result = mutableMapOf<String, ChatScope>()
        for ((scopeId, config) in scopeConfigs) {
            // Collect tools for this scope
            val scopedTools = mutableListOf<AimoToolCallback>()

            // 1. Add global tools if inheritGlobal is true
            if (config.inheritGlobal) {
                scopedTools.addAll(allTools.filter { tool ->
                    val toolScopes = toolScopeMap[tool.toolDefinition.name] ?: emptySet()
                    // Global tool: no scope restriction
                    toolScopes.isEmpty()
                })
            }

            // 2. Add tools that explicitly declare this scope (from annotations)
            scopedTools.addAll(allTools.filter { tool ->
                val toolScopes = toolScopeMap[tool.toolDefinition.name] ?: emptySet()
                // Tool declared for this specific scope
                toolScopes.contains(scopeId)
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
                    val msgName = callbackToName[msg] ?: return@filter false
                    val msgScopes = systemMessageScopeMap[msgName] ?: emptySet()
                    // Global message: no scope restriction
                    msgScopes.isEmpty()
                })
            }

            // 2. Add messages that explicitly declare this scope (from annotations)
            systemMessagesForScope.addAll(allSystemMessages.filter { msg ->
                val msgName = callbackToName[msg] ?: return@filter false
                val msgScopes = systemMessageScopeMap[msgName] ?: emptySet()
                // Message declared for this specific scope
                msgScopes.contains(scopeId)
            })

             // 3. Add messages explicitly referenced in config (systemMessageRefs)
             // system-message-refs act as an override: explicitly include these messages regardless of their scope restrictions
             systemMessagesForScope.addAll(allSystemMessages.filter { msg ->
                 val msgName = callbackToName[msg] ?: return@filter false
                 config.systemMessageRefs.contains(msgName)
             })

            // Remove duplicates by name
            val uniqueSystemMessages = systemMessagesForScope
                .distinctBy { callbackToName[it] }

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
                tools = uniqueTools,
                systemMessages = allScopedSystemMessages
            )
        }

        return result
    }

    private class InlineSystemMessageCallback(
        val id: String,
        val messageText: String
    ) : SystemMessageCallback {
        override fun call(context: org.ivcode.aimo.core.chatservice.SystemMessageContext): String? = messageText
    }
}




