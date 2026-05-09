package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.Aimo
import org.ivcode.aimo.core.AimoImpl
import org.ivcode.aimo.core.controller.ChatController
import org.ivcode.aimo.core.controller.ChatControllerEntity
import org.ivcode.aimo.core.controller.SystemMessageCallback
import org.ivcode.aimo.core.controller.toAimoToolCallbacks
import org.ivcode.aimo.core.controller.toSystemMessageCallbacks
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
import org.ivcode.aimo.core.model.AimoToolCallback
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
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
    fun createPrimaryAimoChatModel(chatModelFactories: Map<String, AimoChatModelProviderFactory>): AimoChatModel {
        val factories: List<AimoChatModelProviderFactory> = chatModelFactories.values.toList()

        val providerPrimaries: List<AimoChatModel> = factories.mapNotNull { factory: AimoChatModelProviderFactory ->
            factory.getPrimaryName()?.let { primaryName ->
                requireNotNull(factory.createAimoChatModel(primaryName)) {
                    "Chat model factory '${factory.provider}' reported primary model '$primaryName' but could not create it."
                }
            }
        }
        require(providerPrimaries.size <= 1) {
            "Only one Aimo chat model can be marked primary=true. Found: ${providerPrimaries.map { it.name }}"
        }
        providerPrimaries.firstOrNull()?.let { return it }

        val allModels: List<AimoChatModel> = factories.flatMap { factory: AimoChatModelProviderFactory ->
            factory.getNames().map { name: String ->
                requireNotNull(factory.createAimoChatModel(name)) {
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
    fun createAimo (
        primaryModel: AimoChatModel,
        chatClientDao: AimoChatClientDao,
        tools: List<AimoToolCallback>,
        systemMessages: List<SystemMessageCallback>,
    ): Aimo {
        return AimoImpl(primaryModel, chatClientDao, tools, systemMessages)
    }
}