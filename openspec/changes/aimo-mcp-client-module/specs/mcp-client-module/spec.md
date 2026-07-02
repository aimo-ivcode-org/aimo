# Capability: mcp-client-module

## Purpose

Provide a dedicated MCP client module that discovers tools from configured MCP servers, exposes them through the existing AIMO callback model, and keeps discovery/refresh behavior isolated from core runtime code.

## ADDED Requirements

### Requirement: YAML-configured MCP servers
The system SHALL configure MCP servers from `aimo.mcp.*` YAML properties and SHALL fail fast when the configuration is invalid or a configured server cannot be reached at startup.

The configuration schema is:

```yaml
aimo:
  mcp:
    enabled: true                          # defaults to true; set false to disable the module entirely
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

#### Scenario: Server configuration is present
- **WHEN** the application starts with valid MCP server definitions
- **THEN** the module creates one persistent client connection per configured server
- **AND** makes each server available for tool discovery

#### Scenario: Configuration is invalid
- **WHEN** a server has an invalid transport type, missing required fields, or an unsupported scope reference
- **THEN** startup fails with a clear configuration error before the application serves any requests

#### Scenario: Configured server is unreachable at startup
- **WHEN** a server's transport is valid but the server cannot be contacted during startup discovery
- **THEN** startup fails with a clear error naming the unreachable server

### Requirement: MCP tool discovery and caching
The system SHALL discover tools from each configured MCP server at startup and SHALL cache the discovered tools in memory for reuse.

#### Scenario: Startup discovery succeeds
- **WHEN** an MCP server advertises tools during discovery
- **THEN** the module wraps each tool as an existing AIMO tool callback
- **AND** stores the results in the server's cache

#### Scenario: Tool set changes after startup
- **WHEN** a server later adds or removes tools
- **THEN** the cached tool set remains unchanged until a refresh is triggered

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

### Requirement: Refreshable MCP discovery
The system SHALL support manual and periodic MCP tool re-discovery and SHALL invalidate any cached scope state when discovered tools change.

#### Scenario: Admin refresh is invoked
- **WHEN** an HTTP `POST` request is made to `/aimo-api/admin/mcp-servers/refresh`
- **THEN** the module re-discovers tools from all configured MCP servers
- **AND** updates each server cache in place
- **AND** invalidates cached scope data so the next request sees the new tool set
- **AND** returns a response listing each server and whether its refresh succeeded

#### Scenario: Scheduled refresh runs
- **WHEN** the discovery scheduler executes (interval set by `aimo.mcp.discovery-interval-minutes`; disabled when set to `0`)
- **THEN** the module re-discovers tools from configured servers using the same refresh path as the manual endpoint

### Requirement: MCP server scope configuration semantics
The system SHALL apply the same scope rules to MCP servers that apply to annotated `@ChatService` beans. There are no wildcards; every entry in `scope` is treated as a literal scope name.

| `scope` value | Meaning |
|---|---|
| `[]` (empty) | No restriction — tools are available in the built-in global scope and unrestricted contexts |
| `["research", "admin"]` | Tools are available only in the named scopes listed; they are excluded from the built-in global scope |
| `["global"]` | Tools are available only in a named scope called `"global"`, not in the built-in global scope |

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

