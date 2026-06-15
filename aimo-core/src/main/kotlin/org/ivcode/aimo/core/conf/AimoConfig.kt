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
            allSystemMessages = systemMessages,
            predefinedScopes = predefinedScopes,
            toolScopeMap = toolScopeMap,
            systemMessageScopeMap = systemMessageScopeMap,
            interceptors = emptyList() // Phase 3 adds security interceptors
        )
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

        return scopeConfigs.mapValues { (id, config) ->
            // Filter tools by name references
            val scopedTools = if (config.toolRefs.isEmpty()) {
                emptyList<AimoToolCallback>()
            } else {
                allTools.filter { tool ->
                    val toolName = tool.toolDefinition.name

                    // Must be in the YAML tool-refs
                    if (!config.toolRefs.contains(toolName)) return@filter false

                    // Must satisfy annotation scope restriction
                    val toolScopes = toolScopeMap[toolName] ?: emptySet()
                    toolScopes.isEmpty() || toolScopes.contains(id)
                }
            }

            // Filter pre-defined system messages by name references
            val filteredPreDefinedSystemMessages = if (config.systemMessageRefs.isEmpty()) {
                emptyList<SystemMessageCallback>()
            } else {
                allSystemMessages.filter { msg ->
                    val msgName = callbackToName[msg] ?: return@filter false

                    // Must be referenced in the YAML system-message-refs
                    if (!config.systemMessageRefs.contains(msgName)) return@filter false

                    // Must satisfy annotation scope restriction
                    val msgScopes = systemMessageScopeMap[msgName] ?: emptySet()
                    msgScopes.isEmpty() || msgScopes.contains(id)
                }
            }

            // Create inline system message callbacks from YAML system-messages field
            val inlineSystemMessages = config.systemMessages.map { (msgId, msgText) ->
                InlineSystemMessageCallback(msgId, msgText)
            }

            // Combine filtered pre-defined and inline system messages
            val allScopedSystemMessages = filteredPreDefinedSystemMessages + inlineSystemMessages

            ChatScope(
                id = id,
                displayName = config.displayName,
                description = config.description,
                tools = scopedTools,
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
          defaultInterceptors: List<ChatClientInterceptor>, // Spring auto-collects all ChatClientInterceptor beans
      ): ChatClientBuilderFactory {
          return ChatClientBuilderFactoryImpl(
              modelProviderFactories = chatModelFactories,
              toolCallbacks = tools,
              systemMessages = systemMessages,
              defaultInterceptors = defaultInterceptors,
          )
      }
}

