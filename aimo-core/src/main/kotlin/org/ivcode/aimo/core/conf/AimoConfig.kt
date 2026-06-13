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
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.toAimoToolCallbacks
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.properties.AimoProperties
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
            list.add(ChatServiceEntity (
                name = beanName,
                clazz = chatService.javaClass,
                instance = chatService,
                tools = toAimoToolCallbacks(chatService, objectMapper),
                systemMessages = toSystemMessageCallbacks(chatService),
            ))
        }

        return list
    }

    @Bean
    fun createToolCallbacks(chatServices: List<ChatServiceEntity>): List<AimoToolCallback> {
        return chatServices.flatMap { it.tools }
    }

    @Bean
    fun createSystemMessageCallbacks(chatServices: List<ChatServiceEntity>): List<SystemMessageCallback> {
        return chatServices.flatMap { it.systemMessages }
    }


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

