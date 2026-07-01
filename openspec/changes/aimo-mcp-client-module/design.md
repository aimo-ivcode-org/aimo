# Design: aimo-mcp-client module

## Approach

The `aimo-mcp-client` module owns MCP-specific concerns end-to-end:

1. Read `aimo.mcp.*` configuration from YAML.
2. Create and manage one MCP client per configured server.
3. Discover tools at startup and cache the results in each server instance.
4. Wrap each discovered MCP tool as an existing `AimoToolCallback`, then apply server scopes through `ScopedToolCallback`.
5. Expose MCP-backed tools through Spring so the core framework consumes them as part of the same unified callback list as annotated tools.
6. Support manual and periodic refresh so newly added or removed MCP tools are picked up without restarting the application.

Tool naming uses the `"{serverId}:{toolName}"` convention to avoid collisions with annotated tools and between servers.

## Components Affected

 `aimo-mcp-client` module, Spring configuration, MCP client manager, tool discovery and schema conversion, refresh scheduler, admin refresh endpoint, scope validation / cache invalidation behavior, and module/user documentation.

## Trade-offs

The design favors fail-fast validation and in-memory caching over lazy connection handling. That keeps runtime behavior predictable and makes discovery errors visible at startup, at the cost of requiring healthy MCP servers during application boot.
