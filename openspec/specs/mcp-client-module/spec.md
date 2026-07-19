# Mcp Client Module

## Purpose

Establish a client library for the Model Context Protocol (MCP) that discovers and exposes MCP server tools and prompts as AIMO chat scopes and callbacks, enabling integration of external AI tools with scope-based access control. The MCP client SHALL handle protocol communication, server initialization, tool and prompt discovery, and lifecycle management while respecting AIMO's two-level scope filtering (provider scope and individual callback scope).

## Requirements

### Requirement: MCP protocol layer implements JSON-RPC 2.0 messaging
The system SHALL implement a JSON-RPC 2.0 messaging layer that handles request/response/notification message types, generates unique message IDs, and validates protocol compliance.

#### Scenario: A JSON-RPC request is sent
- **WHEN** the protocol layer sends a request message
- **THEN** it assigns a unique message ID
- **AND** the message conforms to JSON-RPC 2.0 format
- **AND** it can correlate responses back to the original request

#### Scenario: A JSON-RPC response is received
- **WHEN** the protocol layer receives a response
- **THEN** it validates that the response ID matches a pending request
- **AND** it returns the result or error to the caller

### Requirement: MCP data layer manages initialization and capabilities negotiation
The system SHALL implement the MCP initialization flow (client init → server init → capabilities negotiation) and maintain session state.

#### Scenario: MCP session is initialized
- **WHEN** a connection is established to an MCP server
- **THEN** the client sends an initialize request with client info and protocol version
- **AND** it waits for the server's initialize response
- **AND** it validates server capabilities (tools, prompts support)
- **AND** session state is established for subsequent calls

#### Scenario: MCP session is terminated
- **WHEN** the client shuts down or the session is no longer needed
- **THEN** it performs transport-level disconnect
- **AND** any pending tool calls are rejected

### Requirement: Stdio transport handles process spawning and message framing
The system SHALL implement a stdio transport that spawns an MCP server process and communicates via newline-delimited JSON on stdin/stdout.

#### Scenario: Stdio transport connects to a server
- **WHEN** a server configuration specifies stdio with a command and arguments
- **THEN** the transport spawns the process
- **AND** the process's stdout is read as newline-delimited JSON messages
- **AND** messages are written to the process's stdin

#### Scenario: Stdio transport handles process termination
- **WHEN** the transport is closed or the process terminates unexpectedly
- **THEN** any pending requests are rejected
- **AND** the process is cleaned up (killed if still running)

### Requirement: MCP tool discovery converts OpenRPC schemas to JSON Schema Draft 2020-12
The system SHALL discover tools from an MCP server and convert their OpenRPC tool schema to JSON Schema Draft 2020-12 format compatible with AIMO's JSON schema validation.

#### Scenario: Tools are discovered from a server
- **WHEN** the MCP client initializes and the server supports tools
- **THEN** it calls the tools/list endpoint
- **AND** each tool's inputSchema is cached
- **AND** the tool list is available for wrapping as ToolCallback instances

#### Scenario: Tool schema is converted to JSON Schema Draft 2020-12
- **WHEN** a tool is wrapped as an AIMO ToolCallback
- **THEN** its OpenRPC schema is converted to JSON Schema Draft 2020-12
- **AND** the schema is used for parameter validation in the chat engine

### Requirement: MCP prompt discovery exposes server prompts as system message callbacks
The system SHALL discover prompts from an MCP server and expose them as SystemMessageCallback instances that can be included in chat scopes.

#### Scenario: Prompts are discovered from a server
- **WHEN** the MCP client initializes and the server supports prompts
- **THEN** it calls the prompts/list endpoint
- **AND** each prompt's metadata and arguments are cached
- **AND** prompts are available for wrapping as SystemMessageCallback instances

#### Scenario: A prompt is invoked as a system message
- **WHEN** a prompt is included in a chat scope and called
- **THEN** the prompt's arguments are passed to the MCP server
- **AND** the server's response text is returned as the system message content

### Requirement: MCP server configuration is read from YAML with optional startup bypass
The system SHALL read MCP server configurations from `aimo.mcp.servers.*` in YAML, validate them at startup, and support an optional `aimo.mcp.required: false` bypass flag.

#### Scenario: MCP servers are configured
- **WHEN** `aimo.mcp.servers` is populated in application.yml
- **THEN** each server configuration specifies a server ID, transport type, and transport-specific options
- **AND** the client attempts to connect at startup

#### Scenario: MCP startup is bypassed
- **WHEN** `aimo.mcp.required: false` is set
- **THEN** configuration validation errors do not fail startup
- **AND** unavailable servers are skipped with a warning
- **AND** available MCP tools and prompts are still discovered and registered

#### Scenario: MCP startup fails with required=true
- **WHEN** `aimo.mcp.required: true` (default) and a configured server is unreachable
- **THEN** startup fails with a clear error message
- **AND** the application does not start until the issue is resolved

### Requirement: Discovered tools are wrapped as AIMO ToolCallback instances with naming convention
The system SHALL wrap each discovered MCP tool as an existing AIMO `ToolCallback`, using the `"{serverId}:{toolName}"` naming convention, and register it with the ChatServiceProvider interface.

#### Scenario: An MCP tool is wrapped as ToolCallback
- **WHEN** a tool is discovered from an MCP server
- **THEN** it is wrapped as a ToolCallback with name `"{serverId}:{toolName}"`
- **AND** when invoked, the callback calls the MCP server's tools/call endpoint
- **AND** the server's result is returned to the chat engine

#### Scenario: Tool naming avoids collisions
- **WHEN** multiple MCP servers are configured or local annotated tools exist
- **THEN** the `{serverId}:` prefix ensures no name collisions
- **AND** scope filtering can select tools by their full qualified names

### Requirement: Discovered prompts are wrapped as AIMO SystemMessageCallback instances with naming convention
The system SHALL wrap each discovered MCP prompt as an existing AIMO `SystemMessageCallback`, using the `"{serverId}:{promptName}"` naming convention, and register it with the ChatServiceProvider interface.

#### Scenario: An MCP prompt is wrapped as SystemMessageCallback
- **WHEN** a prompt is discovered from an MCP server
- **THEN** it is wrapped as a SystemMessageCallback with name `"{serverId}:{promptName}"`
- **AND** when invoked, the callback calls the MCP server's prompts/get endpoint
- **AND** the server's response text is returned as the system message

#### Scenario: Prompt naming uses consistent convention
- **WHEN** prompts and tools are discovered from the same server
- **THEN** both use the `{serverId}:` naming convention for consistency
- **AND** configuration references can target tools or prompts by their qualified names

### Requirement: Refreshable MCP discovery / retry
The system SHALL support MCP connectivity retry via the `McpClientManager.refresh()` API for programmatic use and via periodic scheduled re-discovery driven by `aimo.mcp.discovery-interval-minutes`. Tool-set updates SHOULD be handled via MCP `tools/listChanged` notifications when supported by the server. **HTTP endpoint exposure for refresh is a server-layer concern and outside the scope of this client library.**

#### Scenario: Refresh is called programmatically
- **WHEN** `McpClientManager.refresh()` is invoked (by server layer or other client)
- **THEN** the module attempts to (re)initialize any failed/unreachable MCP servers
- **AND** returns a map listing each server and whether the refresh/retry succeeded

#### Scenario: Scheduled refresh runs
- **WHEN** the discovery scheduler executes (interval set by `aimo.mcp.discovery-interval-minutes`; disabled when set to `0`)
- **THEN** the module performs the same retry behavior as the programmatic refresh API

### Requirement: MCP tools and prompts are subject to two-level scope filtering
The system SHALL apply scope constraints when MCP-backed tools and prompts are included in chat scopes, respecting both the provider-level scope (the MCP server's own scope set) and individual callback scope constraints. Scope filtering is applied per the ChatServiceProvider abstraction: both the provider's scope set AND the callback's own scope set must allow the requested scope ID for inclusion.

#### Scenario: An MCP-backed tool is available in a chat scope
- **WHEN** a chat scope is built for scope ID "research"
- **AND** an MCP server is configured with `scopes: ["research"]`
- **AND** that server provides a tool named "search"
- **THEN** the tool is available in the "research" scope as `"{serverId}:search"`
- **AND** it is not available in other scopes like "admin" or "public"

#### Scenario: A chat scope combines annotated and MCP-backed tools
- **WHEN** a chat scope is built
- **THEN** it includes both annotated tools (from @ChatService beans) and MCP-backed tools (from configured servers)
- **AND** all tools are filtered by their scope constraints
- **AND** naming conventions (`"{serverId}:{toolName}"` for MCP, direct names for annotated) distinguish their sources

#### Scenario: An MCP-backed system message is available in a chat scope
- **WHEN** a chat scope is built for scope ID "admin"
- **AND** an MCP server is configured with `scopes: ["admin"]`
- **AND** that server provides a prompt named "code-review"
- **THEN** the prompt is available in the "admin" scope as `"{serverId}:code-review"`
- **AND** when invoked, it calls the MCP server's prompts/get endpoint

#### Scenario: A chat scope reflects real-time changes to MCP providers
- **WHEN** MCP tools or prompts are refreshed (manually or on schedule)
- **THEN** the next chat scope resolution includes the updated tools and prompts
- **AND** old cached callbacks are replaced atomically per server
- **AND** no restart is required to reflect the changes

#### Scenario: An MCP server with no scope restriction is global
- **WHEN** an MCP server is configured without a `scopes` field
- **THEN** its tools and prompts are available to all chat scopes
- **AND** they are gated only by each tool/prompt's individual scope constraint

#### Scenario: An MCP server restricted to specific scopes is filtered
- **WHEN** an MCP server specifies `scopes: ["admin", "research"]`
- **THEN** its tools and prompts are only included when building "admin" or "research" scopes
- **AND** requests for "public" scope exclude all tools/prompts from that server

#### Scenario: A global MCP tool within a scoped server is scoped
- **WHEN** an MCP server is configured with `scopes: ["research"]`
- **AND** a tool from that server has an empty individual scope (global within its server)
- **THEN** the tool is included only when building "research" scope
- **AND** it is not available in "admin" or "public" scopes

#### Scenario: Both provider and callback scope constraints must be satisfied
- **WHEN** an MCP server specifies `scopes: ["admin", "research"]`
- **AND** a tool from that server specifies its own `scope: ["admin"]`
- **AND** a scope request is made for "research"
- **THEN** the tool is not included
- **AND** the tool is only available when building "admin" scope (intersection of both constraints)

### Requirement: MCP provider configuration validates scope assignments
The system SHALL validate at startup that each MCP server's scope assignment (if any) is valid and consistent with existing scopes defined in configuration.

#### Scenario: An MCP server is scoped to a valid scope
- **WHEN** `aimo.mcp.servers[id].scopes: ["admin"]` matches an existing scope in configuration
- **THEN** validation passes
- **AND** the server's tools and prompts are registered under that scope constraint

#### Scenario: An MCP server is scoped to an undefined scope
- **WHEN** `aimo.mcp.servers[id].scopes: ["unknown"]` does not match any defined scope
- **THEN** validation fails at startup with a clear error message
- **AND** if `aimo.mcp.required: false`, the server is skipped with a warning

### Requirement: HTTP/SSE transport support is designed for future implementation
The system SHALL establish HTTP and SSE transport interfaces in the protocol layer, with an initial reference implementation using stdio (HTTP/SSE follows in Phase 2).

#### Scenario: Transport abstraction exists
- **WHEN** the protocol layer is implemented
- **THEN** transport logic is abstracted into pluggable interfaces
- **AND** stdio transport is the current implementation
- **AND** HTTP/SSE transport can be added later without protocol layer changes
