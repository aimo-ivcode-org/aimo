# Phase 2 ChatScopes: Detailed Implementation Plan

## Overview
ChatScopes define which tools and system messages are available in a conversation—the autonomous decision-making capabilities. ChatScopes are purely metadata (identifiers); system messages and context are resolved at chat time by the ChatClient builder. Every aimo instance has a built-in **global scope** that includes all available tools.

---

## Task 1: Update Annotations to Support Scoping

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/Annotations.kt`

**Changes**:
1. Add `scope: Array<String> = []` to `@ChatService` annotation
2. Add `scope: Array<String> = []` to `@Tool` annotation  
3. Add `scope: Array<String> = []` to `@SystemMessage` annotation

**Semantics**: Empty array means "available to all scopes" (backwards compatible).

**Example**:
```kotlin
@Tool(
    name = "admin_reset",
    description = "Reset system configuration",
    scope = ["admin", "superuser"]
)
fun resetConfig() { ... }
```

---

## Task 2: Create ChatScope Domain Models

**New Package**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatscope/`

### File: `ChatScope.kt`
```kotlin
package org.ivcode.aimo.core.chatscope

/**
 * Defines autonomous decision-making capabilities for a conversation.
 * 
 * ChatScope is purely metadata - it identifies which tools and system messages
 * apply. System messages are invoked at chat time with context-of-the-moment.
 * 
 * @property id Unique identifier (e.g., "global", "admin", "research")
 * @property displayName Human-readable name
 * @property description What this scope provides
 * @property toolNames Set of tool names available in this scope
 * @property systemMessageNames Set of system message callback names available
 */
data class ChatScope(
    val id: String,
    val displayName: String,
    val description: String,
    val toolNames: Set<String>,
    val systemMessageNames: Set<String>
)
```

### File: `ChatScopeProvider.kt`
```kotlin
package org.ivcode.aimo.core.chatscope

/**
 * Provider for retrieving available ChatScopes.
 * 
 * Supports optional interceptor chain for access control (Phase 3).
 */
interface ChatScopeProvider {
    /**
     * Get all scopes available (after applying any interceptors).
     * @param context Request context for filtering (user info, etc.)
     * @return List of available scopes
     */
    fun getScopes(context: Map<String, Any> = emptyMap()): List<ChatScope>
    
    /**
     * Get a specific scope by ID (after applying any interceptors).
     * @return ChatScope or null if not found/not accessible
     */
    fun getScope(id: String, context: Map<String, Any> = emptyMap()): ChatScope?
    
    /**
     * Get the global scope (always available, includes all tools).
     */
    fun getGlobalScope(): ChatScope
}
```

### File: `ChatScopeProviderImpl.kt`
```kotlin
package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.chatservice.SystemMessageCallback

/**
 * Default implementation of ChatScopeProvider.
 * 
 * Builds global scope from all registered tools/system messages.
 * Loads predefined scopes from configuration.
 * Supports interceptor chain for access control.
 */
class ChatScopeProviderImpl(
    private val allTools: List<AimoToolCallback>,
    private val allSystemMessages: List<SystemMessageCallback>,
    private val predefinedScopes: Map<String, ChatScope> = emptyMap(),
    private val toolScopeMap: Map<String, Set<String>>, // toolName -> scopes
    private val systemMessageScopeMap: Map<String, Set<String>>, // callback id -> scopes
    private val interceptors: List<ChatScopeProviderInterceptor> = emptyList()
) : ChatScopeProvider {
    
    private val globalScope: ChatScope = ChatScope(
        id = "global",
        displayName = "Global",
        description = "All available tools and system messages",
        toolNames = allTools.map { it.toolDefinition.name }.toSet(),
        systemMessageNames = allSystemMessages.indices.map { it.toString() }.toSet()
    )
    
    override fun getScopes(context: Map<String, Any>): List<ChatScope> {
        val allScopes = listOf(globalScope) + predefinedScopes.values
        
        // Apply interceptor chain for filtering
        if (interceptors.isEmpty()) return allScopes
        
        val chain = buildChain(interceptors, 0) { ctx ->
            ctx["scopes"] as List<ChatScope>
        }
        
        val mutableContext = mutableMapOf<String, Any>(
            "operation" to "getScopes",
            "scopes" to allScopes
        )
        mutableContext.putAll(context)
        
        @Suppress("UNCHECKED_CAST")
        return chain.proceed(mutableContext) as List<ChatScope>
    }
    
    override fun getScope(id: String, context: Map<String, Any>): ChatScope? {
        val scope = if (id == "global") globalScope else predefinedScopes[id]
        if (scope == null) return null
        
        // Apply interceptor chain for access control
        if (interceptors.isEmpty()) return scope
        
        val chain = buildChain(interceptors, 0) { ctx ->
            ctx["scope"]
        }
        
        val mutableContext = mutableMapOf<String, Any>(
            "operation" to "getScope",
            "scopeId" to id,
            "scope" to scope
        )
        mutableContext.putAll(context)
        
        return chain.proceed(mutableContext) as? ChatScope
    }
    
    override fun getGlobalScope(): ChatScope = globalScope
    
    private fun buildChain(
        interceptors: List<ChatScopeProviderInterceptor>,
        index: Int,
        finalAction: (MutableMap<String, Any>) -> Any?
    ): ChatScopeProviderInterceptor.Chain {
        return object : ChatScopeProviderInterceptor.Chain {
            override fun proceed(context: MutableMap<String, Any>): Any? {
                return if (index < interceptors.size) {
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, context)
                } else {
                    finalAction(context)
                }
            }
        }
    }
}
```

### File: `ChatScopeProviderInterceptor.kt`
```kotlin
package org.ivcode.aimo.core.chatscope

/**
 * Interceptor for ChatScopeProvider operations.
 * 
 * Enables security modules to filter available scopes based on user context.
 * Concrete implementations deferred to Phase 3.
 */
interface ChatScopeProviderInterceptor {
    fun intercept(chain: Chain, context: MutableMap<String, Any>): Any?
    
    interface Chain {
        fun proceed(context: MutableMap<String, Any>): Any?
    }
}
```

---

## Task 3: Update Tool/System Message Discovery

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/ControllerHelpers.kt`

**Changes**:
1. Update `toAimoToolCallbacks` to extract `scope` from `@Tool` annotation and return `ScopedToolCallback` wrapper
2. Update `toSystemMessageCallbacks` to extract `scope` from `@SystemMessage` annotation and return `ScopedSystemMessageCallback` wrapper

**New Classes in ControllerHelpers.kt**:
```kotlin
/**
 * Wrapper that associates a tool callback with its scopes.
 */
data class ScopedToolCallback(
    val callback: AimoToolCallback,
    val scopes: Set<String> // empty = available to all scopes
)

/**
 * Wrapper that associates a system message callback with its scopes.
 */
data class ScopedSystemMessageCallback(
    val callback: SystemMessageCallback,
    val scopes: Set<String> // empty = available to all scopes
)
```

**Implementation Notes**:
- When extracting tools, check `method.getAnnotation(Tool::class.java).scope`
- When extracting system messages from methods, check annotation similarly
- For fields/properties with @SystemMessage, check annotation on the field/property
- Empty scope array means component is available to ALL scopes

---

## Task 4: Update ChatServiceEntity

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/ChatServiceEntity.kt`

**Changes**:
```kotlin
data class ChatServiceEntity (
    val name: String,
    val clazz: Class<out Any>,
    val instance: Any,
    val tools: List<ScopedToolCallback>,           // Changed type
    val systemMessages: List<ScopedSystemMessageCallback>  // Changed type
)
```

Also update annotation extraction on `@ChatService` class level to capture service-level scopes.

---

## Task 5: Update AimoConfig Discovery

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/conf/AimoConfig.kt`

**Changes**:

1. **Update bean creation** to use scoped wrappers:
```kotlin
@Bean
fun createToolCallbacks(chatServices: List<ChatServiceEntity>): List<ScopedToolCallback> {
    return chatServices.flatMap { it.tools }
}

@Bean
fun createSystemMessageCallbacks(chatServices: List<ChatServiceEntity>): List<ScopedSystemMessageCallback> {
    return chatServices.flatMap { it.systemMessages }
}
```

2. **Create scope mappings**:
```kotlin
@Bean
fun createToolScopeMap(tools: List<ScopedToolCallback>): Map<String, Set<String>> {
    return tools.associate { scoped ->
        scoped.callback.toolDefinition.name to scoped.scopes
    }
}

@Bean
fun createSystemMessageScopeMap(systemMessages: List<ScopedSystemMessageCallback>): Map<String, Set<String>> {
    return systemMessages.mapIndexed { index, scoped ->
        index.toString() to scoped.scopes
    }.toMap()
}
```

3. **Create ChatScopeProvider bean**:
```kotlin
@Bean
fun createChatScopeProvider(
    tools: List<ScopedToolCallback>,
    systemMessages: List<ScopedSystemMessageCallback>,
    toolScopeMap: Map<String, Set<String>>,
    systemMessageScopeMap: Map<String, Set<String>>,
    properties: AimoProperties
): ChatScopeProvider {
    // Build predefined scopes from properties
    val predefinedScopes = buildPredefinedScopes(
        properties.scope,
        tools.map { it.callback },
        systemMessages.map { it.callback },
        toolScopeMap,
        systemMessageScopeMap
    )
    
    return ChatScopeProviderImpl(
        allTools = tools.map { it.callback },
        allSystemMessages = systemMessages.map { it.callback },
        predefinedScopes = predefinedScopes,
        toolScopeMap = toolScopeMap,
        systemMessageScopeMap = systemMessageScopeMap,
        interceptors = emptyList() // Phase 3
    )
}

private fun buildPredefinedScopes(
    scopeConfigs: Map<String, AimoChatScopeProperties>,
    allTools: List<AimoToolCallback>,
    allSystemMessages: List<SystemMessageCallback>,
    toolScopeMap: Map<String, Set<String>>,
    systemMessageScopeMap: Map<String, Set<String>>
): Map<String, ChatScope> {
    return scopeConfigs.mapValues { (id, config) ->
        // Filter tools: include if scope matches OR tool has no scope restriction
        val scopedTools = allTools.filter { tool ->
            val toolScopes = toolScopeMap[tool.toolDefinition.name] ?: emptySet()
            toolScopes.isEmpty() || toolScopes.contains(id)
        }
        
        // Filter system messages similarly
        val scopedSystemMessages = allSystemMessages.filterIndexed { index, _ ->
            val msgScopes = systemMessageScopeMap[index.toString()] ?: emptySet()
            msgScopes.isEmpty() || msgScopes.contains(id)
        }
        
        ChatScope(
            id = id,
            displayName = config.displayName,
            description = config.description,
            toolNames = scopedTools.map { it.toolDefinition.name }.toSet(),
            systemMessageNames = scopedSystemMessages.indices.map { it.toString() }.toSet()
        )
    }
}
```

4. **Update ChatClientBuilderFactory creation** to include ChatScopeProvider:
```kotlin
@Bean
fun createChatClientBuilderFactory(
    chatModelFactories: Map<String, AimoChatModelProviderFactory>,
    tools: List<ScopedToolCallback>,
    systemMessages: List<ScopedSystemMessageCallback>,
    chatScopeProvider: ChatScopeProvider,
    defaultInterceptors: List<ChatClientInterceptor>,
): ChatClientBuilderFactory {
    return ChatClientBuilderFactoryImpl(
        modelProviderFactories = chatModelFactories,
        toolCallbacks = tools.map { it.callback },
        systemMessages = systemMessages.map { it.callback },
        chatScopeProvider = chatScopeProvider,
        defaultInterceptors = defaultInterceptors,
    )
}
```

---

## Task 6: Update SystemMessageContext

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/SystemMessageContext.kt`

**Changes**:
```kotlin
class SystemMessageContext (
    val context: Map<String, Any>,
    val chatScopeId: String? = null
)
```

**Note**: No breaking changes to SystemMessageCallback signatures. The chatScopeId is available in context but callbacks don't need to use it unless they want scope-aware behavior.

---

## Task 7: Add ChatScope Selection to ChatClientBuilder

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/builder/ChatClientBuilder.kt`

**Changes**:
```kotlin
/**
 * Select a chat scope by ID.
 * 
 * At build() time, only tools and system messages belonging to this scope
 * will be passed to AimoChatClientImpl.
 * 
 * @param chatScopeId The scope ID (default: "global")
 * @return this builder for chaining
 */
fun withChatScope(chatScopeId: String): ChatClientBuilder
```

---

## Task 8: Update ChatClientBuilderImpl

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/builder/impl/ChatClientBuilderImpl.kt`

**Changes**:

1. **Add fields**:
```kotlin
private val chatScopeProvider: ChatScopeProvider
private var selectedChatScopeId: String? = null
```

2. **Add method**:
```kotlin
override fun withChatScope(chatScopeId: String): ChatClientBuilder {
    this.selectedChatScopeId = chatScopeId
    return this
}
```

3. **Update build() method**:
```kotlin
override fun build(): AimoChatClient {
    val model = selectedModel ?: getPrimaryModel()
    val conv = conversation
        ?: throw IllegalStateException("Conversation is required for ChatClient")
    
    // Resolve scope: use selected, or conversation metadata, or global
    val scopeId = selectedChatScopeId 
        ?: conv.getChatProperty("aimo.chatScopeId") as? String 
        ?: "global"
    
    val scope = chatScopeProvider.getScope(scopeId, emptyMap())
        ?: throw IllegalStateException("ChatScope not found or not accessible: $scopeId")
    
    // Filter tools and system messages by scope
    val scopedTools = toolCallbacks.filter { tool ->
        scope.toolNames.contains(tool.toolDefinition.name)
    }
    
    val scopedSystemMessages = systemMessages.filterIndexed { index, _ ->
        scope.systemMessageNames.contains(index.toString())
    }
    
    // Create base AimoChatClient with filtered tools/system messages
    val baseChatClient: AimoChatClient = AimoChatClientImpl(
        chatId = conv.chatId,
        conversation = conv,
        model = model,
        tools = scopedTools,
        systemMessages = scopedSystemMessages,
        chatScopeId = scopeId  // NEW parameter
    )
    
    val allInterceptors = builderInterceptors + factoryDefaultInterceptors
    if (allInterceptors.isEmpty()) {
        return baseChatClient
    }
    
    return InterceptedChatClient(baseChatClient, allInterceptors)
}
```

---

## Task 9: Update AimoChatClientImpl Constructor

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/client/chat/AimoChatClientImpl.kt`

**Changes**:

1. **Add constructor parameter**:
```kotlin
internal class AimoChatClientImpl (
    override val chatId: UUID,
    private val conversation: Conversation,
    private val model: AimoChatModelConfig,
    tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val chatScopeId: String? = null  // NEW
) : AimoChatClient {
    // ...existing code...
}
```

2. **Update createSystemMessageContext**:
```kotlin
private fun createSystemMessageContext(requestId: UUID, request: AimoChatRequest) = SystemMessageContext(
    context = createContextMap(
        requestId = requestId,
        requestContext = request.context,
    ),
    chatScopeId = chatScopeId
)
```

**No other changes needed** - the filtering already happened in the builder.

---

## Task 10: Update ChatClientBuilderFactory

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/builder/ChatClientBuilderFactory.kt`

**Changes**:
```kotlin
/**
 * Get all available chat scopes.
 * @return List of available chat scope IDs and metadata
 */
fun getChatScopes(context: Map<String, Any> = emptyMap()): List<ChatScope>

/**
 * Get a specific chat scope by ID.
 * @return ChatScope or null if not found
 */
fun getChatScope(id: String, context: Map<String, Any> = emptyMap()): ChatScope?

/**
 * Get the global scope (always available).
 */
fun getGlobalChatScope(): ChatScope
```

---

## Task 11: Update ChatClientBuilderFactoryImpl

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/builder/impl/ChatClientBuilderFactoryImpl.kt`

**Changes**:

1. **Add field**:
```kotlin
private val chatScopeProvider: ChatScopeProvider
```

2. **Update builder() methods** to pass chatScopeProvider:
```kotlin
override fun builder(): ChatClientBuilder {
    return ChatClientBuilderImpl(
        conversation = null,
        factoryDefaultInterceptors = defaultInterceptors,
        toolCallbacks = toolCallbacks,
        systemMessages = systemMessages,
        chatScopeProvider = chatScopeProvider,  // NEW
        getPrimaryModel = { _primaryModel },
        getModelByName = { name -> getModel(name) },
    )
}

override fun builder(conversation: Conversation): ChatClientBuilder {
    return ChatClientBuilderImpl(
        conversation = conversation,
        factoryDefaultInterceptors = defaultInterceptors,
        toolCallbacks = toolCallbacks,
        systemMessages = systemMessages,
        chatScopeProvider = chatScopeProvider,  // NEW
        getPrimaryModel = { _primaryModel },
        getModelByName = { name -> getModel(name) },
    )
}
```

3. **Implement new methods**:
```kotlin
override fun getChatScopes(context: Map<String, Any>): List<ChatScope> {
    return chatScopeProvider.getScopes(context)
}

override fun getChatScope(id: String, context: Map<String, Any>): ChatScope? {
    return chatScopeProvider.getScope(id, context)
}

override fun getGlobalChatScope(): ChatScope {
    return chatScopeProvider.getGlobalScope()
}
```

4. **Remove getAgent stub** (line 100-103):
```kotlin
// Delete this method - replaced by getChatScope
override fun getAgent(agentId: String): Any? {
    // TODO: Implement agent lookup in Phase 2
    return null
}
```

---

## Task 12: Add Conversation Helper Methods

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/conversation/ConversationExtensions.kt` (NEW)

```kotlin
package org.ivcode.aimo.core.conversation

private const val CHAT_SCOPE_ID_KEY = "aimo.chatScopeId"

/**
 * Get the selected chat scope ID for this conversation.
 * @return Chat scope ID or null if not set (defaults to "global")
 */
fun Conversation.getSelectedChatScope(): String? {
    return this.getChatProperty(CHAT_SCOPE_ID_KEY) as? String
}

/**
 * Set the chat scope for this conversation.
 * @param chatScopeId The scope ID to set
 */
fun Conversation.setSelectedChatScope(chatScopeId: String) {
    this.writeChatProperty(CHAT_SCOPE_ID_KEY, chatScopeId)
}

/**
 * Clear the chat scope selection (reverts to global).
 */
fun Conversation.clearSelectedChatScope(): Boolean {
    return this.deleteChatProperty(CHAT_SCOPE_ID_KEY)
}
```

---

## Task 13: Update Server ChatController

**File**: `aimo-server/src/main/kotlin/org/ivcode/aimo/server/controller/ChatController.kt`

**Currently no changes needed** - scope is read from conversation metadata automatically by the builder.

**Optional Enhancement** (for explicit scope override in request):
If you want to allow scope override via request body, update `ChatRequest` model and pass through:

```kotlin
// In ChatRequest.kt (if you add chatScopeId field)
data class ChatRequest (
    val prompt: String,
    val stream: Boolean = false,
    val chatScopeId: String? = null  // Optional scope override
)

// In ChatService.kt
val client = chatClientFactory
    .builder(conversation)
    .apply {
        request.chatScopeId?.let { withChatScope(it) }
    }
    .build()
```

---

## Task 14: Update Properties Configuration

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/properties/AimoProperties.kt`

**Changes**:

1. **Rename field** from `agents` to `scope`:
```kotlin
@ConfigurationProperties(prefix = "aimo")
@Validated
data class AimoProperties(
    var dataDir: String = "./data/conversations",
    var globalUserId: String? = "global",
    
    /**
     * ChatScope configurations.
     * Maps scope ID → scope configuration.
     */
    var scope: Map<String, AimoChatScopeProperties> = emptyMap(),  // RENAMED from agents
    
    var guardRails: Map<String, AimoGuardRailProperties> = emptyMap(),
    var interceptors: AimoInterceptorProperties = AimoInterceptorProperties()
)
```

2. **Rename and update properties class**:
```kotlin
/**
 * ChatScope configuration properties.
 * 
 * Structure:
 * ```yaml
 * aimo.scope:
 *   admin:
 *     display-name: "Administrator"
 *     description: "Full system access"
 *     tool-filter: ["admin_*", "system_*"]  # Wildcard patterns
 *     system-message-filter: ["*"]
 *   research:
 *     display-name: "Research Assistant"
 *     description: "Research and analysis tools"
 *     tool-filter: ["search", "summarize", "analyze"]
 *     system-message-filter: ["research_prompt"]
 * ```
 */
data class AimoChatScopeProperties(
    var displayName: String = "",
    var description: String = "",
    
    /**
     * Tool name filters (supports wildcards).
     * Empty means no tools. Use annotation scoping for finer control.
     */
    var toolFilter: List<String> = emptyList(),
    
    /**
     * System message callback filters (by index or name).
     * Empty means no system messages.
     */
    var systemMessageFilter: List<String> = emptyList()
)
```

**Note**: The YAML-based filtering (toolFilter, systemMessageFilter) is supplementary to annotation-based scoping. In Task 5, `buildPredefinedScopes` should be updated to apply both annotation scopes AND YAML filters.

---

## Task 15: Add Example Configuration

**File**: `application-phase2-chatscopes-example.yaml` (NEW in root)

```yaml
aimo:
  data-dir: ./data/conversations
  global-user-id: global
  
  # ChatScope definitions
  scope:
    admin:
      display-name: "Administrator"
      description: "Full system access with administrative tools"
      tool-filter: ["*"]  # All tools
      system-message-filter: ["*"]  # All system messages
    
    research:
      display-name: "Research Assistant"
      description: "Research and web search capabilities"
      tool-filter: ["search", "summarize", "web_fetch"]
      system-message-filter: ["research_system_msg"]
  
  model:
    ollama:
      gpt-oss:
        base-url: http://localhost:11434
        primary: true
        options:
          model: gpt-oss:20b
          temperature: 0.7
  
  interceptors:
    logging:
      enabled: true
      level: INFO
```

---

## Task 16: Update Existing ChatService Examples

**File**: `aimo-plugin-ui/src/main/kotlin/org/ivcode/aimo/ui/chatcontroller/TitleChatController.kt`

**Changes**: Add scope annotation if needed:
```kotlin
@SystemMessage(scope = ["admin"])  // Example: restrict to admin scope
fun titleUpdateInstructions(): String = ...
```

**Other files**: Review all @ChatService beans and add scope annotations as appropriate.

---

## Task 17: Update Unit Tests

**Files to Update**:
- `AimoChatClientImplMessageIdTest.kt` - mock scoped callbacks
- `ConversationImplTest.kt` - test scope metadata storage
- Any other tests that create ChatServiceEntity or use tool/system message discovery

**Example**:
```kotlin
// In tests, wrap callbacks in ScopedToolCallback
val scopedTools = listOf(
    ScopedToolCallback(mockToolCallback, emptySet())
)
```

---

## Task 18: Documentation Updates

1. **README.md**: Add ChatScope section explaining:
   - What scopes are
   - How to define scopes via annotations
   - How to configure scopes in YAML
   - Example usage

2. **AGENTS.md**: Update to use "ChatScope" terminology and document:
   - Scope annotation syntax
   - Global scope behavior
   - YAML configuration structure
   - Interceptor extension points

3. **ROADMAP.md**: Mark Phase 2 as in-progress or complete

---

## Implementation Order

Recommended sequence to minimize compilation errors:

1. Task 1 (annotations) + Task 2 (models)
2. Task 3 (discovery helpers) + Task 4 (ChatServiceEntity)
3. Task 5 (AimoConfig wiring)
4. Task 6 (SystemMessageContext)
5. Task 7-11 (Builder changes)
6. Task 12 (Conversation helpers)
7. Task 9 (AimoChatClientImpl)
8. Task 13-14 (Server + Properties)
9. Task 15-16 (Config + Examples)
10. Task 17-18 (Tests + Docs)

---

## Testing Strategy

1. **Unit Tests**: Verify scope filtering logic in isolation
2. **Integration Tests**: Test full flow from annotation → discovery → filtering → chat
3. **Manual Testing**: 
   - Start with no scope config (global scope)
   - Add predefined scopes
   - Test scope selection via conversation metadata
   - Verify tools are filtered correctly

---

## Questions to Resolve Before Implementation

1. **Tool/System Message Identification**: Should system messages have stable IDs (rather than indices) for scope filtering? Curr ently using index.toString() which is fragile.

2. **YAML Wildcard Matching**: Should toolFilter support wildcards ("admin_*")? If yes, need pattern matching logic.

3. **Scope Access Control**: Should getScope() throw if scope is not accessible, or return null? (Affects error handling)

4. **Conversation Scope Override**: Should request body support explicit chatScopeId override, or only use conversation metadata?

5. **Multiple Scopes per Component**: Current design allows `scope = ["scope1", "scope2"]`. Should a component be in multiple scopes, or should we enforce single-scope membership?

6. **Empty Scope Array Semantics**: Confirm that `scope = []` means "all scopes" rather than "no scopes".

 