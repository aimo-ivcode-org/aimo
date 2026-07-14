# Design: aimo-mcp-client module

## Approach

The `aimo-mcp-client` module owns MCP-specific concerns end-to-end using direct protocol implementation:

### Protocol Layer (Reusable Foundation)

1. Implement JSON-RPC 2.0 messaging layer (request/response/notification)
2. Implement MCP data layer lifecycle (init/capabilities negotiation/termination)
3. Implement transport abstractions:
   - **Stdio transport** (MVP): Process spawning, stream-based message framing
   - **HTTP/SSE transport** (future): Network communication with optional bearer token auth
4. Tool and prompt discovery and schema conversion (MCP OpenRPC tools → JSON Schema Draft 2020-12; MCP prompts → system message callbacks)

### Client Integration Layer

1. Read `aimo.mcp.*` configuration from YAML with optional startup bypass flag
2. Create and manage one protocol client per configured server using the protocol layer
3. Discover tools and prompts at startup and cache results in each server instance
4. Wrap each discovered MCP tool as an existing `AimoToolCallback`, then apply server scopes
5. Wrap each discovered MCP prompt as an existing `SystemMessageCallback`, then apply server scopes
6. Expose MCP-backed tools and system messages through Spring so core framework consumes them as unified callback lists
7. Support refresh: manual endpoint + scheduler trigger protocol re-discovery, replace tools/messages in-place, invalidate scope cache

Tool naming uses the `"{serverId}:{toolName}"` convention to avoid collisions with annotated tools and between servers.
Prompt naming uses the `"{serverId}:{promptName}"` convention for consistency.

### Design Principles

- **Protocol isolation**: Transport and data layers are separate from client integration, enabling reuse for future server implementation
- **Unified callback model**: Tools and prompts are discovered separately but exposed through the same `ChatServiceProvider` interface (tools → `ToolCallback`, prompts → `SystemMessageCallback`)
- **Consistent naming**: Both tools and prompts use the `{serverId}:{name}` convention for scoped visibility and to avoid collisions
- **Fail-fast with bypass**: Configuration errors and unreachable servers fail at startup unless `aimo.mcp.required: false`
- **In-place refresh**: Tool and prompt cache replacement is atomic per server; scope cache invalidation ensures consistency

## Components Affected

`aimo-mcp-client` module structure:
- **Protocol layer**: MCP transport and data layer implementations (json-rpc, lifecycle, tool/prompt schemas)
- **Client layer**: Server connection management, discovery (tools + prompts), callback wrapping, refresh logic
- Spring configuration, scope validation, cache invalidation, admin endpoint, module/user documentation.

## Trade-offs

- **Fail-fast validation** (with bypass): Keeps runtime behavior predictable and discovery errors visible at startup; `aimo.mcp.required: false` allows graceful degradation
- **In-place refresh**: Atomic cache replacement; simpler than maintaining old/new tool/message sets
- **Direct protocol implementation** over SDK: More control and foundation for server implementation; higher maintenance burden vs. SDK edge-case handling
- **Prompt naming convention**: `{serverId}:{promptName}` mirrors tool naming for consistency; requires that MCP prompt names don't conflict within a server

## Specification Compliance

The implementation targets the official MCP 2025-11-25 specification. Compliance verification and fixes:

- **Status**: 77% compliant (40/52 checks passing)
- **Core Protocol**: 100% - JSON-RPC 2.0, lifecycle, version negotiation
- **Transports**: 100% - Stdio and HTTP/SSE with proper headers and session management
- **Server Features**: 100% - Tools and prompts discovery, invocation, and notifications
- **Gaps**: Resources (0%), Client features (0%) - prioritized for Phase 2+
- **Priority 1 Fixes** (July 2026):
  - Added `MCP-Protocol-Version` HTTP header per spec section basic/transports#protocol-version-header
  - Fixed shutdown to use transport-level disconnect per spec section basic/lifecycle#shutdown
- **Documentation**: See `aimo-mcp-client/SPECIFICATION-COMPLIANCE-REPORT.md` for detailed compliance analysis


