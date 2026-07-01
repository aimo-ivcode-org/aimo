## Why

The codebase needs a dedicated MCP client module that can discover tools from configured MCP servers, cache them, and expose them to the existing AIMO tool pipeline without adding MCP knowledge to core runtime code.

## What Changes

Create an `aimo-mcp-client` module that reads MCP server configuration from YAML, connects to each server, discovers tools, wraps them as existing AIMO callbacks, supports refresh via both manual and scheduled re-discovery, and documents how to configure and use the module.

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

- `mcp-client-module`

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

- `chat-scopes` (runtime tool availability continues to rely on scope filtering, now against MCP-backed tools too)

## Impact

Affected areas include the new MCP adapter module, Spring configuration, tool discovery/refresh behavior, scope filtering, example app wiring, module documentation, and tests for tool naming, validation, refresh, and doc coverage.
