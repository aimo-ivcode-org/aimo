# MCP Client Module (`aimo-mcp-client`)

The MCP client module discovers tools from configured Model Context Protocol (MCP) servers and exposes them through AIMO's existing tool callback system. This module implements the MCP protocol directly to establish a reusable foundation for both client and future server implementations.

## Features

- **Direct Protocol Implementation**: Implements JSON-RPC 2.0 messaging and MCP lifecycle management without external SDKs
- **Stdio Transport (MVP)**: Spawn and communicate with local MCP servers via standard input/output
- **Tool Discovery**: Discover tools from configured MCP servers at startup and cache them in memory
- **Tool Invocation**: Execute MCP tools via the `tools/call` RPC method with proper error handling
- **Scoped Availability**: Apply AIMO scope rules to MCP tools—restrict visibility by conversation scope
- **Refresh Capability**: Manually refresh or periodically re-discover tools without stopping the application
- **Graceful Degradation**: Optional startup bypass allows applications to start without MCP servers if configured

## Configuration

MCP servers are configured under `aimo.mcp` in `application.yml`:

```yaml
aimo:
  mcp:
    enabled: true                                 # Enable/disable MCP module
    required: true                                # Fail startup if servers unreachable (false = optional)
    discovery-interval-minutes: 5                 # Periodic refresh interval (0 = disabled)
    servers:
      - id: my-stdio-server
        transport:
          type: stdio
          command: /path/to/mcp-server            # Executable path (required for stdio)
          args: ["--config", "file.json"]         # Optional arguments
        scope: []                                 # Empty = available in global scope
      
      - id: my-http-server
        transport:
          type: http                              # HTTP with Server-Sent Events
          url: https://example.com/mcp/           # URL (required for http)
          auth-token: ${MCP_TOKEN}                # Optional; supports property placeholders
        scope: [research, admin]                  # Restricted to named scopes
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `aimo.mcp.enabled` | boolean | `true` | Enable/disable MCP client module |
| `aimo.mcp.required` | boolean | `true` | Fail startup if servers unreachable; `false` allows graceful degradation |
| `aimo.mcp.discovery-interval-minutes` | integer | `5` | Interval for periodic tool re-discovery in minutes; `0` disables scheduler |
| `aimo.mcp.servers[].id` | string | — | Unique server identifier (used in tool name prefix) |
| `aimo.mcp.servers[].transport.type` | enum | — | `stdio` or `http` |
| `aimo.mcp.servers[].transport.command` | string | — | Executable path (required for `stdio`) |
| `aimo.mcp.servers[].transport.args` | list | `[]` | Optional command arguments |
| `aimo.mcp.servers[].transport.url` | string | — | Server URL (required for `http`) |
| `aimo.mcp.servers[].transport.auth-token` | string | — | Optional bearer token (supports property placeholders) |
| `aimo.mcp.servers[].scope` | list | `[]` | Scope list; empty = unrestricted (global scope) |

## Tool Naming

MCP tools are named using the `"{serverId}:{toolName}"` convention to avoid collisions between servers and with annotated tools:

- Local tool: `search` → `/aimo-api/chat/{chatId}` sees `search` (from `@Tool` annotation)
- MCP tool from `my-search-server`: `search` → `/aimo-api/chat/{chatId}` sees `my-search-server:search`

## Scope Visibility Rules

Scopes control which tools are available in a conversation:

| Server Scope | Visibility |
|---|---|
| `scope: []` (empty) | Available in the **global scope** and all unrestricted contexts |
| `scope: [research, admin]` | Available **only** in the `research` and `admin` scopes; **excluded** from global scope |
| `scope: [global]` | Available **only** in a scope named `"global"` (if defined); **excluded** from the built-in global scope |

## Prompt (System Message) Discovery

MCP servers can expose prompts (templates) that become AIMO system messages. Prompts are discovered using the `prompts/list` RPC and included in chat requests to provide context and instructions to the LLM.

### Prompt Naming

MCP prompts are named using the `"{serverId}:{promptName}"` convention to avoid collisions:

- Local system message: `security-policy` → `/aimo-api/chat/{chatId}` sees `security-policy` (from `@SystemMessage` annotation)
- MCP prompt from `my-server`: `research-guidelines` → `/aimo-api/chat/{chatId}` sees `my-server:research-guidelines`

### Prompt Scope Visibility

Prompts follow the same scope rules as tools:

| Server Scope | Visibility |
|---|---|
| `scope: []` (empty) | Available in the **global scope** and all unrestricted contexts |
| `scope: [research, admin]` | Available **only** in the `research` and `admin` scopes |

### Example: Prompts with Scope Configuration

```yaml
aimo:
  scope:
    research:
      system-message-refs: 
        - research-guidelines     # Annotated system message
        - research-server:citation-instructions  # MCP prompt
    admin:
      system-message-refs:
        - admin-policy
        - admin-server:admin-guidelines  # MCP prompt

  mcp:
    servers:
      - id: research-server
        transport:
          type: stdio
          command: /path/to/research-mcp
        scope: [research]  # Prompts visible only in research scope
      
      - id: admin-server
        transport:
          type: stdio
          command: /path/to/admin-mcp
        scope: [admin]     # Prompts visible only in admin scope
```

### Prompt Invocation

When a chat request is made in a specific scope:
1. All prompts with matching scope are discovered
2. Each prompt is fetched via `prompts/get` RPC (with optional arguments)
3. Prompts are prepended to the chat message sequence as system messages
4. LLM uses prompts as context for generating responses

### Example: Prompt Arguments

If a prompt accepts arguments, pass them via context:

```kotlin
val context = SystemMessageContext(
    context = mapOf(
        "research-server:dataset-summary:args" to mapOf(
            "dataset_name" to "my_dataset",
            "rows" to 1000
        )
    ),
    chatScopeId = "research"
)
```

## Scope Visibility Rules

Scopes control which tools and prompts are available in a conversation.

### Example: Tool and Prompt Scope Configuration

```yaml
aimo:
  scope:
    research:
      tool-refs: [research-tool, server-a:research-search]    # Both annotated and MCP tools
      system-message-refs:
        - research-guidelines
        - server-a:citation-instructions                       # MCP prompt
    admin:
      tool-refs: [admin-tool, server-b:admin-action]
      system-message-refs:
        - admin-policy
        - server-b:admin-guidelines                            # MCP prompt

  mcp:
    servers:
      - id: server-a
        transport:
          type: stdio
          command: /path/to/research-server
        scope: [research]                         # Visible only in research scope
      
      - id: server-b
        transport:
          type: stdio
          command: /path/to/admin-server
        scope: [admin]                            # Visible only in admin scope
```

## Refresh Behavior

### Manual Refresh

Trigger a refresh of all MCP servers via HTTP:

```bash
POST /aimo-api/admin/mcp-servers/refresh
```

Response:

```json
{
  "servers": [
    {
      "id": "my-server",
      "success": true,
      "toolCount": 5,
      "message": "Successfully refreshed"
    }
  ]
}
```

### Scheduled Refresh

When `aimo.mcp.discovery-interval-minutes > 0`, the module automatically refreshes tools on the specified interval. For example, with `discovery-interval-minutes: 5`, tools are re-discovered every 5 minutes.

Set to `0` to disable scheduled refresh (tools are discovered only at startup).

## Startup Behavior

### Required Servers (Default)

With `aimo.mcp.required: true` (default), the application fails at startup if:
- A configured server's transport is invalid (missing required fields)
- A configured server cannot be reached during discovery
- A server references an unknown scope name

### Optional Servers

With `aimo.mcp.required: false`, the application continues startup even if:
- Configured servers cannot be reached
- Discovery fails for individual servers

Unreachable servers are logged as unavailable, and their tools are not registered. Reachable servers continue to function normally.

## Troubleshooting

### Server Configuration Errors

Check application logs for messages like:

```
Server '<id>' references unknown scope '<scope>'
Stdio command cannot be blank
SSE URL cannot be blank
```

Verify your YAML configuration is correct and matches the schema above.

### Unreachable Server at Startup

If `aimo.mcp.required: true`:

```
Startup failed: MCP server '<id>' unreachable
```

Check that:
- The server executable path exists and is correct
- The server is running (for remote servers)
- Network connectivity exists (for SSE servers)

If the server is not essential, set `aimo.mcp.required: false` to allow startup without it.

### Tools Not Appearing

1. Verify the server is configured and reachable
2. Check the server's scope matches the conversation scope
3. Verify server logs for tool discovery errors
4. Manually refresh via `/aimo-api/admin/mcp-servers/refresh` to check discovery status

## Architecture

The MCP client module is organized in layers:

### Protocol Layer

- **JSON-RPC 2.0 Messaging**: Request/response/notification types with proper ID tracking
- **MCP Lifecycle Management**: Initialize, capabilities negotiation, and connection termination
- **Transport Abstraction**: Pluggable transport implementations
  - **Stdio** (stable): Local process spawning with JSON Lines framing
  - **HTTP/SSE** (experimental): Remote HTTP servers with Server-Sent Events streaming
- **Tool Schema Conversion**: MCP OpenRPC tool schemas → AIMO `AimoToolDefinition` (JSON Schema Draft 2020-12)

### Client Integration Layer

- **Server Connection Management**: One persistent protocol client per configured server
- **Startup Tool Discovery**: Discover tools at startup and cache results
- **Tool Wrapping**: Convert discovered tools to AIMO `AimoToolCallback` instances
- **Scope-Aware Exposure**: Apply scope rules to MCP tools, same as annotated tools
- **Refresh Logic**: Manual endpoint and periodic scheduler for re-discovery with in-place replacement

### Spring Integration

- **Auto-Configuration**: Automatic startup and shutdown
- **Tool Registry**: Provides discovered tools to AIMO core framework
- **Admin Endpoint**: Exposes refresh capability at `/aimo-api/admin/mcp-servers/refresh`
- **Configuration Properties**: Spring-managed `aimo.mcp.*` configuration

## Implementation Details

### Protocol Version

The module uses MCP protocol version `"2024-11-05"`.

### Request/Response Pairing

Requests are tracked by unique ID using `ConcurrentHashMap`, with a configurable timeout (default 60 seconds) for responses. Responses are matched to requests and delivered to waiting callers.

### Message Framing

The stdio transport uses JSONL (JSON Lines) framing: one JSON object per line. This enables streaming and proper message demarcation.

### Tool Invocation Timeout

Tool invocation calls (via `tools/call` RPC) have a reasonable timeout. If a tool call does not complete within the timeout window, the error is returned to the caller and may trigger connection recovery.

### Prompt (System Message) Discovery

MCP prompts are discovered using the `prompts/list` RPC method and converted to AIMO `SystemMessageCallback` instances. Each prompt:

- Gets a namespaced name: `{serverId}:{promptName}` to avoid collisions
- Inherits the server's scope restrictions (empty `scope: []` = global, named scopes = restricted)
- Is invoked via `prompts/get` RPC when needed in a chat request
- Can accept optional arguments passed through the context map

Prompts are cached per server and updated via `prompts/listChanged` notifications (if supported by the server).

### Scope Cache Invalidation

When tools or prompts are refreshed, the scope cache is invalidated to ensure the next request sees the updated tool/prompt set. Scope cache is rebuilt on-demand.

## MCP Specification Compliance Matrix

This section documents which features from the [MCP 2025-11-25 Specification](MCP-SPECIFICATION-2025-11-25-COMPLETE.md) are implemented, partial, or not yet implemented.

### Core Protocol

| Feature | Status | Notes |
|---------|--------|-------|
| **Transports** | | |
| Stdio Transport | ✅ Implemented | Full support for spawning local MCP servers via JSON Lines |
| HTTP/Streamable Transport | ⚠️ Partial | Implemented but may have edge cases; HTTP error handling needs improvement |
| Custom Transports | ❌ Not Implemented | Extensible framework not yet provided |
| **Lifecycle** | | |
| Initialization Phase | ✅ Implemented | Full capability negotiation and version handling |
| Operation Phase | ✅ Implemented | Normal request/response routing |
| Shutdown Phase | ✅ Implemented | Graceful process termination for stdio; connection closure for HTTP |
| **Capability Negotiation** | ✅ Implemented | Client declares supported capabilities; server capabilities negotiated |
| **JSON-RPC 2.0** | ✅ Implemented | Proper request/response/notification handling with ID tracking |

### Server Features (Client receives these)

| Feature | Status | Notes |
|---------|--------|-------|
| **Tools** | | |
| tools/list | ✅ Implemented | Full discovery with pagination support |
| tools/call | ✅ Implemented | Tool invocation with proper timeout handling |
| tools/listChanged Notification | ✅ Implemented | Server list updates trigger cache invalidation |
| **Resources** | | |
| resources/list | ❌ Not Implemented | Discovery not yet wired to tool registry |
| resources/read | ❌ Not Implemented | Content fetching not implemented |
| resources/templates/list | ❌ Not Implemented | Parameterized resource templates not supported |
| resources/subscribe | ❌ Not Implemented | Resource subscriptions not implemented |
| resources/listChanged Notification | ❌ Not Implemented | No resource change notification handling |
| resources/updated Notification | ❌ Not Implemented | No resource update notification handling |
| **Prompts** | | |
| prompts/list | ✅ Implemented | Full discovery with pagination support |
| prompts/get | ✅ Implemented | Prompts fetched and converted to SystemMessageCallback |
| prompts/listChanged Notification | ✅ Implemented | Server list updates trigger cache invalidation |

### Client Features (Server can request these)

| Feature | Status | Notes |
|---------|--------|-------|
| **Roots** | ❌ Not Implemented | Filesystem boundary exposure not yet supported |
| **Sampling** | ❌ Not Implemented | Server-initiated LLM interactions not supported |
| **Elicitation** | ❌ Not Implemented | Server-initiated user information requests not supported |

### Utilities & Advanced Features

| Feature | Status | Notes |
|---------|--------|-------|
| **Pagination** | ✅ Implemented | Cursor-based pagination for list operations (tools, prompts) |
| **Progress Tracking** | ❌ Not Implemented | Progress notifications not handled |
| **Cancellation** | ❌ Not Implemented | Request cancellation not yet supported |
| **Logging** | ❌ Not Implemented | Server logging not captured/displayed |
| **Completion** | ❌ Not Implemented | Argument autocompletion not implemented |
| **Tasks** | ❌ Not Implemented | Task-augmented operations not supported |

### Integration Features

| Feature | Status | Notes |
|---------|--------|-------|
| **Scope-Aware Tool Exposure** | ✅ Implemented | MCP tools respect AIMO chat scopes (same as annotated tools) |
| **Tool Naming Convention** | ✅ Implemented | `{serverId}:{toolName}` prevents collisions |
| **Prompt Naming Convention** | ✅ Implemented | `{serverId}:{promptName}` prevents collisions |
| **Scope Cache Invalidation** | ✅ Implemented | Cache refreshed when tools/prompts change |
| **Optional Server Startup** | ✅ Implemented | `aimo.mcp.required: false` allows graceful degradation |
| **Manual Refresh Endpoint** | ✅ Implemented | `/aimo-api/admin/mcp-servers/refresh` allows on-demand discovery |
| **Periodic Auto-Refresh** | ✅ Implemented | Configurable `discovery-interval-minutes` for automatic rediscovery |

### Summary

- **Implemented**: 16 features
- **Partial**: 1 feature
- **Not Implemented**: 20 features

The module currently focuses on the **core client integration pattern**: discovering and invoking MCP tools, and consuming MCP prompts as system messages. This covers the primary use case of extending AIMO's capabilities with MCP servers. Resources, roots, sampling, and advanced utilities remain for future phases.

## Future Work

- **HTTP/SSE Transport**: Improve edge case handling and error reporting for HTTP transport stability
- **Resource Support**: Implement resource discovery, reading, templates, and subscriptions to expose MCP resource data
- **Client Capabilities**: Add support for roots, sampling, and elicitation to enable server-initiated interactions
- **Advanced Utilities**: Support progress tracking, cancellation, logging, and task-augmented operations
- **Server Implementation**: Build MCP server support using the same protocol layer for symmetric implementation

## HTTP/SSE Transport Status (Experimental)

The HTTP/SSE transport is currently implemented but requires proper server configuration to function. This section documents known limitations and troubleshooting.

### Supported Servers

- **Stdio**: ✅ Stable - Local process spawning with JSON Lines framing
- **HTTP/SSE**: ⚠️ Experimental - Remote servers using HTTP with Server-Sent Events

### HTTP Transport Known Issues

1. **SSE Parsing**: The transport now properly filters SSE metadata lines (`event:`, `id:`, `retry:`, comments) before JSON deserialization, resolving "Unrecognized token" errors.

2. **Message Sending**: Some HTTP/SSE servers may not accept POST messages after the initial connection is established. This can manifest as HTTP 400 errors. Possible causes:
   - Missing or invalid authentication token
   - Server expects bidirectional communication via a different mechanism
   - Server only supports one-way events (client receives only)

3. **Authentication**: HTTP transports support bearer token authentication via the `auth-token` configuration property. Tokens can be provided via environment variables using `${ENV_VAR}` syntax.

### Troubleshooting HTTP Transport

If you encounter HTTP 400 errors with the `type: http` transport:

1. **Verify Auth Token**
   ```yaml
   aimo:
     mcp:
       servers:
         - id: github-mcp
           transport:
             type: http
             url: https://api.githubcopilot.com/mcp/
             auth-token: ${GITHUB_MCP_TOKEN}  # Ensure env var is set
   ```
   Check that the environment variable is set and valid.

2. **Check Server Logs**
   Look for detailed error messages in the server response body, which will be logged at ERROR level.

3. **Fallback to Stdio**
   For local servers, use the `type: stdio` transport instead:
   ```yaml
   - id: local-mcp
     transport:
       type: stdio
       command: /path/to/mcp-server
   ```

4. **Optional Startup**
   To allow the application to start even if HTTP transport fails:
   ```yaml
   aimo:
     mcp:
       required: false  # Don't fail startup if servers unreachable
   ```
