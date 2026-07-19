# Plan: Phase 3 — MCP Tool Consuming (Integration Strategy)

> ⚠️ **Implementation Note**: The plan was reviewed against the actual codebase. See **"Codebase Reconciliation"** section before implementing.

**TL;DR**: MCP tools integrate at **bean/Spring configuration time** in the `aimo-mcp` module. MCP servers are configured in YAML, discovered tools are wrapped as `ScopedToolCallback` instances, and injected into `AimoConfig` via Spring's dependency injection. The core framework remains completely unaware of MCP—it just works with a single unified list of scoped callbacks from all sources. Scopes handle tool availability; core doesn't need to know about MCP servers.

## Codebase Reconciliation

Before implementing, review these design constraints and required pre-Phase 3 work:

### Design Principle: No Circular Dependencies

`ChatServiceProvider` and all callback types (`ScopedToolCallback`, `ScopedSystemMessageCallback`) must stay in `aimo-core`. `aimo-mcp` imports from `aimo-core` (one-way). Core never imports from `aimo-mcp`.

- MCP tools implement the existing `AimoToolCallback` interface (as `McpAimoToolCallback`) and are wrapped in `ScopedToolCallback`
- No new callback interface types in `aimo-mcp`

### Pre-Phase 3 Refactor: Add `name` to `SystemMessageCallback`

`SystemMessageCallback` currently has no `name` property, forcing the name onto the wrapper `ScopedSystemMessageCallbackWithName` (the old wrapper name). This is inconsistent with `AimoToolCallback` which carries its name via `toolDefinition.name`.

**Required**: Add `val name: String` to `SystemMessageCallback` and rename the wrapper:

```kotlin
// Updated interface
interface SystemMessageCallback {
    val name: String
    fun call(context: SystemMessageContext): String?
}

// Wrapper renamed (name now on callback, not wrapper)
data class ScopedSystemMessageCallback(
    val callback: SystemMessageCallback,  // callback.name replaces wrapper's name field
    val scopes: Set<String>
)
```

**Files affected**:
- `SystemMessageCallback.kt` — add `val name: String`
- `FieldSystemMessageCallback.kt` — implement `name`
- `MethodSystemMessageCallback.kt` — implement `name`
- `PropertySystemMessageCallback.kt` — implement `name`
- `ControllerHelpers.kt` — rename `ScopedSystemMessageCallbackWithName` → `ScopedSystemMessageCallback`, remove `name` from wrapper
- `ChatServiceEntity.kt`, `AimoConfig.kt`, `ChatScopeProviderImpl.kt` — update type references
- All tests referencing `ScopedSystemMessageCallbackWithName` (now `ScopedSystemMessageCallback`) (now `ScopedSystemMessageCallback`)

> ⚠️ Do this refactor before implementing Phase 3.1.

### Reference: Correct Interface Signatures

```kotlin
// ChatServiceProvider — in aimo-core
interface ChatServiceProvider {
    val id: String
    val scopes: Set<String>
    fun callbacks(): List<ScopedToolCallback>           // AimoToolCallback + scopes
    fun systemMessages(): List<ScopedSystemMessageCallback>  // SystemMessageCallback + scopes
}

// MCPServer — in aimo-mcp, extends ChatServiceProvider
interface MCPServer : ChatServiceProvider {
    override val id: String
    override val scopes: Set<String>
    override fun callbacks(): List<ScopedToolCallback>
    fun refresh(): List<ScopedToolCallback>
}

// McpAimoToolCallback — implements EXISTING AimoToolCallback, wrapped in ScopedToolCallback
class McpAimoToolCallback(
    override val toolDefinition: AimoToolDefinition,
    private val mcpClient: McpClient,
    private val toolName: String
) : AimoToolCallback {
    override fun call(argumentsJson: String, context: Map<String, Any>): String { /* MCP invocation */ }
}
```



## Checklist

### Phase 3.1: Foundation — ChatServiceProvider Infrastructure

**aimo-core**:
- [ ] Create `ChatServiceProvider.kt` interface (in `chatservice` package, uses existing `ScopedToolCallback` / `ScopedSystemMessageCallback`)
- [ ] Create `ChatServiceProviderManager.kt` class
- [ ] Create `AnnotatedChatServiceProvider.kt` wrapper for `ChatServiceEntity` (annotated tools/messages)
- [ ] Refactor `AimoConfig.kt`:
  - [ ] Create `annotatedChatServiceProvider()` bean
  - [ ] Create `chatServiceProviderManager()` bean
  - [ ] Update `createChatScopeProvider()` to accept manager
  - [ ] Refactor `ChatScopeProvider` to filter callbacks by scope at runtime
  - [ ] Keep/deprecate static tool/message beans

**aimo-mcp**:
- [ ] Create module structure and `build.gradle.kts`
- [ ] Create `McpProperties.kt` with `@ConfigurationProperties(prefix = "aimo.mcp")`
- [ ] Create `McpServerConfig.kt` and `McpTransportConfig.kt`
- [ ] Create `MCPServer.kt` interface (extends `ChatServiceProvider`, returns `ScopedToolCallback`)
- [ ] Create `MCPServerManager.kt` class
- [ ] Create `McpToolProviderFactory.kt` @Bean to create MCPServer instances from config
- [ ] Create `McpChatServiceProvider.kt` wrapper for MCP servers
- [ ] Create `McpAimoToolCallback.kt` (implements existing `AimoToolCallback` for MCP tools)
- [ ] Register `aimo-mcp` module in `settings.gradle.kts`

### Phase 3.2: Schema Conversion & Runtime

**aimo-mcp**:
- [ ] Create `McpSchemaConverter.kt` (OpenRPC → AIMO `AimoToolDefinition`)
- [ ] Create `McpClientManager.kt` (manages MCP client connections)
- [ ] Implement MCP tool naming: `"{serverId}:{toolName}"` format (in `McpAimoToolCallback`)
- [ ] Add tool-refs validation (both annotated + MCP tools)
- [ ] Add scope validation (MCP server scopes match defined scopes)
- [ ] Create `PeriodicToolDiscoveryScheduler.kt` for periodic refresh
- [ ] Create admin endpoint `/aimo-api/admin/mcp-servers/refresh` for manual refresh
- [ ] Implement scope cache invalidation on tool discovery refresh

**aimo-core**:
- [ ] Update `ChatScopeProviderImpl` to query `ChatServiceProviderManager` for tools and messages
- [ ] Implement dynamic scope building (queries manager at scope build time)
- [ ] Test tool-refs cherry-picking with MCP tools

### Phase 3.3: Example & Documentation

**examples/simple-ollama**:
- [ ] Add `aimo-mcp` dependency in `build.gradle.kts`
- [ ] Create example YAML config in `application.yml` with MCP server definitions
- [ ] Create `McpIntegrationTest.kt` showing MCP tools work

**Documentation**:
- [ ] Document MCP server configuration in README
- [ ] Document tool naming convention (`"{serverId}:{toolName}"`)
- [ ] Document scope assignment for MCP servers
- [ ] Document manual/periodic refresh mechanism
- [ ] Document tool-refs cherry-picking for MCP tools
- [ ] Create integration guide (connecting to Claude Desktop, Cline, etc.)
- [ ] Document error handling and troubleshooting

### Testing

- [ ] Unit tests for `ChatServiceProviderManager`
- [ ] Unit tests for `AnnotatedChatServiceProvider`
- [ ] Unit tests for `McpChatServiceProvider`
- [ ] Unit tests for `PeriodicToolDiscoveryScheduler`
- [ ] Unit tests for callback scope filtering (verify tool.scopes filtering works)
- [ ] Integration tests for MCP server discovery
- [ ] Integration tests for dynamic tool refresh with scope reapplication
- [ ] Integration tests for scope-based tool filtering with MCP tools
- [ ] Test tool naming collision detection
- [ ] Test scope validation (fail-fast on invalid scopes)
- [ ] Test tool-refs with mixed annotated + MCP tools
- [ ] Test that each callback maintains its own scopes property

### Validation & Sign-off

- [ ] All tests passing (unit + integration)
- [ ] No regressions in Phase 2 functionality
- [ ] MCP tools work with all existing scope features
- [ ] Manual refresh endpoint works
- [ ] Periodic refresh scheduler works
- [ ] Documentation complete
- [ ] Example app runs successfully with MCP servers
- [ ] Code review approval

## High-Level Architecture

```
Spring Boot Startup
├─ AimoConfig discovers @ChatService beans → List<ScopedToolCallback> (annotated tools)
├─ McpToolProviderFactory (NEW, in aimo-mcp) creates MCP tools
│  ├─ Reads McpProperties from aimo.mcp.* YAML
│  ├─ Connects to each MCP server, discovers tools
│  ├─ Wraps each MCP tool as ScopedToolCallback with server's scopes
│  └─ Returns List<ScopedToolCallback> as @Bean
├─ AimoConfig.scopedToolCallbacks() merges annotated + MCP lists (via Spring injection)
├─ Single List<ScopedToolCallback> → ChatScopeProvider
├─ ChatScopeProvider builds scopes with filtered tools by scope ID
└─ ChatClientBuilderFactory ready for use

At Chat Time (runtime)
├─ ChatClientBuilderImpl.build() → select ChatScope by ID
├─ ChatScope contains filtered tools for that scope (annotated + MCP)
├─ AimoChatClientImpl invokes tools by name (MCP or annotated)
└─ Results streamed back through conversation
```

## Integration Steps

1. **Add MCP configuration properties** — Create a separate `McpProperties` class with `@ConfigurationProperties(prefix = "aimo.mcp")` in the `aimo-mcp` module (NOT extending `AimoProperties`). This follows the pattern used by provider-specific properties like Ollama and Bedrock, which are also in their respective adapter modules.

2. **Create `McpToolProviderFactory`** — Spring `@Bean` in `aimo-mcp` that reads `McpProperties` and produces `List<ScopedToolCallback>` from discovered MCP tools; each tool is wrapped with its server's scopes for automatic integration with ChatScopeProvider.

3. **Implement MCP schema conversion** — Convert MCP OpenRPC schemas → AIMO's `AimoToolDefinition` (JSON Schema Draft 2020-12); reuse existing parameter binding logic.

4. **Create `McpAimoToolCallback`** — Implements `AimoToolCallback`; holds reference to MCP client and tool definition; invokes tool via MCP Java SDK on `call()`. Wrapped as `ScopedToolCallback` during factory creation.

5. **Wire service providers into core** — Modify `AimoConfig.kt` and `ChatScopeProviderImpl`:
   - Create `AnnotatedChatServiceProvider` bean wrapping `ChatServiceEntity` tools + system messages
   - Create `ChatServiceProviderManager` bean that collects all `ChatServiceProvider` instances (annotated + MCP)
   - Update `createChatScopeProvider()` to accept `ChatServiceProviderManager` instead of static lists
   - `ChatScopeProviderImpl` queries manager for current tools/messages at scope build time
   - Refactor `buildPredefinedScopes()` to work with providers dynamically
   - Remove or deprecate static tool/message list beans (keep for backward compat if needed)

6. **Test & document** — Integration test showing YAML configuration; document server discovery, tool naming, scope assignment, and scope-based tool availability.

## Configuration

**YAML (Spring Boot properties) — ONLY way to configure MCP servers**:
```yaml
aimo:
  mcp:
    enabled: true
    servers:
      - id: claude-desktop
        transport:
          type: stdio
          command: "/path/to/mcp-server"
          args: ["--config", "file.json"]
        scope: ["research", "admin"]      # MCP server tools available ONLY in these scopes
        
      - id: external-service
        transport:
          type: sse
          url: "http://example.com/mcp"
          auth-token: "${MCP_TOKEN}"
        scope: ["*"]                       # MCP server tools available in ALL scopes
        
      - id: disabled-server
        transport:
          type: stdio
          command: "/path/to/server"
        scope: []                          # Empty scope = server disabled (no access)
```

**Important**: MCP servers are configured via YAML only. There are NO builder methods on `ChatClientBuilder` or any core API to add MCP servers. The `aimo-mcp` module connects, discovers tools, wraps them as `ScopedToolCallback` with their configured scopes, and injects them via Spring. The core framework has **no knowledge of MCP servers**—it only works with the merged list of `ScopedToolCallback` instances.

## Scope Semantics for MCP Servers

Each MCP server defines which ChatScopes can access its tools via the `scope` field:

- **`scope: ["research", "admin"]`** — Tools from this server are available ONLY when the chat is using the "research" or "admin" scope
- **`scope: ["*"]`** — Tools available in ALL ChatScopes (global + all named scopes)
- **`scope: ["global"]`** — Tools available only in the built-in global scope (no other scopes can use them)
- **`scope: []`** (empty list) — Server is effectively disabled; no scopes have access to its tools
- **Scope validation**: At startup, specified scope IDs are validated against defined scopes from `@ChatService(scope=[...])` annotations and `aimo.scope.*` config. Invalid scope references fail fast.

During chat runtime, when a user selects a ChatScope (e.g., "research"), all MCP tools configured for that scope become available to the LLM, alongside annotated tools and other MCP servers that support that scope.

## Core Integration: ChatServiceProvider Architecture

The key design uses existing `ScopedToolCallback` and `ScopedSystemMessageCallback` types — scopes are already on these wrappers. See **Codebase Reconciliation** section above for details on type alignment with existing code.

```kotlin
// MCPServer - in aimo-mcp, extends ChatServiceProvider from aimo-core
// Uses EXISTING ScopedToolCallback / ScopedSystemMessageCallback types
interface MCPServer : ChatServiceProvider {
    override val id: String
    override val scopes: Set<String>  // provider-level scope (e.g., server config `scope: ["research"]`)
    override fun callbacks(): List<ScopedToolCallback>  // tools with scopes already set
    fun refresh(): List<ScopedToolCallback>
}

// MCPServerManager - manages all MCP servers, applies scope config to each tool
class MCPServerManager(val servers: List<MCPServer>) {
    fun refreshServer(serverId: String): List<ScopedToolCallback> {
        return servers.find { it.id == serverId }?.refresh() ?: emptyList()
    }
    
    fun refreshAllServers(): List<MCPServer> {
        servers.forEach { it.refresh() }
        return servers
    }
    
    fun getServer(serverId: String): MCPServer? = servers.find { it.id == serverId }
    fun getAllServers(): List<MCPServer> = servers
}

// ChatServiceProvider - in aimo-core (read-only, uses EXISTING types)
interface ChatServiceProvider {
    val id: String
    val scopes: Set<String>
    fun callbacks(): List<ScopedToolCallback>                        // EXISTING type
    fun systemMessages(): List<ScopedSystemMessageCallback>  // EXISTING type
}

// McpChatServiceProvider - in aimo-mcp, wraps MCPServer
class McpChatServiceProvider(val server: MCPServer) : ChatServiceProvider {
    override val id = "mcp:${server.id}"
    override val scopes = server.scopes
    
    override fun callbacks(): List<ScopedToolCallback> = server.callbacks()
    
    override fun systemMessages(): List<ScopedSystemMessageCallback> = emptyList()
    // MCP servers provide tools only, not system messages
}

// AnnotatedChatServiceProvider - in aimo-core, wraps @ChatService beans
class AnnotatedChatServiceProvider(
    override val id: String,
    override val scopes: Set<String>,
    private val tools: List<ScopedToolCallback>,                         // from ChatServiceEntity.tools
    private val messages: List<ScopedSystemMessageCallback>      // from ChatServiceEntity.systemMessages
) : ChatServiceProvider {
    override fun callbacks() = tools
    override fun systemMessages() = messages
}

```

**Architecture Flow**:

1. **Startup**:
   - Each MCP server connects and discovers tools via MCP protocol
   - For each discovered tool, wrap as `McpAimoToolCallback` (implements existing `AimoToolCallback`)
   - Apply server's scope config: wrap each callback as `ScopedToolCallback(callback, serverScopes)`
   - `McpChatServiceProvider` created wrapping the server
   - All providers (annotated + MCP) collected in `ChatServiceProviderManager`

2. **Periodic Refresh**:
   - `mcpServerManager.refreshAllServers()` re-discovers tools from MCP servers
   - New `ScopedToolCallback` instances created with scopes re-applied from server config
   - Tools updated in-place; next query gets current tools

3. **Runtime Tool Access**:
   - `ChatScopeProvider` queries `chatServiceProviderManager.getAllCallbacks()`
   - Gets all `ScopedToolCallback` instances from all providers
   - Filters by: `(provider.scopes contains requestedScope) AND (callback.scopes contains requestedScope)`
   - Builds scope with filtered tools (as `AimoToolCallback` list for the model)
   - Gets all tools from all providers
   - For each tool: checks if (provider.scopes contains requestedScope) AND (tool.scopes contains requestedScope)
   - Both conditions must be true to include tool in scope
   - Builds scope with filtered tools

**Responsibilities**:

- **ToolCallback / SystemMessageCallback**: Each callback owns its `scopes: Set<String>` (callback-level scope restriction)
- **ChatServiceProvider**: Declares provider-level `scopes: Set<String>` that restrict which scopes can access this provider
- **MCPServer**: Discovers tools, returns them as ToolCallback instances, declares provider-level scopes
- **MCPServerManager**: Applies scope config to each discovered tool
- **McpChatServiceProvider**: Provides access to server's tools with both provider-level and callback-level scopes
- **ChatServiceProviderManager**: Collects all providers
- **ChatScopeProvider**: Filters callbacks by checking both provider scopes AND callback scopes at runtime

## AimoConfig Refactoring for Phase 3

**Current Architecture (Phase 2)**:
- `AimoConfig` injects static lists: `List<ScopedToolCallback>`, `List<AimoToolCallback>`, etc.
- `createChatScopeProvider()` receives static lists, builds scope maps once at startup
- `ChatScopeProvider` gets pre-built scope maps and static tool/message lists
- **Problem**: Can't support dynamic tool discovery (MCP refresh) with static lists

**Phase 3 Architecture**:
- `AimoConfig` works with service providers instead of static lists
- Creates `AnnotatedChatServiceProvider` bean for annotated tools/messages (each with scopes)
- Creates `ChatServiceProviderManager` bean that collects all providers
- `ChatScopeProvider` queries manager for current tools/messages and filters by scope ID at runtime
- **Benefit**: Supports MCP refresh; tool list updates without restart

**Specific AimoConfig Changes**:

1. **Parse @ChatService into AnnotatedChatServiceProvider**:
   - Discover all `@ChatService` beans (as currently done)
   - For each bean, extract its tools and system messages
   - Create `AnnotatedChatServiceProvider` instance with all tools/messages and provider-level scopes (if any)
   - Register as Spring `@Bean`
   - **Key**: Each `@ChatService` becomes a provider; tools/messages are no longer static lists

2. **Create AnnotatedChatServiceProvider bean**:
   ```kotlin
   @Bean
   fun annotatedChatServiceProvider(
       chatServices: List<ChatServiceEntity>
   ): ChatServiceProvider {
       return AnnotatedChatServiceProvider(
           scopes = emptySet(),  // or extracted from @ChatService if provider needs scope restriction
           tools = chatServices.flatMap { it.tools },  // each tool already has scopes
           systemMessages = chatServices.flatMap { it.systemMessages }  // each message already has scopes
       )
   }
   ```

3. **Create ChatServiceProviderManager bean**:
   ```kotlin
   @Bean
   fun chatServiceProviderManager(
       providers: List<ChatServiceProvider>  // Spring auto-discovers all providers
   ): ChatServiceProviderManager {
       return ChatServiceProviderManager(providers)
   }
   ```

4. **Update createChatScopeProvider()**:
   - Accept `ChatServiceProviderManager` instead of static lists
   - Pass manager to `ChatScopeProvider`

5. **Refactor ChatScopeProvider**:
   - Query manager dynamically for current tools/messages: `manager.getAllCallbacks()`, `manager.getAllSystemMessages()`
   - At scope-build time, filter by scope: for each callback, check (provider.scopes.contains(scopeId) AND callback.scopes.contains(scopeId))
   - Only include callback if both provider and callback allow the requested scope
   - Build scope map from filtered callbacks
   - Still handle YAML config, tool-refs, system message refs same way

6. **Rework ChatScope construction**:
   - Update `ChatScope` data class to hold `providers: List<ChatServiceProvider>` in addition to filtered callbacks
   - Scope building now produces a ChatScope with both the providers that contribute to it AND the filtered tools/messages
   - This enables scope rebuilding when providers refresh

7. **Support for Additional Tools/Messages Per Scope**:
   - Scopes can have individual tools/messages added beyond those from providers
   - Update scope definition (YAML or programmatic) to allow:
     ```yaml
     aimo:
       scope:
         research:
           tool-refs: ["searchPapers"]  # from providers
           additional-tools: [...]  # new: individual ToolCallback instances
           system-message-refs: ["research_guide"]
           additional-messages: [...]  # new: individual SystemMessageCallback instances
     ```
   - At scope build time, include all three sources: provider callbacks + additional tools + additional messages
   - Validate all callbacks (regardless of source) have compatible scopes

8. **Remove/deprecate static beans** (optional for Phase 3):
   - Keep `createToolCallbacks()`, `createScopedToolCallbacks()` for now
   - Mark as deprecated
   - Or remove if backward compat not needed

**Key Insight**:
- Scopes exist at two levels: provider-level and callback-level
- Provider scopes restrict which scopes can access ANY tool/message from that provider
- Callback scopes restrict which scopes can access THAT SPECIFIC tool/message
- `ChatScopeProvider` filters callbacks by checking both provider scopes AND callback scopes
- MCP scope config applies to provider level (server's scope); individual tool scopes can further restrict

## Further Considerations

1. **Individual Tool & System Message Addition to Scopes**:
   - Scopes should support adding individual `ToolCallback` and `SystemMessageCallback` instances in addition to those from `ChatServiceProvider`
   - This enables mixing provider-sourced callbacks with one-off/dynamic callbacks
   - **Implementation**: Extend `ChatScope` to hold both:
     - `providers: List<ChatServiceProvider>` (sources of callbacks)
     - `additionalTools: List<ToolCallback>` (individual tools added directly)
     - `additionalSystemMessages: List<SystemMessageCallback>` (individual messages added directly)
   - **Scope Building**: When building a scope, include callbacks from all three sources:
     1. Filtered callbacks from all providers (after scope filtering)
     2. Additional tools that match the scope
     3. Additional system messages that match the scope
   - **Use Cases**: 
     - Add one-off tools for specific scopes without creating `@ChatService` beans
     - Mix MCP tools, annotated tools, and dynamically created tools in one scope
     - Support programmatic scope building with fluent API (future enhancement)
   - **Validation**: At scope build time, validate that all callbacks (provider + additional) have compatible scopes

2. **ChatScope Refactoring** (updated):
   - Current `ChatScope` holds only filtered tools and system messages (results of scope building)
   - Phase 3 will rework to also hold:
     - `providers: List<ChatServiceProvider>` (sources that contribute to this scope)
     - `additionalTools: List<ToolCallback>` (one-off tools added directly to this scope)
     - `additionalSystemMessages: List<SystemMessageCallback>` (one-off messages added directly to this scope)
   - `ChatScope` becomes a complete snapshot: providers + filtered provider-callbacks + additional callbacks
   - Enables dynamic scope rebuilding when providers refresh

3. **Scope Construction Refactoring** (updated):
   - Current process: Static lists of tools/messages → hand-filtered maps → ChatScope objects at startup
   - Phase 3 process: Parse `@ChatService` annotations into `ChatServiceProvider` → collect all providers in `ChatServiceProviderManager` → dynamic scope building on-demand with support for additional callbacks
   - **New steps**:
     1. Parse `@ChatService` beans into `ChatServiceProvider` instances
     2. Collect all providers in `ChatServiceProviderManager`
     3. For each scope in YAML or programmatic definition:
        a. Query manager for provider-sourced callbacks
        b. Filter by scope ID (both provider-level and callback-level scopes)
        c. Add any additional tools/messages registered for this scope
        d. Build `ChatScope` with providers + all callbacks (provider + additional)
   - **Timing**: Scope building can happen per-request (dynamic) or cached with invalidation on provider refresh

4. **Connection Lifecycle**:

2. **Tool Naming Collisions**:
   - Prefix by server: `"claude-desktop:searchWeb"` → namespace-safe but verbose
   - Fail fast at startup with detailed error → catches config issues early
   - Last-loaded wins → implicit precedence, harder to debug
   - **Recommendation**: Fail fast + offer prefixing as escape hatch in config

3. **Scope Assignment (granularity)**:
   - Per-server scope (recommended): All tools from a server inherit server's scopes
   - Per-tool scope (future): Fine-grained control via per-tool scope overrides in config
   - **Recommendation**: Stick with per-server scope; allow per-tool scope overrides later if needed

4. **Error Handling for Tool Invocation**:
   - Retry transient errors → interceptor pattern handles this (no MCP-specific logic)
   - Graceful degradation (remove tool from scope if server dies) → complex, deferred
   - **Recommendation**: Basic error messages back to model; retry/recovery via interceptors

5. **Dynamic Tool Set Changes**:
   - MCP servers may add/remove tools during runtime (not just at startup)
   - **Manual refresh**: Admin endpoint or method to force tool re-discovery from all servers
   - **Periodic re-discovery**: Background task that rechecks available tools at configurable intervals (e.g., every 5 minutes)
   - **Tool caching**: Discovered tools are cached in-memory in `MCPServer` instances. When refresh is triggered (manual or periodic):
     1. Re-connect to MCP server
     2. Re-discover available tools via MCP protocol
     3. Update cached tools in-place (replaces previous cache)
     4. Apply scope configuration to each tool
     5. ChatScope objects are rebuilt on next query
   - **Scope invalidation**: When tools change, ChatScope cache is invalidated (scope objects rebuilt on next request) so they include updated tool sets
   - **Recommendation**: Support both manual refresh (explicit control) + periodic re-discovery (catch changes automatically)

## Key Design Decisions

### Decision 1: Connection Strategy
- **Eager connect at startup** — simplest implementation, fail-fast configuration errors

### Decision 2: Naming Collisions
- **Fail fast at startup** — clear error messages, prevents silent shadowing

### Decision 3: Scope Granularity
- **Per-server scope** — all tools from a server share scopes

### Decision 4: Error Recovery
- **Delegate to interceptors** — MCP tool callbacks behave like any other tool

### Decision 5: Core Isolation (CRITICAL)
- **Core framework NEVER knows about MCP** — MCP is purely an `aimo-mcp` module concern
- **No builder methods** — Do NOT add `withMcpServer()` or similar to ChatClientBuilder
- **No MCP knowledge in core** — Scopes are the only integration point
- **Configuration: YAML only** — fully handled by `aimo-mcp` beans via Spring injection

### Decision 6: MCP Tool Naming Convention
- **Format**: `"{serverId}:{toolName}"` for all MCP tools (e.g., `"claude-desktop:web_search"`)
- **Annotated tools**: No prefix (e.g., `"searchPapers"`)
- **Benefits**: Eliminates namespace collisions; both types can coexist in `tool-refs`
- **tool-refs integration**: Can cherry-pick both annotated and MCP tools using their respective naming conventions
- **Validation**: At startup, validate all `tool-refs` names against unified tool pool

### Decision 7: Dynamic Tool Discovery Strategy
- **Manual refresh**: Admin endpoint/method to force re-discovery from all MCP servers
- **Periodic re-discovery**: Background scheduled task runs every N minutes (configurable, default: 5 min)
- **Tool caching strategy** (clarified):
  - Discovered tools are stored in-memory in `MCPServer` instances (the cache)
  - When refresh is triggered: re-discover tools from MCP server, update cached tools in-place
  - Scope builders query the cache via `server.callbacks()` — always get latest cached tools
  - No restart needed — scope rebuilding picks up changes automatically on next query
- **Scope invalidation**: ChatScope cache invalidated on tool discovery refresh, forcing scopes to rebuild
- **Configuration**: `aimo.mcp.discovery-interval-minutes: 5` (0 = disabled, only manual refresh)
- **Rationale**: MCP servers can dynamically add/remove tools; framework must support detecting changes without requiring restart

## Implementation Roadmap

### Phase 3.1: Foundation
- Create `McpProperties`, `McpServerConfig`, `McpTransportConfig` classes in `aimo-mcp` module
- Create `McpToolProviderFactory` @Bean with eager server connection and scope assignment
   - **Namespacing with server ID prefix** (recommended): `"{serverId}:{toolName}"` for MCP tools, no prefix for annotated tools
   - Example: MCP tool "web_search" from "claude-desktop" server → `"claude-desktop:web_search"`
   - Enables cherry-picking via `tool-refs`: `["searchPapers", "claude-desktop:web_search"]` (both types coexist)
   - Fail-fast validation at startup if referenced tool doesn't exist
   - **Recommendation**: Use server ID prefix; ensures no collision between annotated and MCP tools
### Phase 3.2: Schema Conversion & Runtime
- OpenRPC → JSON Schema converter
- MCP tool wrapping with scope enforcement (each tool inherits server's scopes)
- **MCP tool naming**: Assign namespaced names (`"{serverId}:{toolName}"`) to all MCP tools
- **tool-refs integration**: Enable `tool-refs` to cherry-pick both annotated and MCP tools (MCP tools use prefixed names)
- Scope validation: ensure all scope IDs in MCP server config match defined scopes
- Tool naming collision detection (per-scope, fail-fast with helpful error)
- Validate `tool-refs` names at startup (both annotated and MCP) against actual available tools
- **Dynamic tool discovery**: Implement periodic re-discovery scheduler + manual refresh endpoint
  - Schedule background task to re-discover tools from all servers at configurable intervals
  - Admin endpoint `/aimo-api/admin/mcp-servers/refresh` to force immediate re-discovery
  - Tool cache invalidation and ChatScope rebuild on tool set changes

### Phase 3.3: Example & Documentation
- Example app showing YAML configuration
- Integration guide (connecting to Claude Desktop, Cline, etc.)
- Error handling and troubleshooting guide
- Scope usage documentation

## Files to Create/Modify

### New Files (in aimo-core)
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/ChatServiceProvider.kt` — Base interface for providers (uses existing `ScopedToolCallback`, `ScopedSystemMessageCallback`)
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/ChatServiceProviderManager.kt` — Manages all providers
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/AnnotatedChatServiceProvider.kt` — Wraps `ChatServiceEntity` (annotated tools/messages already scoped)

> ⚠️ Note: `ToolCallback.kt` and `SystemMessageCallback.kt` are NOT new files. Use existing `ScopedToolCallback` (in `ControllerHelpers.kt`) and `ScopedSystemMessageCallback` (in `ControllerHelpers.kt`).

### New Files (in aimo-mcp)
- `aimo-mcp/build.gradle.kts` — Module with MCP Java SDK dependency
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/properties/McpProperties.kt` — `@ConfigurationProperties(prefix = "aimo.mcp")`
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/server/MCPServer.kt` — Interface for a single MCP server (extends `ChatServiceProvider`, returns `ScopedToolCallback`)
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/server/MCPServerManager.kt` — Manages all servers, applies scope config to each tool
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/config/McpToolProviderFactory.kt` — Creates `MCPServer` instances from `McpProperties`
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/provider/McpChatServiceProvider.kt` — Implements `ChatServiceProvider`, wraps `MCPServer`
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/tool/McpAimoToolCallback.kt` — Implements **existing** `AimoToolCallback` interface for MCP tools
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/schema/McpSchemaConverter.kt` — Converts MCP OpenRPC → AIMO schemas (`AimoToolDefinition`)
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/client/McpClientManager.kt` — Manages MCP client connections + tool discovery
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/discovery/PeriodicToolDiscoveryScheduler.kt` — Scheduled periodic refresh
- `aimo-mcp/src/test/kotlin/org/ivcode/aimo/mcp/...` — Unit + integration tests
- `settings.gradle.kts` — Add `aimo-mcp` module

### Examples
- `examples/simple-ollama/build.gradle.kts` — Add `aimo-mcp` dependency
- `examples/simple-ollama/src/main/resources/application.yml` — Example YAML MCP config
- `examples/simple-ollama/src/test/kotlin/.../McpIntegrationTest.kt` — Integration test showing MCP tools work

### Modified Files (in aimo-core)
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/conf/AimoConfig.kt` — Create `ChatServiceProviderManager` bean, wrap `ChatServiceEntity` list in `AnnotatedChatServiceProvider`
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatscope/ChatScopeProviderImpl.kt` — Query `ChatServiceProviderManager`, filter `ScopedToolCallback` by scope ID at runtime
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatscope/ChatScope.kt` — Update to hold `providers: List<ChatServiceProvider>`, `additionalTools: List<ScopedToolCallback>`, `additionalSystemMessages: List<ScopedSystemMessageCallback>`


## Notes

- **Existing Types Used**: `ScopedToolCallback` (wraps `AimoToolCallback` + scopes) and `ScopedSystemMessageCallback` (wraps `SystemMessageCallback` + name + scopes) already exist in `aimo-core`. `ChatServiceProvider` uses these — no new callback types needed.
- **MCP Tool Implementation**: `McpAimoToolCallback` implements the existing `AimoToolCallback` interface. It is then wrapped in `ScopedToolCallback` just like annotated tools.
- **No Circular Dependency**: `ChatServiceProvider` and all callback types are in `aimo-core`. `aimo-mcp` imports from `aimo-core` (one-way). Core never imports from `aimo-mcp`.
- **Scope Filtering (Two Levels)**: `ChatScopeProvider` filters callbacks by checking: (1) provider's `scopes` contains requested scope, AND (2) `ScopedToolCallback.scopes` contains requested scope. Both must be true.
- **ChatServiceProvider Hierarchy**: `ChatServiceProvider` is the base interface (read-only) for any provider. Returns existing wrapper types that already carry scope information.
- **Provider Wrapping**: `McpChatServiceProvider` wraps an `MCPServer` and provides access to its tools as `ScopedToolCallback` instances.
- **Individual Callback Addition**: Scopes can include individual `ScopedToolCallback` and `ScopedSystemMessageCallback` instances in addition to provider-sourced callbacks.
- **Core Filters by Scope**: `ChatScopeProvider` queries `ChatServiceProviderManager`, then filters using `ScopedToolCallback.scopes` and provider-level `scopes`.
- **MCP tool naming**: Each tool namespaced as `"{serverId}:{toolName}"` to avoid collisions with annotated tools (no prefix) and other MCP servers
- **tool-refs integration**: Both annotated tools (`"searchPapers"`) and MCP tools (`"claude-desktop:web_search"`) can be cherry-picked in YAML `tool-refs` lists
- **Namespace safety**: No collision possible; annotated tools and MCP tools coexist in the same scope via different naming conventions
- **Scope configuration**: Each MCP server declares its `scope: List<String>` in YAML. During discovery, each tool is wrapped as `ScopedToolCallback(callback, serverScopes)`.
- **tool-refs validation**: At startup, validate that all `tool-refs` names exist in the unified tool pool (both annotated and MCP tools with namespacing)
- **No builder methods for MCP**: The core framework does NOT have `withMcpServer()` or any MCP-related builder methods. Configuration is YAML-only.
- **No LLM changes needed**: Model providers (Ollama, Bedrock, etc.) receive tools as normal `AimoToolDefinition` list via existing `AimoToolCallback`; they don't know which are MCP-sourced.
- **Backward compatible**: Existing apps without MCP config continue to work unchanged (empty MCP server list by default).
- **Tool caching strategy**: Tools discovered from MCP servers are cached in-place in `MCPServer` instances. When refresh (manual or periodic) is triggered: (1) re-discover tools from MCP server, (2) update cached tools in-place, (3) apply scope config to tools. Scope builders query `server.callbacks()` to get latest cached tools. No restart needed — changes picked up on next scope query.
- **Scope cache invalidation**: ChatScope objects rebuilt when underlying tool sets change; `ChatServiceProviderManager` queries fresh tools and messages each time `ChatScopeProvider` needs them.
- **Provider interface is read-only**: `ChatServiceProvider` interface offers no mutation methods. Refresh is internal to MCP server management, not exposed through provider interface.
- **Defer Phase 3.5 (Programmatic Scope Builder)**: Focus Phase 3 on consuming; Phase 3.5 is a future enhancement added to the ROADMAP.

