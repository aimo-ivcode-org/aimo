## Why

The codebase needs a dedicated MCP client module that implements the MCP protocol directly (not via SDK) to discover tools from configured MCP servers and expose them to the existing AIMO tool pipeline. Direct protocol implementation provides two key benefits:

1. **Foundation for server implementation**: A reusable protocol layer enables a future MCP server that exposes AIMO's own tools through MCP while maintaining full control over Spring/OpenAPI integration (SDKs cannot generate OpenAPI docs for dynamically registered MCP server capabilities).
2. **Consistency**: Both client and server use the same protocol implementation, reducing complexity and maintenance burden.

## What Changes

Create an `aimo-mcp-client` module that:
- Implements the MCP protocol directly (JSON-RPC 2.0 data layer + transport layer)
- Reads MCP server configuration from YAML with optional startup bypass
- Connects to each server (stdio transport first; SSE HTTP added later)
- Discovers tools, wraps them as existing AIMO callbacks, and caches results
- Supports refresh via both manual endpoint and scheduled re-discovery (in-place replacement)
- Establishes a reusable protocol foundation for future MCP server implementation

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

- `mcp-client-module`

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

- `chat-scopes` (runtime tool availability continues to rely on scope filtering, now against MCP-backed tools too)

## Impact

Affected areas include the new MCP adapter module, Spring configuration, tool discovery/refresh behavior, scope filtering, example app wiring, module documentation, and tests for tool naming, validation, refresh, and doc coverage.
