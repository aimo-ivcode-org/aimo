package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.builder.ChatClientBuilderFactory
import org.ivcode.aimo.core.builder.ConversationFactory
import org.ivcode.aimo.core.builder.impl.ChatClientBuilderFactoryImpl
import org.ivcode.aimo.core.builder.impl.ConversationFactoryImpl
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.ivcode.aimo.core.builder.interceptor.impl.ErrorHandlingInterceptor
import org.ivcode.aimo.core.builder.interceptor.impl.LoggingInterceptor
import org.ivcode.aimo.core.builder.interceptor.impl.TracingInterceptor
import org.ivcode.aimo.core.controller.ChatController
import org.ivcode.aimo.core.controller.ChatControllerEntity
import org.ivcode.aimo.core.controller.SystemMessageCallback
import org.ivcode.aimo.core.controller.toAimoToolCallbacks
import org.ivcode.aimo.core.controller.toSystemMessageCallbacks
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.properties.AimoProperties
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
    ): List<ChatControllerEntity> {
        val list = mutableListOf<ChatControllerEntity>()

        ctx.getBeansWithAnnotation<ChatController>().forEach {(beanName, chatController) ->
            list.add(ChatControllerEntity (
                name = beanName,
                clazz = chatController.javaClass,
                instance = chatController,
                tools = toAimoToolCallbacks(chatController, objectMapper),
                systemMessages = toSystemMessageCallbacks(chatController),
            ))
        }

        return list
    }

    @Bean
    fun createToolCallbacks(chatControllers: List<ChatControllerEntity>): List<AimoToolCallback> {
        return chatControllers.flatMap { it.tools }
    }

    @Bean
    fun createSystemMessageCallbacks(chatControllers: List<ChatControllerEntity>): List<SystemMessageCallback> {
        return chatControllers.flatMap { it.systemMessages }
    }


    @Bean
    fun createPrimaryAimoChatModel(chatModelFactories: Map<String, AimoChatModelProviderFactory>): AimoChatModelConfig {
        val factories: List<AimoChatModelProviderFactory> = chatModelFactories.values.toList()

        val providerPrimaries: List<AimoChatModelConfig> = factories.mapNotNull { factory: AimoChatModelProviderFactory ->
            factory.getPrimaryName()?.let { primaryName ->
                requireNotNull(factory.getModel(primaryName)) {
                    "Chat model factory '${factory.provider}' reported primary model '$primaryName' but could not create it."
                }
            }
        }
        require(providerPrimaries.size <= 1) {
            "Only one Aimo chat model can be marked primary=true. Found: ${providerPrimaries.map { it.name }}"
        }
        providerPrimaries.firstOrNull()?.let { return it }

        val allModels: List<AimoChatModelConfig> = factories.flatMap { factory: AimoChatModelProviderFactory ->
            factory.getNames().map { name: String ->
                requireNotNull(factory.getModel(name)) {
                    "Chat model factory '${factory.provider}' reported model '$name' but could not create it."
                }
            }
        }
        require(allModels.isNotEmpty()) { "No Aimo chat models configured." }

        if (allModels.size == 1) return allModels.first()

        error(
            "Multiple Aimo chat models are configured (${allModels.map { it.name }}) " +
                "but none is marked primary=true."
        )
    }

     @Bean
     fun createConversationFactory(
         conversationStore: AimoChatClientDao,
     ): ConversationFactory {
         return ConversationFactoryImpl(conversationStore)
     }

     @Bean
     fun createDefaultInterceptors(properties: AimoProperties): List<ChatClientInterceptor> {
         val interceptors = mutableListOf<ChatClientInterceptor>()

         // Logging interceptor
         if (properties.interceptors.logging.enabled) {
             val logLevel = when (properties.interceptors.logging.level.uppercase()) {
                 "DEBUG" -> LoggingInterceptor.LogLevel.DEBUG
                 "INFO" -> LoggingInterceptor.LogLevel.INFO
                 "WARN" -> LoggingInterceptor.LogLevel.WARN
                 "ERROR" -> LoggingInterceptor.LogLevel.ERROR
                 else -> LoggingInterceptor.LogLevel.INFO
             }
             interceptors.add(LoggingInterceptor(logLevel = logLevel, enabled = true))
         }

         // Tracing interceptor
         if (properties.interceptors.tracing.enabled) {
             interceptors.add(
                 TracingInterceptor(
                     enabled = true,
                     serviceName = properties.interceptors.tracing.serviceName
                 )
             )
         }

         // Error handling interceptor
         if (properties.interceptors.errorHandling.enabled) {
             interceptors.add(
                 ErrorHandlingInterceptor(
                     maxRetries = properties.interceptors.errorHandling.maxRetries,
                     retryBackoffMs = properties.interceptors.errorHandling.retryBackoffMs,
                     enabled = true
                 )
             )
         }

         return interceptors
     }

     @Bean
     fun createChatClientBuilderFactory(
         chatModelFactories: Map<String, AimoChatModelProviderFactory>,
         tools: List<AimoToolCallback>,
         systemMessages: List<SystemMessageCallback>,
         defaultInterceptors: List<ChatClientInterceptor>,
     ): ChatClientBuilderFactory {
         return ChatClientBuilderFactoryImpl(
             modelProviderFactories = chatModelFactories,
             toolCallbacks = tools,
             systemMessages = systemMessages,
             defaultInterceptors = defaultInterceptors,
         )
     }
}

