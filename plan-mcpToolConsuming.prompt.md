# Plan: Phase 3 — MCP Tool Consuming (Integration Strategy)

**TL;DR**: MCP tools integrate at **bean/Spring configuration time** in the `aimo-mcp` module. MCP servers are configured in YAML, discovered tools are wrapped as `ScopedToolCallback` instances, and injected into `AimoConfig` via Spring's dependency injection. The core framework remains completely unaware of MCP—it just works with a single unified list of scoped callbacks from all sources. Scopes handle tool availability; core doesn't need to know about MCP servers.

## Checklist

### Phase 3.1: Foundation — Callback Group Infrastructure

**aimo-core**:
- [ ] Create `CallbackGroup.kt` interface (base for all groups)
- [ ] Create `ScopedCallbackGroup.kt` interface (extends CallbackGroup with scope metadata)
- [ ] Create `ScopedCallbackManager.kt` class
- [ ] Create `AnnotatedScopedCallbackGroup.kt` wrapper for annotated tools/messages
- [ ] Refactor `AimoConfig.kt`:
  - [ ] Create `annotatedScopedCallbackGroup()` bean
  - [ ] Create `scopedCallbackManager()` bean
  - [ ] Update `createChatScopeProvider()` to accept manager
  - [ ] Refactor `buildPredefinedScopes()` to query manager dynamically
  - [ ] Keep/deprecate static tool/message beans

**aimo-mcp**:
- [ ] Create module structure and `build.gradle.kts`
- [ ] Create `McpProperties.kt` with `@ConfigurationProperties(prefix = "aimo.mcp")`
- [ ] Create `McpServerConfig.kt` and `McpTransportConfig.kt`
- [ ] Create `ToolCallback.kt` interface (base for MCP tools)
- [ ] Create `MCPServer.kt` interface (extends CallbackGroup)
- [ ] Create `MCPServerManager.kt` class
- [ ] Create `McpToolProviderFactory.kt` @Bean to create MCPServer instances from config
- [ ] Create `McpScopedCallbackGroup.kt` wrapper for MCP servers
- [ ] Register `aimo-mcp` module in `settings.gradle.kts`

### Phase 3.2: Schema Conversion & Runtime

**aimo-mcp**:
- [ ] Create `McpSchemaConverter.kt` (OpenRPC → AIMO AimoToolDefinition)
- [ ] Create `McpClientManager.kt` (manages MCP client connections)
- [ ] Create `McpToolCallback.kt` (implements ToolCallback for MCP tools)
- [ ] Implement MCP tool naming: `"{serverId}:{toolName}"` format
- [ ] Add tool-refs validation (both annotated + MCP tools)
- [ ] Add scope validation (MCP server scopes match defined scopes)
- [ ] Create `PeriodicToolDiscoveryScheduler.kt` for periodic refresh
- [ ] Create admin endpoint `/aimo-api/admin/mcp-servers/refresh` for manual refresh
- [ ] Implement scope cache invalidation on tool discovery refresh

**aimo-core**:
- [ ] Update `ChatScopeProvider` to query `ScopedCallbackManager` for tools
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

- [ ] Unit tests for `ScopedCallbackManager`
- [ ] Unit tests for `AnnotatedScopedCallbackGroup`
- [ ] Unit tests for `McpScopedCallbackGroup`
- [ ] Unit tests for `PeriodicToolDiscoveryScheduler`
- [ ] Integration tests for MCP server discovery
- [ ] Integration tests for dynamic tool refresh
- [ ] Integration tests for scope-based tool filtering with MCP tools
- [ ] Test tool naming collision detection
- [ ] Test scope validation (fail-fast on invalid scopes)
- [ ] Test tool-refs with mixed annotated + MCP tools

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

5. **Wire callback groups into core** — Modify `AimoConfig.kt` and `ChatScopeProvider`:
   - Create `AnnotatedScopedCallbackGroup` bean wrapping annotated tools + system messages
   - Create `ScopedCallbackManager` bean that collects all `ScopedCallbackGroup` instances (annotated + MCP)
   - Update `createChatScopeProvider()` to accept `ScopedCallbackManager` instead of static lists
   - `ChatScopeProvider` queries manager for current tools/messages at scope build time
   - Refactor `buildPredefinedScopes()` to work with groups dynamically
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

## Core Integration: Scoped Callback Group Architecture

The key design uses a **scoped callback group architecture** for managing dynamic tool discovery:

```kotlin
// MCPServer interface - represents a single MCP server connection, extends CallbackGroup
interface MCPServer : CallbackGroup {
    override val id: String  // e.g., "claude-desktop", "external-service"
    override fun callbacks(): List<ToolCallback>  // current tools from this server
    fun refresh(): List<ToolCallback>  // rediscover and return updated tools
}

// MCPServerManager - manages all MCP servers
class MCPServerManager(val servers: List<MCPServer>) {
    fun refreshServer(serverId: String): List<ToolCallback> {
        // Refresh a single server's tools
        return servers.find { it.id == serverId }?.refresh() ?: emptyList()
    }
    
    fun refreshAllServers(): List<MCPServer> {
        // Refresh all servers (periodic scheduler, manual endpoint)
        servers.forEach { it.refresh() }
        return servers
    }
    
    fun getServer(serverId: String): MCPServer? = servers.find { it.id == serverId }
    fun getAllServers(): List<MCPServer> = servers
}


// CallbackGroup interface - base interface for any group of callbacks (non-scoped)
interface CallbackGroup {
    val groupId: String
    fun callbacks(): List<ToolCallback>
}

// ScopedCallbackGroup interface - extends CallbackGroup with scope metadata
interface ScopedCallbackGroup : CallbackGroup {
    val scopes: Set<String>  // scopes this group (and all its tools) participates in
}

// Implementation for MCP server group
class McpScopedCallbackGroup(val server: MCPServer) : ScopedCallbackGroup {
    override val groupId = "mcp:${server.id}"
    override val scopes = server.scopes  // Group declares its scopes
    
    override fun callbacks(): List<ToolCallback> {
        // Return server's tools as-is (raw, unscoped)
        // Scoping is handled at the group level, not individual tool level
        return server.callbacks()
    }
}

```

**Architecture Flow**:

1. **Startup**:
   - `MCPServerManager` created with all configured servers
   - Each server connects and discovers tools
   - For each server, `McpScopedCallbackGroup` created
   - Groups registered as Spring `@Bean` instances
   - `ScopedCallbackManager` collects all groups

2. **Periodic Refresh** (configurable interval):
   - Scheduler calls `mcpServerManager.refreshAllServers()`
   - Each server re-discovers tools independently
   - Server's tool list updates in-place
   - Next time `ChatScopeProvider` queries manager, it gets current tools

3. **Manual Refresh** (admin endpoint):
   - Endpoint calls `mcpServerManager.refreshServer(serverId)` or `refreshAllServers()`
   - Immediate tool re-discovery
   - ChatScope cache invalidated

4. **Runtime Tool Access**:
   - `ChatScopeProvider` queries `scopedCallbackManager.getAllCallbacks()`
   - Gets current tools from all groups (including refreshed servers)
   - Builds scopes with current tool set

**Responsibilities**:

- **MCPServer** (aimo-mcp): Connection, discovery, refresh
- **MCPServerManager** (aimo-mcp): Orchestrates all servers, refresh coordination
- **McpScopedCallbackGroup** (aimo-mcp): Wraps server, provides scope integration
- **ScopedCallbackManager** (aimo-core): Collects groups, provides unified interface
- **Core** (aimo-core): Queries manager for tools, builds scopes, unaware of servers/refresh

## AimoConfig Refactoring for Phase 3

**Current Architecture (Phase 2)**:
- `AimoConfig` injects static lists: `List<ScopedToolCallback>`, `List<AimoToolCallback>`, etc.
- `createChatScopeProvider()` receives static lists, builds scope maps once at startup
- `ChatScopeProvider` gets pre-built scope maps and static tool/message lists
- **Problem**: Can't support dynamic tool discovery (MCP refresh) with static lists

**Phase 3 Architecture**:
- `AimoConfig` works with callback groups instead of static lists
- Creates `AnnotatedScopedCallbackGroup` bean for annotated tools/messages
- Creates `ScopedCallbackManager` bean that collects all groups
- `ChatScopeProvider` queries manager for current tools at scope build time
- **Benefit**: Supports MCP refresh; tool list updates without restart

**Specific AimoConfig Changes**:

1. **Create AnnotatedScopedCallbackGroup bean**:
   ```kotlin
   @Bean
   fun annotatedScopedCallbackGroup(
       chatServices: List<ChatServiceEntity>
   ): ScopedCallbackGroup {
       return AnnotatedScopedCallbackGroup(
           tools = chatServices.flatMap { it.tools },
           systemMessages = chatServices.flatMap { it.systemMessages }
       )
   }
   ```

2. **Create ScopedCallbackManager bean**:
   ```kotlin
   @Bean
   fun scopedCallbackManager(
       groups: List<ScopedCallbackGroup>  // Spring auto-discovers all groups
   ): ScopedCallbackManager {
       return ScopedCallbackManager(groups)
   }
   ```

3. **Update createChatScopeProvider()**:
   - Accept `ScopedCallbackManager` instead of static lists
   - Accept `scopedSystemMessages` for backward compat (system messages still static for now)
   - Pass manager to `ChatScopeProvider`

4. **Refactor buildPredefinedScopes()**:
   - Query manager dynamically for current tools: `manager.getAllCallbacks()`
   - Build scope maps from current tools at scope build time
   - Still handle YAML config, tool-refs, system messages same way

5. **Remove/deprecate static beans** (optional for Phase 3):
   - Keep `createToolCallbacks()`, `createScopedToolCallbacks()` for now
   - Mark as deprecated; MCP phase will phase them out
   - Or remove if backward compat not needed

**Key Insight**:
- System messages can stay static for Phase 3 (no dynamic system message discovery in MCP)
- Tools must be dynamic via groups to support MCP refresh
- `ScopedCallbackManager` is the bridge: queries groups for current tools

## Further Considerations

1. **Connection Lifecycle**:
   - **Eager connect at startup** (recommended): All MCP servers connect during `McpToolProviderFactory` bean creation. Fails fast if unreachable; simplifies per-request logic.
   - **Lazy connect on first tool call**: Lower startup latency, but tool list not known until first use. More complex error recovery.
   - **Recommendation**: Start eager; add lazy mode as configurable option later.

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
   - **Tool caching**: Cache discovered tools in memory; periodically refresh from servers
   - **Scope invalidation**: When tools change, invalidate cached ChatScope objects so they're rebuilt with updated tool lists
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
- **Tool caching**: In-memory cache of discovered tools; periodically refreshed from servers
- **Scope invalidation**: When tool sets change, ChatScope cache is invalidated so scopes rebuild with updated tools
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
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/callback/CallbackGroup.kt` — Base interface for groups (non-scoped)
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/callback/ScopedCallbackGroup.kt` — Interface for scoped groups
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/callback/ScopedCallbackManager.kt` — Manages all groups
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/callback/AnnotatedScopedCallbackGroup.kt` — Wraps annotated tools/messages

### New Files (in aimo-mcp)
- `aimo-mcp/build.gradle.kts` — Module with MCP Java SDK dependency
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/properties/McpProperties.kt` — `@ConfigurationProperties(prefix = "aimo.mcp")`
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/server/MCPServer.kt` — Interface for a single MCP server
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/server/MCPServerManager.kt` — Manages all servers, refresh orchestration
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/config/McpToolProviderFactory.kt` — Creates `MCPServer` instances from `McpProperties`
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/group/McpScopedCallbackGroup.kt` — Implements `ScopedCallbackGroup`, wraps `MCPServer`
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/tool/ToolCallback.kt` — Base interface for tool callbacks
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/tool/McpToolCallback.kt` — Implements `ToolCallback` for MCP tools
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/schema/McpSchemaConverter.kt` — Converts MCP OpenRPC → AIMO schemas
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/client/McpClientManager.kt` — Manages MCP client connections + tool discovery
- `aimo-mcp/src/main/kotlin/org/ivcode/aimo/mcp/discovery/PeriodicToolDiscoveryScheduler.kt` — Scheduled periodic refresh
- `aimo-mcp/src/test/kotlin/org/ivcode/aimo/mcp/...` — Unit + integration tests
- `settings.gradle.kts` — Add `aimo-mcp` module

### Examples
- `examples/simple-ollama/build.gradle.kts` — Add `aimo-mcp` dependency
- `examples/simple-ollama/src/main/resources/application.yml` — Example YAML MCP config
- `examples/simple-ollama/src/test/kotlin/.../McpIntegrationTest.kt` — Integration test showing MCP tools work

### Modified Files (in aimo-core)
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/conf/AimoConfig.kt` — Create `ScopedCallbackManager` bean, register annotated callbacks group
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatscope/ChatScopeProvider.kt` — Query `ScopedCallbackManager.getAllCallbacks()` instead of static list


## Notes

- **Callback Group Hierarchy**: `CallbackGroup` is the base interface (non-scoped) for any group of callbacks. `ScopedCallbackGroup` extends `CallbackGroup` and adds scope metadata. `MCPServer` implements `CallbackGroup` directly (non-scoped).
- **MCP Server Management**: `MCPServer` represents a single server connection and is itself a callback group. `MCPServerManager` orchestrates all servers and refresh operations.
- **Group Wrapping**: `McpScopedCallbackGroup` wraps an `MCPServer` and implements `ScopedCallbackGroup`, providing scope integration at the group level.
- **Core Queries Groups**: `ChatScopeProvider` queries `ScopedCallbackManager.getAllCallbacks()`. Manager flattens all groups' scoped callbacks into single list for scope building.
- **MCP tool naming**: Each tool namespaced as `"{serverId}:{toolName}"` to avoid collisions with annotated tools (no prefix) and other MCP servers
- **tool-refs integration**: Both annotated tools (`"searchPapers"`) and MCP tools (`"claude-desktop:web_search"`) can be cherry-picked in YAML `tool-refs` lists
- **Namespace safety**: No collision possible; annotated tools and MCP tools coexist in the same scope via different naming conventions
- **Scope integration**: MCP tools inherit per-server scope restrictions; scopes automatically filter them just like annotated tools via `ChatScopeProvider`.
- **Scope configuration**: Each MCP server declares its `scope: List<String>` in YAML; scope IDs are validated at startup. Supports `["*"]` for all scopes.
- **tool-refs validation**: At startup, validate that all `tool-refs` names exist in the unified tool pool (both annotated and MCP tools with namespacing)
- **No builder methods for MCP**: The core framework does NOT have `withMcpServer()` or any MCP-related builder methods. Configuration is YAML-only.
- **No LLM changes needed**: Model providers (Ollama, Bedrock, etc.) receive tools as normal `AimoToolDefinition` list; they don't know tools are MCP-sourced or which scopes they belong to.
- **Backward compatible**: Existing apps without MCP config continue to work unchanged (empty MCP server list by default).
- **Dynamic tool discovery**: `MCPServerManager` supports periodic refresh (background scheduler) and manual refresh (admin endpoint). Tools updated in-place in servers; next scope build gets current tools.
- **Tool caching strategy**: Tools cached in-place in `MCPServer` instances. On refresh, server re-discovers and updates cache. Groups query servers for current tools via `callbacks()`.
- **Scope cache invalidation**: ChatScope objects rebuilt when underlying tool sets change; `ScopedCallbackManager` queries fresh tools each time `ChatScopeProvider` needs them.
- **Defer Phase 3.5 (Programmatic Scope Builder)**: Focus Phase 3 on consuming; Phase 3.5 is a future enhancement added to the ROADMAP.

