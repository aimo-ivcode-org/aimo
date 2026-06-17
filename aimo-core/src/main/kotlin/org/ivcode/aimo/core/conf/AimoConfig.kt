package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.builder.ChatClientBuilderFactory
import org.ivcode.aimo.core.builder.ConversationFactory
import org.ivcode.aimo.core.builder.impl.ChatClientBuilderFactoryImpl
import org.ivcode.aimo.core.builder.impl.ConversationFactoryImpl
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.ivcode.aimo.core.builder.interceptor.impl.ErrorHandlingInterceptor
import org.ivcode.aimo.core.builder.interceptor.impl.LoggingInterceptor
import org.ivcode.aimo.core.builder.interceptor.impl.TracingInterceptor
import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.ChatServiceEntity
import org.ivcode.aimo.core.chatservice.ScopedSystemMessageCallbackWithName
import org.ivcode.aimo.core.chatservice.ScopedToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.ivcode.aimo.core.chatservice.toAimoToolCallbacks
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.chatscope.ChatScopeProviderImpl
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.properties.AimoProperties
import org.ivcode.aimo.core.properties.AimoChatScopeProperties
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
            val chatServiceAnnotation = chatService::class.java.getAnnotation(ChatService::class.java)
            val parentServiceScopes = chatServiceAnnotation.scope.toSet()

            list.add(ChatServiceEntity (
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
    fun createSystemMessageNameRegistry(scopedSystemMessages: List<ScopedSystemMessageCallbackWithName>): Map<String, SystemMessageCallback> {
        // Build registry: systemMessageName → SystemMessageCallback
        val registry = mutableMapOf<String, SystemMessageCallback>()

        // Detect duplicate names across all ChatService beans
        for (scoped in scopedSystemMessages) {
            require(!registry.containsKey(scoped.name)) {
                "Duplicate system message name '${scoped.name}' detected. System message names must be unique."
            }
            registry[scoped.name] = scoped.callback
        }

        return registry
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
         // Build tool scope map locally from scopedTools
         val toolScopeMap = scopedTools.associate { scoped ->
             scoped.callback.toolDefinition.name to scoped.scopes
         }

         // Build system message scope map locally from scopedSystemMessages
         val systemMessageScopeMap = scopedSystemMessages.associate { scoped ->
             scoped.name to scoped.scopes
         }

         // Build predefined scopes from YAML configuration
         val predefinedScopes = buildPredefinedScopes(
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
             systemMessageScopeMap = systemMessageScopeMap,
             interceptors = emptyList() // Phase 3 adds security interceptors
         )
     }
    }

    private fun buildPredefinedScopes(
        scopeConfigs: Map<String, AimoChatScopeProperties>,
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

         // AUTO-DISCOVER SCOPES FROM ANNOTATIONS AND YAML
         // Collect all scope IDs from two sources:
         // 1. Annotation-based: mentioned in @ChatService(scope=[...])
         val discoveredAnnotationScopes = mutableSetOf<String>()
         discoveredAnnotationScopes.addAll(toolScopeMap.values.flatten())
         discoveredAnnotationScopes.addAll(systemMessageScopeMap.values.flatten())

         // 2. YAML-based: defined in application.yml aimo.scope.*
         val yamlDefinedScopes = scopeConfigs.keys

         // Combine both sources (but exclude "global" - it's handled by ChatScopeProviderImpl)
         val allScopeIds = (discoveredAnnotationScopes + yamlDefinedScopes) - "global"

         // Create ChatScope for each discovered/configured scope
         return allScopeIds.associateWith { scopeId ->
            val config = scopeConfigs[scopeId] ?: AimoChatScopeProperties(
                displayName = scopeId.replaceFirstChar { it.uppercase() },
                description = "Scope: $scopeId",
                inheritGlobal = true,
                toolRefs = emptyList(),
                systemMessages = emptyMap(),
                systemMessageRefs = emptyList()
            )

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

            ChatScope(
                id = scopeId,
                displayName = config.displayName,
                description = config.description,
                tools = uniqueTools,
                systemMessages = allScopedSystemMessages
            )
        }
    }

    /**
     * Inline system message callback created from YAML configuration.
     * Returns the static message text provided in config.
     */
    private class InlineSystemMessageCallback(
        val id: String,
        val messageText: String
    ) : SystemMessageCallback {
        override fun call(context: SystemMessageContext): String? = messageText
    }

    // ...existing interceptor beans...

     @Bean
     fun createConversationFactory(
         conversationStore: AimoChatClientDao,
     ): ConversationFactory {
         return ConversationFactoryImpl(conversationStore)
     }

     @Bean
     @ConditionalOnProperty(
         prefix = "aimo.interceptors.logging",
         name = ["enabled"],
         havingValue = "true",
         matchIfMissing = false
     )
     fun loggingInterceptor(properties: AimoProperties): ChatClientInterceptor {
         val logLevel = when (properties.interceptors.logging.level.uppercase()) {
             "DEBUG" -> LoggingInterceptor.LogLevel.DEBUG
             "INFO" -> LoggingInterceptor.LogLevel.INFO
             "WARN" -> LoggingInterceptor.LogLevel.WARN
             "ERROR" -> LoggingInterceptor.LogLevel.ERROR
             else -> LoggingInterceptor.LogLevel.INFO
         }
         return LoggingInterceptor(logLevel = logLevel, enabled = true)
     }

     @Bean
     @ConditionalOnProperty(
         prefix = "aimo.interceptors.tracing",
         name = ["enabled"],
         havingValue = "true",
         matchIfMissing = false
     )
     fun tracingInterceptor(properties: AimoProperties): ChatClientInterceptor {
         return TracingInterceptor(
             enabled = true,
             serviceName = properties.interceptors.tracing.serviceName
         )
     }

     @Bean
     @ConditionalOnProperty(
         prefix = "aimo.interceptors.error-handling",
         name = ["enabled"],
         havingValue = "true",
         matchIfMissing = false
     )
     fun errorHandlingInterceptor(properties: AimoProperties): ChatClientInterceptor {
         return ErrorHandlingInterceptor(
             maxRetries = properties.interceptors.errorHandling.maxRetries,
             retryBackoffMs = properties.interceptors.errorHandling.retryBackoffMs,
             enabled = true
         )
     }

      @Bean
      fun createChatClientBuilderFactory(
          chatModelFactories: Map<String, AimoChatModelProviderFactory>,
          tools: List<AimoToolCallback>,
          systemMessages: List<SystemMessageCallback>,
          chatScopeProvider: ChatScopeProvider,
          defaultInterceptors: List<ChatClientInterceptor>, // Spring auto-collects all ChatClientInterceptor beans
      ): ChatClientBuilderFactory {
          return ChatClientBuilderFactoryImpl(
              modelProviderFactories = chatModelFactories,
              toolCallbacks = tools,
              systemMessages = systemMessages,
              chatScopeProvider = chatScopeProvider,
              defaultInterceptors = defaultInterceptors,
          )
      }
}

