# Transport Configuration Guide

## Overview

`aimo-mcp-server` supports three transports for different use cases:
- **HTTP**: Request/response over HTTP
- **SSE**: Server-Sent Events for streaming
- **Stdio**: Standard input/output for subprocesses

## HTTP Transport

### Configuration

```yaml
aimo:
  mcp-server:
    transports:
      http:
        enabled: true
        basePath: "/mcp"              # URL prefix
        connectionTimeout: 30000      # Connection timeout (ms)
        readTimeout: 30000            # Request read timeout (ms)
```

### Usage

Send JSON-RPC requests to `POST /mcp/`:

```bash
curl -X POST http://localhost:9090/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-1",
    "method": "tools/list"
  }'
```

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/mcp/` | POST | Generic JSON-RPC endpoint |
| `/mcp/tools/call` | POST | Call a tool |
| `/mcp/prompts/get` | POST | Get a prompt |
| `/mcp/tools/list` | POST | List available tools |
| `/mcp/prompts/list` | POST | List available prompts |

### Common Patterns

**List all tools:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

**Call a tool:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "add",
    "arguments": {"a": 5, "b": 3}
  }
}
```

**Get a prompt:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "prompts/get",
  "params": {
    "name": "calculator-help"
  }
}
```

## SSE Transport

### Configuration

```yaml
aimo:
  mcp-server:
    transports:
      sse:
        enabled: true
        basePath: "/mcp/sse"          # URL prefix
        connectionTimeout: 300000     # Connection timeout (5 min)
        keepAliveInterval: 30000      # Keep-alive ping interval
```

### Usage

1. **Connect:**
```bash
curl -N http://localhost:9090/mcp/sse/connect
```

2. **Send request (in another terminal):**
```bash
curl -X POST http://localhost:9090/mcp/sse/request \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-1",
    "method": "tools/call",
    "params": {"name": "add", "arguments": {"a": 5, "b": 3}}
  }'
```

3. **Responses stream to connected clients**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/mcp/sse/connect` | GET | Establish SSE connection |
| `/mcp/sse/request` | POST | Send request (broadcasts response) |
| `/mcp/sse/health` | GET | Health check |

## Stdio Transport

### Configuration

```yaml
aimo:
  mcp-server:
    transports:
      stdio:
        enabled: true
```

### Usage

Start the application and communicate via stdin/stdout:

```bash
java -jar my-mcp-server.jar
```

Then send JSON-RPC requests (one per line):

```
{"jsonrpc":"2.0","id":1,"method":"tools/list"}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"add","arguments":{"a":5,"b":3}}}
```

Responses are printed (one per line):

```
{"jsonrpc":"2.0","id":1,"result":{"tools":[...]}}
{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"8.0"}]}}
```

### Use Cases

- **Spring Integration**: Run MCP server as subprocess
- **Testing**: Direct stdin/stdout communication
- **Development**: Debug tool invocations

## Combining Transports

All transports can be enabled simultaneously:

```yaml
aimo:
  mcp-server:
    transports:
      http:
        enabled: true
      sse:
        enabled: true
      stdio:
        enabled: true
```

Clients can choose the transport that suits their needs.

## Troubleshooting

### HTTP returns 404
- Check that transport is enabled in configuration
- Verify endpoint path matches basePath
- Ensure Spring Boot is running on the configured port

### SSE connection times out
- Increase `connectionTimeout` if clients need longer
- Check that keep-alive interval is reasonable
- Verify browser/client supports SSE

### Stdio not responding
- Ensure `enabled: true` in configuration
- Check that stdin/stdout are not redirected elsewhere
- Monitor logs for errors

## Performance Considerations

### HTTP
- Lowest latency
- Best for request/response patterns
- Use for integration with aimo-mcp-client

### SSE
- Good for streaming
- Higher overhead than HTTP
- Better for long-lived connections

### Stdio
- Direct process communication
- No network overhead
- Good for development and testing

