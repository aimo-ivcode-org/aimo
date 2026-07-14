## Why

The codebase needs a dedicated MCP client module that implements the MCP protocol directly (not via SDK) to discover tools and prompts from configured MCP servers and expose them to the existing AIMO callback pipeline. Direct protocol implementation provides two key benefits:

1. **Foundation for server implementation**: A reusable protocol layer enables a future MCP server that exposes AIMO's own tools and system messages through MCP while maintaining full control over Spring/OpenAPI integration (SDKs cannot generate OpenAPI docs for dynamically registered MCP server capabilities).
2. **Consistency**: Both client and server use the same protocol implementation, reducing complexity and maintenance burden. Tools and system messages are discovered and exposed through the same `ChatServiceProvider` abstraction.

## What Changes

Create an `aimo-mcp-client` module that:
- Implements the MCP protocol directly (JSON-RPC 2.0 data layer + transport layer)
- Reads MCP server configuration from YAML with optional startup bypass
- Connects to each server (stdio transport first; SSE HTTP added later)
- Discovers tools, wraps them as existing AIMO `ToolCallback` instances, and caches results
- Discovers prompts, wraps them as existing AIMO `SystemMessageCallback` instances, and caches results
- Supports refresh via both manual endpoint and scheduled re-discovery (in-place replacement)
- Establishes a reusable protocol foundation for future MCP server implementation
- Exposes unified tools and system messages through the `ChatServiceProvider` interface for scope-aware filtering

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

- `mcp-client-module`

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

- `chat-scopes` (runtime tool and system message availability now rely on scope filtering against MCP-backed tools and prompts)

## Impact

Affected areas include the new MCP adapter module, Spring configuration, tool/prompt discovery and refresh behavior, scope filtering, `ChatServiceProvider` interface usage in example app wiring, module documentation, and tests for tool/prompt naming, validation, refresh, and doc coverage.
