# Tasks: aimo-mcp-client module

## Protocol Layer Tasks

- [x] Implement JSON-RPC 2.0 messaging layer
   - Request/response/notification message types with proper ID tracking
   - Error response handling
   - Message serialization/deserialization using Jackson
- [x] Implement MCP data layer lifecycle (init/capabilities/terminate)
   - `initialize` request/response with protocol version negotiation
   - `initialized` notification
   - Capability exchange (tools, resources, prompts, etc.)
   - `close` notification and connection teardown
- [x] Implement Stdio transport (MVP)
   - Process spawning and lifecycle management
   - JSONL message framing and streaming
   - Stream-based I/O handling and error recovery
- [x] Create protocol client abstraction for reuse in future server implementation

## Client Integration Tasks

- [x] Create `aimo-mcp-client` module scaffolding, register in root `settings.gradle.kts`
  - Module structure follows the same pattern as `aimo-model-ollama`: `api(project(":aimo-core"))` + Spring Boot starter
- [x] Add YAML-backed MCP server configuration to `application.yml`
  - Include `aimo.mcp.enabled` property (default `true`)
  - Include `aimo.mcp.required` property (default `true`; set `false` to bypass unreachable servers)
  - Include `aimo.mcp.discovery-interval-minutes` property (default `5`; `0` = disabled)
- [x] Implement MCP client manager: one persistent protocol client per configured server
  - Eager connect at startup
  - Fail fast on config errors or unreachable servers (unless `aimo.mcp.required: false`)
  - Connection lifecycle management
- [x] Implement startup tool discovery using MCP `tools/list` RPC method and in-memory caching per server
- [x] Implement tool invocation via MCP `tools/call` RPC method
  - Marshal AIMO callback arguments into MCP RPC format
  - Handle RPC errors and timeouts
  - Return results as strings to AIMO callback contract
  - Log tool execution with context for troubleshooting
- [x] Implement `McpSchemaConverter`: map MCP OpenRPC tool schemas to `AimoToolDefinition` (JSON Schema Draft 2020-12)
  - Fail fast on unconvertible schemas with clear error naming tool and server
- [x] Apply `"{serverId}:{toolName}"` naming to all discovered tools
- [x] Enforce MCP server scope semantics: same rules as annotated `@ChatService` beans — `[]` = unrestricted (global), named list = restricted to those scopes only; no wildcards; fail fast on unknown scope names
- [x] Add scope validation and tool-refs validation across annotated + MCP tools
- [x] Implement `POST /aimo-api/admin/mcp-servers/refresh` endpoint
   - Triggers re-discovery via `tools/list` for all servers
   - Replaces cached tools in-place per server
   - Invalidates cached scope data
   - Returns per-server refresh results
- [x] Implement periodic discovery scheduler driven by `discovery-interval-minutes`

## Prompt (System Message) Discovery Tasks

 - [x] Implement `PromptDiscovery` class to discover MCP prompts
   - Call `prompts/list` RPC method to enumerate available prompts
   - Extract prompt name, description, and argument schema from each prompt
   - Convert MCP prompt schemas to AIMO system message context
- [x] Create `McpSystemMessageCallback` implementation
   - Implements `SystemMessageCallback` interface (name, scopes, call method)
   - Wraps MCP prompt discovery and `prompts/get` RPC calls
   - Call `prompts/get` with prompt name and (optionally) arguments to fetch prompt text
   - Scopes are set from server configuration (same as tools)
- [x] Update `McpClientManager` to cache prompts alongside tools
   - Add `cachedSystemMessages: List<SystemMessageCallback>` to `ServerConnection`
   - Discover prompts at startup via `PromptDiscovery`
   - Register handler for `prompts/listChanged` notification (if server supports it)
- [x] Update `PerServerMcpChatServiceProvider` to return system messages
   - Implement `getSystemMessages()` to return discovered prompts
   - Apply scope filtering (same as tools)
- [x] Apply `"{serverId}:{promptName}"` naming to all discovered prompts to avoid collisions
- [x] Update refresh logic to include prompts
   - `POST /aimo-api/admin/mcp-servers/refresh` re-discovers both tools and prompts
   - Replaces cached prompts in-place per server
   - Invalidates cached scope data that includes prompts

## Testing Tasks

- [x] Unit tests for JSON-RPC 2.0 messaging (request/response/notification, error handling)
- [x] Unit tests for MCP lifecycle (init/capabilities/terminate)
- [x] Unit tests for Stdio transport (process spawning, message framing, I/O error handling)
- [x] Unit tests for tool discovery, schema conversion, naming, and validation
- [x] Unit tests for all scope config semantics (empty = unrestricted, named = restricted, unknown = fail fast)
- [x] Unit tests for optional startup (aimo.mcp.required: false with unreachable servers)
- [x] Integration tests for startup discovery and refresh behavior (manual endpoint + scheduled scheduler)
- [x] Unit tests for prompt discovery, schema conversion, and naming
   - Test `prompts/list` RPC parsing and error handling
   - Test MCP prompt schema → system message callback conversion
   - Test `{serverId}:{promptName}` naming convention
- [x] Unit tests for `McpSystemMessageCallback` invocation
   - Test `prompts/get` RPC call with proper arguments
   - Test scope visibility and filtering for prompts
- [x] Unit tests for prompt refresh behavior
   - Test `prompts/listChanged` notification handling (if server supports it)
   - Test in-place replacement of cached prompts
   - Test scope cache invalidation when prompts change
- [x] Integration tests combining tool and prompt discovery
   - Test both tools and prompts discovered from same server
   - Test scope filtering across mixed tool/prompt scenarios
   - Test refresh with both tools and prompts
- [x] Documentation for MCP configuration, tool/prompt naming, scope assignment, refresh behavior, and troubleshooting

## Example Tasks

- [x] Add `aimo-mcp-client` dependency to `examples/simple-ollama/build.gradle.kts`
- [x] Add example MCP server configuration to `examples/simple-ollama/src/main/resources/application.yml`

## Specification Compliance Fixes (Priority 1)

- [x] Add `MCP-Protocol-Version` header to all HTTP requests
   - Per MCP 2025-11-25 spec section basic/transports#protocol-version-header
   - Added `protocolVersion` parameter to HttpTransport constructor
   - Header included on all POST and DELETE HTTP requests
   - Default version: `2025-11-25`
- [x] Fix shutdown implementation to use transport-level disconnect
   - Per MCP 2025-11-25 spec section basic/lifecycle#shutdown
   - Changed LifecycleManager.terminate() from RPC `close` notification to `protocolClient.disconnect()`
   - Properly closes transports:
     - Stdio: closes stdin/stdout, terminates subprocess with SIGTERM/SIGKILL if needed
     - HTTP: closes connection gracefully with DELETE for session cleanup
   - Improves spec compliance from 73% to 77%

