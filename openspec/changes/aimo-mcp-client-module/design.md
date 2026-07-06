# Design: aimo-mcp-client module

## Approach

The `aimo-mcp-client` module owns MCP-specific concerns end-to-end using direct protocol implementation:

### Protocol Layer (Reusable Foundation)

1. Implement JSON-RPC 2.0 messaging layer (request/response/notification)
2. Implement MCP data layer lifecycle (init/capabilities negotiation/termination)
3. Implement transport abstractions:
   - **Stdio transport** (MVP): Process spawning, stream-based message framing
   - **HTTP/SSE transport** (future): Network communication with optional bearer token auth
4. Tool discovery and schema conversion (MCP OpenRPC → JSON Schema Draft 2020-12)

### Client Integration Layer

1. Read `aimo.mcp.*` configuration from YAML with optional startup bypass flag
2. Create and manage one protocol client per configured server using the protocol layer
3. Discover tools at startup and cache results in each server instance
4. Wrap each discovered MCP tool as an existing `AimoToolCallback`, then apply server scopes
5. Expose MCP-backed tools through Spring so core framework consumes them as unified callback list
6. Support refresh: manual endpoint + scheduler trigger protocol re-discovery, replace tools in-place, invalidate scope cache

Tool naming uses the `"{serverId}:{toolName}"` convention to avoid collisions with annotated tools and between servers.

### Design Principles

- **Protocol isolation**: Transport and data layers are separate from client integration, enabling reuse for future server implementation
- **Fail-fast with bypass**: Configuration errors and unreachable servers fail at startup unless `aimo.mcp.required: false`
- **In-place refresh**: Tool cache replacement is atomic per server; scope cache invalidation ensures consistency

## Components Affected

`aimo-mcp-client` module structure:
- **Protocol layer**: MCP transport and data layer implementations (json-rpc, lifecycle, tool schemas)
- **Client layer**: Server connection management, discovery, tool wrapping, refresh logic
- Spring configuration, scope validation, cache invalidation, admin endpoint, module/user documentation.

## Trade-offs

- **Fail-fast validation** (with bypass): Keeps runtime behavior predictable and discovery errors visible at startup; `aimo.mcp.required: false` allows graceful degradation
- **In-place refresh**: Atomic cache replacement; simpler than maintaining old/new tool sets
- **Direct protocol implementation** over SDK: More control and foundation for server implementation; higher maintenance burden vs. SDK edge-case handling
