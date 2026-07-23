# Specification: mcp-transport-support

Multi-transport support for MCP server connections: HTTP, Server-Sent Events (SSE), and stdio transports. Allows clients to connect via their preferred protocol.

## ADDED Requirements

### Requirement: HTTP Transport Implementation
The framework SHALL provide an HTTP transport endpoint via Spring's servlet container (using @Controller or handler registration) that accepts MCP JSON-RPC requests and returns responses, enabling web-based client connections.

**Details**:
- Implemented as Spring @Controller or registered handler in Spring's DispatcherServlet
- Default endpoint: `POST /mcp` (configurable via YAML)
- Request body: JSON-RPC 2.0 request object
- Response body: JSON-RPC 2.0 response object
- HTTP status codes: 200 (success), 400 (invalid JSON), 405 (wrong method), 500 (handler error)
- Uses Spring's embedded Tomcat (or configured servlet container); no standalone HTTP server created
- Integrates with Spring Boot's `server.port` and `server.servlet.context-path` settings

#### Scenario: Simple HTTP request-response
```bash
POST /mcp
Content-Type: application/json

{"jsonrpc": "2.0", "method": "tools/call", "params": {"toolName": "search", "arguments": {"query": "AI"}}, "id": 1}
```
Response: `{"jsonrpc": "2.0", "result": {...}, "id": 1}`

#### Scenario: HTTP endpoint integrated with Spring Boot
Application started with Spring's embedded Tomcat on port 8080; MCP endpoint automatically available at `http://localhost:8080/mcp` without additional server setup.

#### Scenario: HTTP 400 for invalid JSON
```bash
POST /mcp
{invalid json}
```
Response: HTTP 400, `{"jsonrpc": "2.0", "error": {"code": -32700, "message": "Parse error"}, "id": null}`

### Requirement: Stdio Transport for Local Processes
The framework SHALL provide newline-delimited JSON communication via stdin/stdout for local process connections (e.g., Claude Desktop).

**Details**:
- Reads JSON-RPC requests from stdin (one per line)
- Writes JSON-RPC responses to stdout (one per line)
- Each line is a complete JSON object
- Stderr available for logging
- Process terminates when stdin closes

#### Scenario: Stdio communication flow
```
Client writes to stdin:
{"jsonrpc": "2.0", "method": "tools/call", "params": {...}, "id": 1}

Server writes to stdout:
{"jsonrpc": "2.0", "result": {...}, "id": 1}
```

### Requirement: Multi-Transport Configuration
Applications SHALL be able to enable multiple transports simultaneously, with each transport accessible independently.

**Details**:
- Configure via YAML: `aimo-mcp-server.transports.*`
- Each transport: `enabled`, transport-specific settings (port, path, etc.)
- At least one transport must be enabled
- All enabled transports start during server startup
- All enabled transports receive requests from their respective clients

#### Scenario: HTTP and stdio both enabled
```yaml
aimo-mcp-server:
  transports:
    http:
      enabled: true
      port: 8080
      context-path: /mcp
    stdio:
      enabled: true
```
Server accepts HTTP requests on port 8080 AND stdio from terminal simultaneously.

#### Scenario: No transports enabled fails at startup
If all transports have `enabled: false`, framework logs error and server remains non-functional.

### Requirement: Transport Request Routing
Each transport SHALL receive MCP JSON-RPC requests and route them to the unified request handler.

**Details**:
- Transport abstracts protocol details (HTTP, stdio, etc.)
- All transports use same request handler
- Request context (transport type, request ID, metadata) captured and passed through
- Responses serialized and sent back via same transport

#### Scenario: Different transports use same tools
HTTP client calls `tools/call` method; stdio client calls same method; both use identical request handling logic.

### Requirement: Request Context Injection
Each transport SHALL capture request metadata and make it available to tools via `@McpContext` injection.

**Details**:
- Request ID (unique per request)
- Transport type (HTTP, stdio, SSE)
- Client metadata (remote address for HTTP, process info for stdio)
- Timestamp

#### Scenario: Tool receives context from HTTP request
```kotlin
@McpTool
fun search(
  query: String,
  @McpContext
  context: Map<String, Any>
): String {
  val requestId = context["requestId"] as String
  val transport = context["transport"] as String  // "HTTP"
  val clientAddr = context["clientAddress"] as String
  // ...
}
```
Tool can log with request ID and know it came from HTTP client.



