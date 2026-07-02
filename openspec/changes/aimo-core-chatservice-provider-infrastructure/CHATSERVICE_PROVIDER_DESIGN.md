# ChatServiceProvider Design Example

This document demonstrates what the `ChatServiceProvider` abstraction will look like, providing a clear picture before implementation begins.

## Key Concept

The provider abstraction separates **tool/system message discovery** from **scope building**. Currently, discovery is embedded in `AimoConfig` as reflection-based bean scanning. With providers, any source (annotations, MCP servers, external APIs) can contribute tools and messages in a uniform way.

**Design Improvement:** Scope information (`scopes: Set<String>`) is added directly to `AimoToolCallback` and `SystemMessageCallback` interfaces, eliminating the need for wrapper classes (`ScopedToolCallback` / `ScopedSystemMessageCallback`). This reflects the fact that `@Tool` and `@SystemMessage` annotations already carry scope metadata—the callback objects should inherently carry it too.

---

## 1. Core Interface: `ChatServiceProvider`

```kotlin
package org.ivcode.aimo.core.chatservice

/**
 * Pluggable source of tools and system messages.
 *
 * Implementations discover tools and system messages from any source
 * (annotations, MCP servers, external APIs, etc.) and expose them
 * through a unified interface. Each callback already carries its scope
 * restrictions as metadata.
 */
interface ChatServiceProvider {
    /**
     * Unique identifier for this provider (e.g., "annotated", "mcp-server-1").
     * Used for logging, debugging, and provider lifecycle management.
     */
    val id: String

    /**
     * Human-readable display name for debugging.
     */
    val displayName: String

    /**
     * Get all tools available from this provider.
     *
     * Each tool callback includes its scope restrictions (empty set = global/unrestricted).
     * Tools are discovered lazily or cached, depending on provider implementation.
     *
     * @return List of tool callbacks; empty if no tools are available
     */
    fun getTools(): List<AimoToolCallback>

    /**
     * Get all system messages available from this provider.
     *
     * Each message callback includes its scope restrictions.
     *
     * @return List of system message callbacks; empty if none are available
     */
    fun getSystemMessages(): List<SystemMessageCallback>

    /**
     * Called by the provider manager at startup for initialization.
     * Implementations can validate state, connect to remote services, etc.
     *
     * @throws Exception if initialization fails
     */
    fun initialize() {
        // Default: no-op
    }

    /**
     * Called by the provider manager at shutdown.
     * Implementations should release resources.
     */
    fun shutdown() {
        // Default: no-op
    }
}
```

---

## 1a. Updated Base Interfaces: `AimoToolCallback` and `SystemMessageCallback`

These interfaces now carry scope information directly, eliminating wrapper classes.

```kotlin
package org.ivcode.aimo.core.model

/**
 * Callback representing an LLM-callable tool.
 *
 * The tool includes its scope restrictions, which determine which
 * ChatScopes can access this tool. Empty scopes means global (available to all scopes).
 *
 * @property toolDefinition Metadata: name, description, JSON schema
 * @property scopes Set of scope IDs this tool is available in (empty = global)
 */
interface AimoToolCallback {
    val toolDefinition: AimoToolDefinition
    val scopes: Set<String>  // NEW: scope info built in
    
    /**
     * Invoke the tool with the given arguments.
     *
     * @param argumentsJson JSON string of named arguments
     * @param context Runtime context (chatId, conversationClient, etc.)
     * @return JSON string result (or raw String if tool returns String)
     */
    fun call(argumentsJson: String, context: Map<String, Any>): String
}

/**
 * Callback representing a system message.
 *
 * The message includes its scope restrictions, just like tools.
 *
 * @property name Stable identifier for this message
 * @property scopes Set of scope IDs this message is available in (empty = global)
 */
interface SystemMessageCallback {
    val name: String
    val scopes: Set<String>  // NEW: scope info built in
    
    /**
     * Generate the system message text.
     *
     * @param context Runtime context (chatId, conversation metadata, etc.)
     * @return Message text, or null to skip this message
     */
    fun call(context: SystemMessageContext): String?
}
```

---

## 2. Implementation: `AnnotatedChatServiceProvider`

Wraps the current Spring annotation-based discovery logic. Now directly returns callbacks with scopes embedded.

```kotlin
package org.ivcode.aimo.core.chatservice

import org.springframework.core.annotation.AnnotationUtils
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext
import org.ivcode.aimo.core.model.AimoToolCallback
import tools.jackson.databind.ObjectMapper

/**
 * Provider that discovers tools and system messages from @ChatService-annotated beans.
 *
 * This implementation replaces the current inline discovery in AimoConfig.
 * It maintains backward compatibility: all existing annotated services work unchanged.
 */
class AnnotatedChatServiceProvider(
    private val applicationContext: ApplicationContext,
    private val objectMapper: ObjectMapper,
    override val id: String = "annotated",
    override val displayName: String = "Annotated Chat Services",
) : ChatServiceProvider {

    private var cachedTools: List<AimoToolCallback>? = null
    private var cachedSystemMessages: List<SystemMessageCallback>? = null

    override fun getTools(): List<AimoToolCallback> {
        if (cachedTools != null) return cachedTools!!

        val result = mutableListOf<AimoToolCallback>()

        applicationContext.getBeansWithAnnotation<ChatService>().forEach { (beanName, chatService) ->
            val annotation = AnnotationUtils.getAnnotation(chatService.javaClass, ChatService::class.java)!!
            val parentScopes = annotation.scope.toSet()

            // Existing utility now returns callbacks with scopes already embedded
            result.addAll(toAimoToolCallbacks(chatService, objectMapper, parentScopes))
        }

        cachedTools = result
        return result
    }

    override fun getSystemMessages(): List<SystemMessageCallback> {
        if (cachedSystemMessages != null) return cachedSystemMessages!!

        val result = mutableListOf<SystemMessageCallback>()

        applicationContext.getBeansWithAnnotation<ChatService>().forEach { (beanName, chatService) ->
            val annotation = AnnotationUtils.getAnnotation(chatService.javaClass, ChatService::class.java)!!
            val parentScopes = annotation.scope.toSet()

            // Existing utility now returns callbacks with scopes already embedded
            result.addAll(toSystemMessageCallbacks(chatService, parentScopes))
        }

        cachedSystemMessages = result
        return result
    }

    override fun initialize() {
        // Pre-populate caches during startup for eager validation
        getTools()
        getSystemMessages()
        logger.info("AnnotatedChatServiceProvider initialized with ${cachedTools!!.size} tools, ${cachedSystemMessages!!.size} system messages")
    }
}
```

---

## 3. Provider Manager: `ChatServiceProviderManager`

Orchestrates multiple providers and presents a unified view.

```kotlin
package org.ivcode.aimo.core.chatservice

/**
 * Central registry and lifecycle manager for all ChatServiceProviders.
 *
 * - Manages provider startup/shutdown lifecycle
 * - Aggregates tools and system messages from all providers
 * - Detects and fails on conflicts (e.g., duplicate tool names across providers)
 * - Provides a unified interface for downstream consumers (ChatScopeProvider, builders, etc.)
 */
class ChatServiceProviderManager(
    private val providers: List<ChatServiceProvider> = emptyList(),
) {
    private lateinit var allTools: List<AimoToolCallback>
    private lateinit var allSystemMessages: List<SystemMessageCallback>

    /**
     * Initialize all providers in order.
     * Fail-fast on conflicts during aggregation.
     *
     * @throws IllegalStateException if duplicate tool/message names detected
     */
    fun initialize() {
        // Initialize all providers
        providers.forEach { provider ->
            try {
                provider.initialize()
                logger.info("Initialized provider: ${provider.displayName}")
            } catch (e: Exception) {
                logger.error("Failed to initialize provider ${provider.id}", e)
                throw RuntimeException("Provider initialization failed: ${provider.id}", e)
            }
        }

        // Aggregate tools from all providers
        val toolList = mutableListOf<AimoToolCallback>()
        for (provider in providers) {
            toolList.addAll(provider.getTools())
        }

        // Detect duplicate tool names
        val toolNameCounts = toolList.groupingBy { it.toolDefinition.name }.eachCount()
        val duplicates = toolNameCounts.filter { it.value > 1 }
        require(duplicates.isEmpty()) {
            "Duplicate tool names detected across providers: ${duplicates.keys.joinToString(", ")}. " +
            "Each tool must have a unique name."
        }

        allTools = toolList

        // Aggregate system messages from all providers
        val messageList = mutableListOf<SystemMessageCallback>()
        for (provider in providers) {
            messageList.addAll(provider.getSystemMessages())
        }

        // Detect duplicate message names
        val messageNameCounts = messageList.groupingBy { it.name }.eachCount()
        val duplicateMessages = messageNameCounts.filter { it.value > 1 }
        require(duplicateMessages.isEmpty()) {
            "Duplicate system message names detected across providers: ${duplicateMessages.keys.joinToString(", ")}. " +
            "Each system message must have a unique name."
        }

        allSystemMessages = messageList

        logger.info(
            "ChatServiceProviderManager initialized: ${allTools.size} tools, " +
            "${allSystemMessages.size} system messages from ${providers.size} providers"
        )
    }

    /**
     * Get all tools from all providers.
     * Must be called after initialize().
     */
    fun getAllTools(): List<AimoToolCallback> {
        require(::allTools.isInitialized) { "Provider manager not initialized" }
        return allTools
    }

    /**
     * Get all system messages from all providers.
     * Must be called after initialize().
     */
    fun getAllSystemMessages(): List<SystemMessageCallback> {
        require(::allSystemMessages.isInitialized) { "Provider manager not initialized" }
        return allSystemMessages
    }

    /**
     * Get a specific provider by ID (for dynamic operations, e.g., MCP server reconnection).
     */
    fun getProvider(id: String): ChatServiceProvider? = providers.find { it.id == id }

    /**
     * Shutdown all providers in reverse order.
     */
    fun shutdown() {
        providers.asReversed().forEach { provider ->
            try {
                provider.shutdown()
                logger.info("Shut down provider: ${provider.displayName}")
            } catch (e: Exception) {
                logger.error("Error shutting down provider ${provider.id}", e)
            }
        }
    }
}
```

---

## 4. Integration: Refactored `AimoConfig`

Shows how configuration wires providers and uses the manager.

```kotlin
package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.AnnotatedChatServiceProvider
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManager
import org.ivcode.aimo.core.chatservice.ScopedToolCallback
import org.ivcode.aimo.core.chatservice.ScopedSystemMessageCallback
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class AimoConfig {

    /**
     * Wire up the annotated chat service provider.
     * In Phase 2, additional providers (e.g., MCP server provider) would be registered similarly.
     */
    @Bean
    fun createAnnotatedChatServiceProvider(
        applicationContext: ApplicationContext,
        objectMapper: ObjectMapper,
    ): ChatServiceProvider {
        return AnnotatedChatServiceProvider(applicationContext, objectMapper)
    }

    /**
     * Create the provider manager with all registered providers.
     * Spring auto-wires all ChatServiceProvider beans into this list.
     */
    @Bean
    fun createChatServiceProviderManager(
        providers: List<ChatServiceProvider>,
    ): ChatServiceProviderManager {  
        val manager = ChatServiceProviderManager(providers)
        manager.initialize()  // Initialize all providers at startup
        return manager
    }

    /**
     * Expose unified tool list from all providers.
     * Replaces the old bean that read directly from ChatServiceEntity.
     */
    @Bean
    fun createScopedToolCallbacks(manager: ChatServiceProviderManager): List<ScopedToolCallback> {
        return manager.getAllTools()
    }

    /**
     * Expose unified system message list from all providers.
     */
    @Bean
    fun createScopedSystemMessageCallbacks(manager: ChatServiceProviderManager): List<ScopedSystemMessageCallback> {
        return manager.getAllSystemMessages()
    }

    /**
     * Flatten scoped tools to simple callbacks for tools not yet scoped.
     * (Unchanged behavior; kept for backward compatibility)
     */
    @Bean
    fun createToolCallbacks(scopedTools: List<ScopedToolCallback>): List<AimoToolCallback> {
        return scopedTools.map { it.callback }
    }

    /**
     * Flatten scoped system messages to simple callbacks for backward compatibility.
     */
    @Bean
    fun createSystemMessageCallbacks(scopedMessages: List<ScopedSystemMessageCallback>): List<SystemMessageCallback> {
        return scopedMessages.map { it.callback }
    }

    /**
     * ChatScopeProvider now uses the aggregated provider results.
     * Scope building logic remains the same; it just reads from the unified lists.
     */
    @Bean
    fun createChatScopeProvider(
        scopedTools: List<ScopedToolCallback>,
        scopedSystemMessages: List<ScopedSystemMessageCallback>,
        tools: List<AimoToolCallback>,
        systemMessages: List<SystemMessageCallback>,
        properties: AimoProperties
    ): ChatScopeProvider {
        // Same scope building logic as before, but sourcing from providers
        return ChatScopeProviderImpl(
            allTools = tools,
            allSystemMessages = scopedSystemMessages,
            predefinedScopes = buildPredefinedScopes(
                scopeConfigs = properties.scope,
                allTools = tools,
                allSystemMessages = systemMessages,
                toolScopeMap = scopedTools.associate { it.callback.toolDefinition.name to it.scopes },
                systemMessageScopeMap = scopedSystemMessages.associate { it.callback.name to it.scopes },
                scopedSystemMessages = scopedSystemMessages
            ),
            // ... other params unchanged
        )
    }

    // ... rest of beans unchanged ...
}
```

---

## 5. Provider Configuration in Spring

Example of how providers are registered as beans.

```kotlin
/**
 * This configuration would be in a separate module or conditional config
 * when MCP support is added.
 */
@Configuration
class McpProviderConfiguration {
    
    @Bean
    @ConditionalOnProperty("aimo.mcp.enabled", havingValue = "true")
    fun createMcpToolsProvider(
        mcpClientManager: McpClientManager,
    ): ChatServiceProvider {
        // Example of a future provider for MCP tools
        return McpChatServiceProvider(
            id = "mcp-client",
            displayName = "MCP Tools Provider",
            mcpClientManager = mcpClientManager,
        )
    }
}
```

---

## 6. Provider Interface Usage Example

How consumers (builders, scope providers) would use the manager.

```kotlin
/**
 * Example of how ChatClientBuilder might use providers.
 * Shows how dynamic tool resolution (e.g., connecting to a new MCP server)
 * becomes possible with the provider abstraction.
 */
class ChatClientBuilderImpl(
    private val providerManager: ChatServiceProviderManager,
) {
    fun addToolsFromProvider(providerId: String) {
        val provider = providerManager.getProvider(providerId)
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        
        val tools = provider.getTools()
        // Add these tools to the current builder session
        // ...
    }
}
```

---

## 7. Test Structure

Example of how providers make testing easier:

```kotlin
/**
 * Example test provider that injects test-specific tools.
 */
class MockChatServiceProvider(
    override val id: String = "mock",
    override val displayName: String = "Mock Provider",
    private val mockTools: List<ScopedToolCallback> = emptyList(),
    private val mockMessages: List<ScopedSystemMessageCallback> = emptyList(),
) : ChatServiceProvider {
    override fun getTools() = mockTools
    override fun getSystemMessages() = mockMessages
}

// Usage in tests:
@Test
fun testWithCustomTools() {
    val mockProvider = MockChatServiceProvider(
        mockTools = listOf(
            ScopedToolCallback(testToolCallback, setOf("test-scope"))
        )
    )
    
    val manager = ChatServiceProviderManager(listOf(mockProvider))
    manager.initialize()
    
    val allTools = manager.getAllTools()
    assertEquals(1, allTools.size)
}
```

---

## Summary: What Changes

| Component | Before | After |
|-----------|--------|-------|
| **Discovery** | Embedded in `AimoConfig` as reflection | Delegated to `ChatServiceProvider` implementations |
| **Extensibility** | Only @ChatService annotations | Any provider source: annotations, MCP, APIs, etc. |
| **Testing** | Hard to mock tool sources | Easy with mock providers |
| **Scope Building** | Reads from static `ChatServiceEntity` list | Reads from `ChatServiceProviderManager` aggregates |
| **Tool Registration** | One-time at startup | Can be dynamic (future MCP reconnection) |
| **Error Handling** | Scattered validation | Centralized in provider manager |

---

## Benefits for Phase 2 (MCP Integration)

1. **Clean separation**: MCP tool discovery → `McpChatServiceProvider` (implements same interface)
2. **Runtime binding**: Tools from MCP servers can be added after startup
3. **Dynamic scopes**: Scope contents can change when MCP tools connect/disconnect
4. **No cross-cutting concerns**: MCP logic stays in MCP provider, not sprinkled through config
5. **Testing**: Mock MCP providers for unit tests without running real servers




