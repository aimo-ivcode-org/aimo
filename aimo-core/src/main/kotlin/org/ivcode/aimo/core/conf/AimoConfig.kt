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
/**
 * Wires the core AIMO beans that assemble chat services, scopes, model factories, and the chat client provider.
 *
 * This configuration keeps the runtime wiring in one place while delegating the scope-building and validation logic
 * to private helpers in the same file so the bean definitions stay readable and easy to trace.
 */
class AimoConfig {

    /**
     * Discovers `@ChatService` beans and converts them into `ChatServiceEntity` instances.
     *
     * @param ctx The application context used to find annotated beans.
     * @param objectMapper The mapper used to build tool schemas from annotations.
     * @return The discovered chat-service entities in bean registration order.
     */
    @Bean
    fun createControllerEntities(
        ctx: ApplicationContext,
        objectMapper: ObjectMapper,
    ): List<ChatServiceEntity> {
        val entities = mutableListOf<ChatServiceEntity>()

        // Discover every annotated service bean and convert it into a runtime entity.
        ctx.getBeansWithAnnotation<ChatService>().forEach { (beanName, chatService) ->
            // Read the class-level annotation once so tool and system-message discovery can validate scope rules.
            val chatServiceAnnotation = AnnotationUtils.getAnnotation(chatService.javaClass, ChatService::class.java)!!
            val parentServiceScopes = chatServiceAnnotation.scope.toSet()

            // Preserve bean registration order so downstream discovery remains deterministic.
            entities.add(ChatServiceEntity(
                name = beanName,
                clazz = chatService.javaClass,
                instance = chatService,
                tools = toToolCallbacks(chatService, objectMapper, parentServiceScopes),
                systemMessages = toSystemMessageCallbacks(chatService, parentServiceScopes),
            ))
        }

        return entities
    }

    /**
     * Collects every discovered tool callback into a single list.
     *
     * @param chatServices The discovered chat-service entities.
     * @return All tool callbacks contributed by the chat services.
     */
    @Bean
    fun createToolCallbacks(chatServices: List<ChatServiceEntity>): List<ToolCallback> {
        return chatServices.flatMap { it.tools }
    }

    /**
     * Collects every discovered system-message callback into a single list.
     *
     * @param chatServices The discovered chat-service entities.
     * @return All system-message callbacks contributed by the chat services.
     */
    @Bean
    fun createSystemMessageCallbacks(chatServices: List<ChatServiceEntity>): List<SystemMessageCallback> {
        return chatServices.flatMap { it.systemMessages }
    }

    /**
     * Exposes the annotated chat services as a registry-backed provider source.
     *
     * @param chatServices The discovered chat-service entities.
     * @return A registry that adapts annotated services into chat-service providers.
     */
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

    /**
     * Flattens all provider registries into the provider manager used by scope resolution.
     *
     * @param registries The registry sources discovered by Spring.
     * @return A provider manager containing every provider from every registry.
     */
    @Bean
    fun createChatServiceProviderManager(
        registries: List<ChatServiceProviderRegistry>
    ): ChatServiceProviderManager {
        val log = org.slf4j.LoggerFactory.getLogger(javaClass)
        log.debug("ChatServiceProviderManager: injected ${registries.size} registries")

        val allProviders = mutableListOf<ChatServiceProvider>()
        // Merge providers from every registry so scope resolution sees a single flattened view.
        registries.forEachIndexed { idx, registry ->
            val registryProviders = registry.getProviders()
            log.debug("Registry[{}]: {} provider(s) - {}", idx, registryProviders.size, registryProviders.map { it.id })
            allProviders.addAll(registryProviders)
        }

        log.debug(
            "ChatServiceProviderManager: total {} provider(s) - {}",
            allProviders.size,
            allProviders.map { it.id }
        )
        return ChatServiceProviderManagerImpl(allProviders)
    }

      /**
       * Builds the scope provider from discovered callbacks and YAML scope configuration.
       *
       * @param tools The discovered tool callbacks.
       * @param systemMessages The discovered system-message callbacks.
       * @param properties The configured AIMO properties.
       * @param providerManager The provider manager used to resolve provider-sourced tools and messages.
       * @return A scope provider ready for runtime lookup.
       */
      @Bean
      fun createChatScopeProvider(
          tools: List<ToolCallback>,
          systemMessages: List<SystemMessageCallback>,
          properties: AimoProperties,
          providerManager: ChatServiceProviderManager
      ): ChatScopeProvider {
        // Resolve the configured scopes before instantiating the provider so validation happens during startup.
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
            providerManager = providerManager,
        )
       }

    /**
     * Creates the conversation factory used to resolve conversation instances from persistent storage.
     *
     * The factory resolves the DAO explicitly so sample applications can contribute their own storage beans without
     * creating an ambiguous type-only injection point.
     *
     * @param applicationContext The Spring context used to locate the conversation DAO.
     * @return A conversation factory bound to the configured store.
     */
    @Bean
    fun createConversationFactory(
        applicationContext: ApplicationContext,
    ): ConversationFactory {
        return ConversationFactoryImpl(resolveConversationStore(applicationContext))
    }


    /**
     * Creates the chat model factory used to select provider-specific model implementations.
     *
     * @param chatModelFactories The available model factories keyed by provider name.
     * @return A factory that delegates model lookup to the configured providers.
     */
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
     *
     * @param chatScopeProvider The scope provider used to resolve chat scopes for new clients.
     * @param defaultInterceptors Default interceptors contributed by the application context.
     * @return A provider that creates chat clients with the configured defaults.
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

/**
 * Builds predefined scopes from YAML configuration and discovered callbacks.
 *
 * @param scopeConfigs The scope definitions loaded from configuration.
 * @param allTools All discovered tool callbacks.
 * @param allSystemMessages All discovered system-message callbacks.
 * @param providerManager The provider manager used to include provider-sourced callbacks.
 * @return The resolved scopes keyed by scope id.
 */
private fun buildPredefinedScopes(
    scopeConfigs: Map<String, AimoChatScopeProperties>,
    allTools: List<ToolCallback>,
    allSystemMessages: List<SystemMessageCallback>,
    providerManager: ChatServiceProviderManager,
): Map<String, ChatScope> {
    // Discover every runtime scope id from annotations and YAML so missing entries still get a default scope.
    val allScopeIds = discoverScopeIds(allTools, allSystemMessages, scopeConfigs.keys)

    // Build lookup sets once so validation stays deterministic and inexpensive.
    val availableToolNames = buildAvailableToolNames(allTools, providerManager)
    val availableSystemMessageNames = buildAvailableSystemMessageNames(allSystemMessages, providerManager)

    return allScopeIds.associateWith { scopeId ->
        buildChatScopeForId(
            scopeId = scopeId,
            scopeConfigs = scopeConfigs,
            allTools = allTools,
            allSystemMessages = allSystemMessages,
            availableToolNames = availableToolNames,
            availableSystemMessageNames = availableSystemMessageNames,
            providerManager = providerManager,
        )
    }
}

/**
 * Collects every scope id that should exist at runtime.
 *
 * @param allTools All discovered tool callbacks.
 * @param allSystemMessages All discovered system-message callbacks.
 * @param yamlScopeIds Scope ids defined in YAML configuration.
 * @return The union of annotation-defined and YAML-defined scopes, excluding `global`.
 */
private fun discoverScopeIds(
    allTools: List<ToolCallback>,
    allSystemMessages: List<SystemMessageCallback>,
    yamlScopeIds: Set<String>,
): Set<String> {
    val annotationScopes = allTools.flatMap { it.scopes } + allSystemMessages.flatMap { it.scopes }
    return (annotationScopes.toSet() + yamlScopeIds) - ChatScopeProvider.GLOBAL_SCOPE_ID
}

/**
 * Collects all tool names that are available through direct discovery or provider registries.
 *
 * @param allTools All discovered tool callbacks.
 * @param providerManager The provider manager used to inspect provider-sourced tools.
 * @return The set of available tool names.
 */
private fun buildAvailableToolNames(
    allTools: List<ToolCallback>,
    providerManager: ChatServiceProviderManager,
): Set<String> = (
    allTools.map { it.toolDefinition.name } +
        providerManager.getProviders().flatMap { p -> p.getTools().map { it.toolDefinition.name } }
).toSet()

/**
 * Collects all system-message names that are available through direct discovery or provider registries.
 *
 * @param allSystemMessages All discovered system-message callbacks.
 * @param providerManager The provider manager used to inspect provider-sourced messages.
 * @return The set of available system-message names.
 */
private fun buildAvailableSystemMessageNames(
    allSystemMessages: List<SystemMessageCallback>,
    providerManager: ChatServiceProviderManager,
): Set<String> = (
    allSystemMessages.map { it.name } +
        providerManager.getProviders().flatMap { p -> p.getSystemMessages().map { it.name } }
).toSet()

/**
 * Builds a single scope by validating references and collecting the matching callbacks.
 *
 * @param scopeId The scope id being resolved.
 * @param scopeConfigs All configured scope definitions.
 * @param allTools All discovered tool callbacks.
 * @param allSystemMessages All discovered system-message callbacks.
 * @param availableToolNames The set of tool names that can be referenced.
 * @param availableSystemMessageNames The set of system-message names that can be referenced.
 * @param providerManager The provider manager used to include provider references.
 * @return A fully resolved `ChatScope` instance.
 */
private fun buildChatScopeForId(
    scopeId: String,
    scopeConfigs: Map<String, AimoChatScopeProperties>,
    allTools: List<ToolCallback>,
    allSystemMessages: List<SystemMessageCallback>,
    availableToolNames: Set<String>,
    availableSystemMessageNames: Set<String>,
    providerManager: ChatServiceProviderManager,
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
        systemMessages = systemMessages,
    )
}

/**
 * Creates the default scope configuration for a scope id that is not explicitly configured.
 *
 * @param scopeId The scope id to derive a fallback configuration for.
 * @return A default `AimoChatScopeProperties` instance that inherits global callbacks.
 */
private fun defaultScopeConfig(scopeId: String) = AimoChatScopeProperties(
    displayName = scopeId.replaceFirstChar { it.uppercase() },
    description = "Scope: $scopeId",
    inheritGlobal = true,
    toolRefs = emptyList(),
    systemMessages = emptyMap(),
    systemMessageRefs = emptyList(),
)

/**
 * Verifies that scope configuration only references tools and system messages that exist.
 *
 * @param scopeId The scope id being validated.
 * @param config The scope configuration to validate.
 * @param availableToolNames The set of known tool names.
 * @param availableSystemMessageNames The set of known system-message names.
 */
private fun validateScopeReferences(
    scopeId: String,
    config: AimoChatScopeProperties,
    availableToolNames: Set<String>,
    availableSystemMessageNames: Set<String>,
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

/**
 * Collects the tool callbacks that belong to a scope.
 *
 * @param scopeId The scope id being resolved.
 * @param config The scope configuration.
 * @param allTools All discovered tool callbacks.
 * @return The distinct set of tool callbacks that should be visible in the scope.
 */
private fun collectToolsForScope(
    scopeId: String,
    config: AimoChatScopeProperties,
    allTools: List<ToolCallback>,
): List<ToolCallback> {
    val scopedTools = mutableListOf<ToolCallback>()

    // Include globally visible tools first so explicit scope refs can only add to the inherited baseline.
    if (config.inheritGlobal) {
        scopedTools.addAll(allTools.filter { it.scopes.isEmpty() })
    }

    // Add tools bound directly to the scope and tools referenced by name from YAML.
    scopedTools.addAll(allTools.filter { it.scopes.contains(scopeId) })
    scopedTools.addAll(allTools.filter { config.toolRefs.contains(it.toolDefinition.name) })

    return scopedTools.distinctBy { it.toolDefinition.name }
}

/**
 * Collects the system-message callbacks that belong to a scope.
 *
 * @param scopeId The scope id being resolved.
 * @param config The scope configuration.
 * @param allSystemMessages All discovered system-message callbacks.
 * @return The distinct set of system-message callbacks that should be visible in the scope.
 */
private fun collectSystemMessagesForScope(
    scopeId: String,
    config: AimoChatScopeProperties,
    allSystemMessages: List<SystemMessageCallback>,
): List<SystemMessageCallback> {
    val messages = mutableListOf<SystemMessageCallback>()

    // Include globally visible messages first so scope-specific entries can build on the inherited baseline.
    if (config.inheritGlobal) {
        messages.addAll(allSystemMessages.filter { it.scopes.isEmpty() })
    }

    // Add messages bound directly to the scope and messages referenced by name from YAML.
    messages.addAll(allSystemMessages.filter { it.scopes.contains(scopeId) })
    messages.addAll(allSystemMessages.filter { config.systemMessageRefs.contains(it.name) })

    // Inline messages come from YAML and are always appended after annotation-backed callbacks.
    val uniqueMessages = messages.distinctBy { it.name }
    val inlineMessages = config.systemMessages.map { (msgId, msgText) ->
        InlineSystemMessageCallback(msgId, msgText, emptySet())
    }

    return uniqueMessages + inlineMessages
}

/**
 * Resolves the conversation DAO from the application context.
 *
 * The lookup prefers a bean named `aimoChatClientDao` when present so example applications can opt into a canonical
 * storage bean name while still allowing other single-candidate DAO beans to work unchanged.
 *
 * @param applicationContext The Spring application context containing DAO beans.
 * @return The resolved `AimoChatClientDao` implementation.
 */
private fun resolveConversationStore(applicationContext: ApplicationContext): AimoChatClientDao {
    val beanNames = applicationContext.getBeanNamesForType(AimoChatClientDao::class.java)
    require(beanNames.isNotEmpty()) {
        "No AimoChatClientDao bean is available for ConversationFactory"
    }

    val preferredBeanName = beanNames.firstOrNull { it == "aimoChatClientDao" } ?: beanNames.first()
    return applicationContext.getBean(preferredBeanName, AimoChatClientDao::class.java)
}

/**
 * Inline system-message callback created from YAML configuration.
 *
 * This adapter exposes YAML-provided message text through the same callback contract as annotated messages.
 *
 * @param id The stable message identifier.
 * @param messageText The static message content defined in configuration.
 * @param scopes The scopes associated with the inline message.
 */
private class InlineSystemMessageCallback(
    val id: String,
    val messageText: String,
    override val scopes: Set<String> = emptySet(),
) : SystemMessageCallback {
    override val name: String = id
    /**
     * Returns the configured inline system message text.
     *
     * @param context The current system-message execution context.
     * @return The configured YAML message text.
     */
    override fun call(context: SystemMessageContext): String = messageText
}


