# Capability: mcp-client-module

## Purpose

Provide a dedicated MCP client module that implements the MCP protocol directly, discovers tools from configured MCP servers, exposes them through the existing AIMO callback model, and establishes a reusable protocol foundation for future MCP server implementation.

## ADDED Requirements

### Requirement: YAML-configured MCP servers with optional startup bypass
The system SHALL configure MCP servers from `aimo.mcp.*` YAML properties. The system SHALL fail fast when configuration is invalid or a configured server cannot be reached at startup, UNLESS `aimo.mcp.required` is set to `false` (permitting startup bypass).

The configuration schema is:

```yaml
aimo:
  mcp:
    enabled: true                          # defaults to true; set false to disable module entirely
    required: true                         # defaults to true; set false to allow startup if servers unreachable
    discovery-interval-minutes: 5          # 0 = disable scheduled re-discovery
    servers:
      - id: my-stdio-server                # unique server identifier (used in tool name prefix)
        transport:
          type: stdio
          command: "/path/to/mcp-server"
          args: ["--config", "file.json"]  # optional
        scope: ["research", "admin"]       # scope rules — same semantics as @ChatService
      
      - id: my-sse-server
        transport:
          type: sse
          url: "http://example.com/mcp"
          auth-token: "${MCP_TOKEN}"       # optional; supports property placeholders
        scope: []                          # empty = unrestricted (available in global scope)
```

Each server entry requires `id`, `transport.type`, and the transport-specific fields (`command` for `stdio`; `url` for `sse`). `auth-token` is optional for `sse`. `scope` is optional and defaults to `[]` (unrestricted).

#### Scenario: Server configuration is present and required
- **WHEN** the application starts with valid MCP server definitions and `aimo.mcp.required: true` (or unset)
- **THEN** the module creates one persistent client connection per configured server
- **AND** makes each server available for tool discovery

#### Scenario: Server configuration is present but not required
- **WHEN** the application starts with `aimo.mcp.required: false` and a configured server is unreachable
- **THEN** startup succeeds; the unreachable server is logged as unavailable
- **AND** other servers (if reachable) are connected normally

#### Scenario: Configuration is invalid
- **WHEN** a server has an invalid transport type, missing required fields, or an unsupported scope reference
- **THEN** startup fails with a clear configuration error before the application serves any requests

#### Scenario: Configured server is unreachable at startup and required
- **WHEN** `aimo.mcp.required: true` and a server's transport is valid but the server cannot be contacted during startup discovery
- **THEN** startup fails with a clear error naming the unreachable server

### Requirement: MCP tool discovery and caching
The system SHALL discover tools from each configured MCP server at startup and SHALL cache the discovered tools in memory for reuse. Tool discovery uses the MCP `tools/list` RPC method.

#### Scenario: Startup discovery succeeds
- **WHEN** an MCP server advertises tools during discovery
- **THEN** the module wraps each tool as an existing AIMO tool callback
- **AND** stores the results in the server's cache

#### Scenario: Tool set changes after startup
- **WHEN** a server later adds or removes tools
- **THEN** the cached tool set remains unchanged until a refresh is triggered

#### Scenario: Discovery fails on unreachable server (required)
- **WHEN** discovery is attempted and the server is unreachable and `aimo.mcp.required: true`
- **THEN** startup fails with a clear error

#### Scenario: Discovery fails on unreachable server (optional)
- **WHEN** discovery is attempted and the server is unreachable and `aimo.mcp.required: false`
- **THEN** startup succeeds; the server is logged as unavailable and tools are not registered

### Requirement: MCP tool invocation
The system SHALL invoke discovered MCP tools via the MCP `tools/call` RPC method when the tool callback is executed by AIMO. The tool name, input arguments (as JSON), and any runtime context shall be marshalled into the RPC call, and the result shall be returned as a string to the AIMO callback contract.

#### Scenario: Tool execution succeeds
- **WHEN** an AIMO tool callback wrapping an MCP tool is invoked with JSON arguments
- **THEN** the module calls the MCP server's `tools/call` RPC method with the tool name and arguments
- **AND** returns the result as a string to the caller

#### Scenario: Tool execution fails on the MCP server
- **WHEN** a tool call fails on the MCP server (invalid arguments, execution error, etc.)
- **THEN** the module returns the error message/details as a string to the caller
- **AND** logs the failure with context (server, tool name, arguments)

#### Scenario: Tool execution times out
- **WHEN** a tool call does not complete within a reasonable timeout
- **THEN** the module returns a timeout error to the caller
- **AND** may attempt to gracefully close/reconnect the server connection

### Requirement: MCP schema conversion to AIMO tool definitions
The system SHALL convert MCP tool schemas (OpenRPC-style) into AIMO `AimoToolDefinition` instances using JSON Schema Draft 2020-12.

#### Scenario: A well-formed MCP tool is discovered
- **WHEN** an MCP server advertises a tool with a name, description, and input schema
- **THEN** the module maps the tool name (namespaced), description, and input schema fields into an `AimoToolDefinition`
- **AND** required parameter flags and parameter descriptions are preserved from the MCP schema

#### Scenario: An MCP tool has an unrecognisable input schema
- **WHEN** the schema cannot be converted to a valid JSON Schema Draft 2020-12 document
- **THEN** the module fails fast at startup with a clear error naming the tool and server

### Requirement: Namespaced MCP tool names
The system SHALL expose MCP tools using the `"{serverId}:{toolName}"` naming convention.

#### Scenario: Same tool name exists on multiple servers
- **WHEN** two MCP servers expose a tool with the same local name
- **THEN** the namespaced names remain unique
- **AND** the tools can coexist in the unified callback list

### Requirement: Tool-ref validation across annotated and MCP tools
The system SHALL validate tool references against the combined set of annotated tools and MCP tools.

#### Scenario: Tool reference points to an MCP tool
- **WHEN** a scope references `server-a:search`
- **THEN** validation succeeds if that namespaced tool exists

#### Scenario: Tool reference is unknown
- **WHEN** a scope references a tool that does not exist in either source
- **THEN** validation fails before the application serves chat requests

### Requirement: Refreshable MCP discovery / retry
The system SHALL support manual and periodic MCP connectivity retry via the `McpClientManager.refresh()` API. Tool-set updates SHOULD be handled via MCP `tools/listChanged` notifications when supported by the server. HTTP endpoint exposure for refresh is a server-layer concern and outside the scope of this client library.

#### Scenario: Refresh is called programmatically
- **WHEN** `McpClientManager.refresh()` is invoked (by server layer or other client)
- **THEN** the module attempts to (re)initialize any failed/unreachable MCP servers
- **AND** returns a map listing each server and whether the refresh/retry succeeded

#### Scenario: Scheduled refresh runs
- **WHEN** the discovery scheduler executes (interval set by `aimo.mcp.discovery-interval-minutes`; disabled when set to `0`)
- **THEN** the module performs the same retry behavior as the programmatic refresh API

### Requirement: MCP server scope configuration semantics
The system SHALL apply the same scope rules to MCP servers that apply to annotated `@ChatService` beans. There are no wildcards; every entry in `scope` is treated as a literal scope name.

| `scope` value | Meaning |
|---|---|
| `[]` (empty) | No restriction — tools are available in the built-in global scope and unrestricted contexts |
| `["research", "admin"]` | Tools are available only in the named scopes listed; they are excluded from the built-in global scope |
#### Scenario: Empty scope list is configured
- **WHEN** a server is configured with `scope: []`
- **THEN** its tools are treated as unrestricted and appear in the built-in global scope

#### Scenario: Named scopes are configured
- **WHEN** a server is configured with `scope: ["research", "admin"]`
- **THEN** its tools appear only when the conversation is using the `"research"` or `"admin"` scope
- **AND** its tools are excluded from the built-in global scope

#### Scenario: Unknown scope name is configured
- **WHEN** a server's `scope` list contains a name that does not match any defined scope
- **THEN** startup fails with a clear error naming the unknown scope

### Requirement: Scope-aware exposure of MCP tools
The system SHALL expose MCP tools through the same scope filtering rules as annotated tools, using both provider-level scopes and callback-level scopes.

#### Scenario: Scope is allowed
- **WHEN** a chat scope is built for a scope allowed by the server and tool
- **THEN** the MCP tool is included in the resulting scope

#### Scenario: Scope is not allowed
- **WHEN** a chat scope is built for a scope not allowed by the server or tool
- **THEN** the MCP tool is excluded from the resulting scope

### Requirement: MCP module documentation is included
The system SHALL document MCP configuration, tool naming, scope assignment, refresh behavior, and troubleshooting as part of the MCP module change.

#### Scenario: A developer reads the module docs
- **WHEN** a developer looks at the MCP module documentation
- **THEN** they can learn how to configure servers, reference tools, understand scope visibility, and refresh tool discovery

