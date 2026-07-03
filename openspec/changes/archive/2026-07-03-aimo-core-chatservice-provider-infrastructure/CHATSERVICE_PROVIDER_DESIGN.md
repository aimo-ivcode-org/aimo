# ChatServiceProvider Design Example

This document demonstrates what the `ChatServiceProvider` abstraction will look like, providing a clear picture before implementation begins.

## Key Concept

The provider abstraction separates **tool/system message discovery** from **scope building**. Currently, discovery is embedded in `AimoConfig` as reflection-based bean scanning. With providers, any source (annotations, MCP servers, external APIs) can contribute tools and messages in a uniform way.

**Note:** Scope information (`scopes: Set<String>`) is already embedded directly on the `ToolCallback` and `SystemMessageCallback` interfaces (done by the prior `aimo-core-refactor-callbacks` and `aimo-core-system-message-refactor` changes) — there are no `ScopedToolCallback`/`ScopedSystemMessageCallback` wrapper classes in the codebase, and this change does not reintroduce them. What's new here is a *provider-level* `scopes: Set<String>` on `ChatServiceProvider` itself (see §1), which is ANDed with each callback's own `scopes` when a scope is built (see capability spec for exact semantics).

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
     * Provider-level scope restriction. Empty set = unrestricted (this provider
     * contributes to every scope, subject to each callback's own `scopes`).
     * A non-empty set restricts this whole provider to only those scope ids,
     * regardless of what individual callbacks declare.
     *
     * `AnnotatedChatServiceProvider` always reports an empty set here, since it
     * aggregates beans from many independently-scoped `@ChatService` classes —
     * restriction for annotated tools/messages is fully handled by each
     * callback's own `scopes`, not by the provider.
     */
    val scopes: Set<String>

    /**
     * Get all tools available from this provider.
     *
     * Each tool callback includes its own scope restrictions (empty set = global/unrestricted).
     * Tools are discovered lazily or cached, depending on provider implementation.
     *
     * @return List of tool callbacks; empty if no tools are available
     */
    fun getTools(): List<ToolCallback>

    /**
     * Get all system messages available from this provider.
     *
     * Each message callback includes its own scope restrictions.
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

## 1a. Existing Base Interfaces (unchanged): `ToolCallback` and `SystemMessageCallback`

These interfaces already carry scope information directly (landed in the prior `aimo-core-refactor-callbacks` and `aimo-core-system-message-refactor` changes). This change does not modify them — shown here only for reference.

```kotlin
package org.ivcode.aimo.core.model

interface ToolCallback {
    val toolDefinition: ToolDefinition
    val scopes: Set<String>
    fun call(argumentsJson: String, context: Map<String, Any>): String
}
```

```kotlin
package org.ivcode.aimo.core.chatservice

interface SystemMessageCallback {
    val name: String
    val scopes: Set<String>
    fun call(context: SystemMessageContext): String?
}
```

---

## 2. Implementation: `AnnotatedChatServiceProvider`

Wraps the `ChatServiceEntity` list already assembled by `AimoConfig.createControllerEntities` (existing bean discovery + `toToolCallbacks`/`toSystemMessageCallbacks` assembly is reused as-is, not re-implemented). Always reports an empty provider-level `scopes` (global/unrestricted) since it aggregates beans from many independently-scoped `@ChatService` classes — restriction stays entirely at the callback level, unchanged from today.

```kotlin
package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.ToolCallback

/**
 * Provider that exposes tools and system messages discovered from @ChatService-annotated
 * beans. Wraps the existing ChatServiceEntity list (built by AimoConfig) rather than
 * re-scanning the ApplicationContext, so discovery logic has a single source of truth.
 *
 * This implementation maintains backward compatibility: all existing annotated services
 * and their scope behavior work unchanged.
 */
class AnnotatedChatServiceProvider(
    private val chatServiceEntities: List<ChatServiceEntity>,
    override val id: String = "annotated",
    override val displayName: String = "Annotated Chat Services",
    override val scopes: Set<String> = emptySet(), // always unrestricted at the provider level
) : ChatServiceProvider {

    override fun getTools(): List<ToolCallback> =
        chatServiceEntities.flatMap { it.tools }

    override fun getSystemMessages(): List<SystemMessageCallback> =
        chatServiceEntities.flatMap { it.systemMessages }

    override fun initialize() {
        logger.info(
            "AnnotatedChatServiceProvider initialized with ${getTools().size} tools, " +
            "${getSystemMessages().size} system messages"
        )
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
    private lateinit var allTools: List<ToolCallback>
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
        val toolList = mutableListOf<ToolCallback>()
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
    fun getAllTools(): List<ToolCallback> {
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

Shows how configuration wires providers alongside the existing static beans (which are kept, per tasks.md, for other consumers like `ChatClientBuilderFactoryImpl`).

```kotlin
package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.chatservice.ChatServiceEntity
import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.AnnotatedChatServiceProvider
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManager
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.model.ToolCallback
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AimoConfig {

    // ... existing createControllerEntities / createToolCallbacks / createSystemMessageCallbacks
    // beans unchanged (see current AimoConfig.kt) ...

    /**
     * Wire up the annotated chat service provider from the already-assembled
     * ChatServiceEntity list. In Phase 2, additional providers (e.g., an MCP
     * server provider) would be registered similarly.
     */
    @Bean
    fun createAnnotatedChatServiceProvider(
        chatServiceEntities: List<ChatServiceEntity>,
    ): ChatServiceProvider {
        return AnnotatedChatServiceProvider(chatServiceEntities)
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
     * ChatScopeProvider now resolves scopes dynamically via the provider manager
     * instead of the static tool/message lists it used before. Filtering applies
     * the two-condition AND described in the capability spec: a callback is
     * included only when both its owning provider's `scopes` and the callback's
     * own `scopes` allow the requested scope id.
     */
    @Bean
    fun createChatScopeProvider(
        providerManager: ChatServiceProviderManager,
        properties: AimoProperties,
    ): ChatScopeProvider {
        return ChatScopeProviderImpl(
            providerManager = providerManager,
            predefinedScopeConfigs = properties.scope,
            // ... other params unchanged ...
        )
    }
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
        // Example of a future provider for MCP tools. Unlike AnnotatedChatServiceProvider,
        // this could report a non-empty provider-level `scopes` if, e.g., a whole MCP
        // server's tools should only ever appear in one specific scope.
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
    override val scopes: Set<String> = emptySet(),
    private val mockTools: List<ToolCallback> = emptyList(),
    private val mockMessages: List<SystemMessageCallback> = emptyList(),
) : ChatServiceProvider {
    override fun getTools() = mockTools
    override fun getSystemMessages() = mockMessages
}

// Usage in tests:
@Test
fun testWithCustomTools() {
    // testToolCallback already carries its own `scopes` (e.g., setOf("test-scope"))
    val mockProvider = MockChatServiceProvider(
        mockTools = listOf(testToolCallback)
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




