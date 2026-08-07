# aimo-mcp-server

A Spring Boot framework for building **Model Context Protocol (MCP)** servers using declarative annotations.

## Overview

`aimo-mcp-server` provides a lightweight, easy-to-use framework for building MCP servers in Java/Kotlin. Instead of dealing with low-level JSON-RPC protocol handling and transport mechanics, developers focus on implementing business logic via annotated methods.

### Key Features

- **Declarative Annotations**: Use `@McpService`, `@McpTool`, and `@McpPrompt` to define callable operations
- **Automatic Schema Generation**: OpenRPC schemas are generated from method signatures  
- **Multiple Transports**: HTTP, SSE, and stdio transports supported out-of-the-box
- **Spring Boot Integration**: Full auto-configuration and component discovery
- **Parameter Binding**: Automatic type conversion and validation
- **Error Handling**: Structured MCP error responses with validation
- **Context Injection**: Access request context in tool methods via `@McpContext`

## Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                 HTTP/SSE/Stdio Transports                    │
│   (Transport interface implementations)                       │
└──────────────────────────────┬──────────────────────────────┘
                               │
                      ┌────────▼────────┐
                      │ McpRequestHandler│
                      │  (routes requests)│
                      └────────┬────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
       ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
       │ToolCallHandler
│  │PromptGetHandler│  │ ResourceHandler
│
       └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
              │                │                │
       ┌──────▼────────────────▼────────────────▼─┐
       │         McpServiceRegistry               │
       │   (service/tool/prompt discovery)        │
       └─────────────────────────────────────────┘
              │
       ┌──────▼──────────────────┐
       │  @McpService Beans      │
       │  (user-defined services)│
       └─────────────────────────┘
```

### Request Flow

1. **Transport Layer** receives incoming request (HTTP POST, SSE, stdio)
2. **McpRequestHandler** routes based on method name (`tools/call`, `prompts/get`, etc.)
3. **Specific Handler** (ToolCallHandler, PromptGetHandler) executes:
   - Looks up tool/prompt in registry
   - Validates parameters
   - Binds parameters to method arguments
   - Invokes the tool/prompt method on the service bean
4. **Response** is formatted as JSON-RPC and sent back via transport

### Annotation Framework

#### @McpService
Marks a Spring bean as an MCP service provider. All public methods annotated with `@McpTool` or `@McpPrompt` are discovered at startup.

```kotlin
@McpService
class MyService {
    @McpTool(name = "my-tool", description = "Does something useful")
    fun myTool(input: String): String {
        return "Result: $input"
    }
}
```

#### @McpTool
Marks a method as a callable MCP tool. Method signature becomes the tool schema.

```kotlin
@McpTool(description = "Add two numbers")
fun add(
    @McpParam(description = "First number")
    a: Double,
    
    @McpParam(description = "Second number")
    b: Double
): Double = a + b
```

#### @McpPrompt
Marks a method as an MCP prompt (template/workflow). Signature: `() -> String?` or `(context) -> String?`.

```kotlin
@McpPrompt(description = "Get calculator help")
fun getHelp(): String {
    return "Available operations: add, subtract, multiply, divide"
}
```

#### @McpParam
Documents a tool parameter. Provides description and specifies if required.

```kotlin
@McpParam(description = "User name", required = true)
name: String
```

#### @McpContext
Injects request context into a tool method. Context contains metadata like `requestId`, `toolName`, etc.

```kotlin
@McpTool
fun myTool(
    @McpParam(description = "Input")
    input: String,
    
    @McpContext
    context: Map<String, Any?>
): String {
    val requestId = context["requestId"]
    return "Processed $input for request $requestId"
}
```

### Schema Generation

Method signatures are automatically converted to OpenRPC-compliant schemas:

```kotlin
@McpTool
fun divide(
    @McpParam(description = "Dividend") numerator: Double,
    @McpParam(description = "Divisor") denominator: Double
): String { ... }
```

Generates:

```json
{
  "name": "divide",
  "description": "Divides numerator by denominator",
  "inputSchema": {
    "type": "object",
    "properties": {
      "numerator": {
        "type": "number",
        "description": "Dividend"
      },
      "denominator": {
        "type": "number",
        "description": "Divisor"
      }
    },
    "required": ["numerator", "denominator"]
  }
}
```

## Quick Start

### 1. Add Dependency

In your Spring Boot application's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":aimo-mcp-server"))
}
```

### 2. Enable MCP Server

Add `@EnableMcpServer` to your Spring Boot configuration:

```kotlin
@SpringBootApplication
@EnableMcpServer
class MyMcpServerApplication

fun main(args: Array<String>) {
    runApplication<MyMcpServerApplication>(*args)
}
```

The `@EnableMcpServer` annotation registers all necessary beans. Individual transports (HTTP, SSE, stdio) are controlled via their respective enabled flags in configuration.

### 3. Create a Service

Define an `@McpService` with `@McpTool` methods:

```kotlin
@McpService
class CalculatorService {
    @McpTool(description = "Add two numbers")
    fun add(
        @McpParam(description = "First number") a: Double,
        @McpParam(description = "Second number") b: Double
    ): Double = a + b
}
```

### 4. Configure (Optional)

In `application.yml`:

```yaml
aimo:
  mcp-server:
    name: "my-mcp-server"
    version: "1.0.0"
    transports:
      http:
        enabled: true
        basePath: "/mcp"
```

### 5. Make Requests

**HTTP:**
```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "add",
      "arguments": {"a": 5, "b": 3}
    }
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{"type": "text", "text": "8.0"}]
  }
}
```

## Transports

### HTTP Transport

RESTful endpoint for request/response style communication.

**Base Path:** `/mcp` (configurable)

**Endpoints:**
- `POST /mcp/` - Generic JSON-RPC endpoint
- `POST /mcp/tools/call` - Tool call
- `POST /mcp/prompts/get` - Prompt get
- `POST /mcp/tools/list` - List tools
- `POST /mcp/prompts/list` - List prompts

### SSE Transport

Server-Sent Events for streaming/subscription-based communication.

**Base Path:** `/mcp/sse` (configurable)

**Endpoints:**
- `GET /mcp/sse/connect` - Establish SSE connection
- `POST /mcp/sse/request` - Send request (responses streamed to connected clients)
- `GET /mcp/sse/health` - Health check

### Stdio Transport

Standard input/output for subprocess/local communication.

**Configuration:**
```yaml
aimo:
  mcp-server:
    transports:
      stdio:
        enabled: true
```

## Configuration

Configuration via `application.yml` under `aimo.mcp-server.*`:

The MCP server framework is enabled by using the `@EnableMcpServer` annotation. Individual transports and discovery behavior are then configured via properties:

```yaml
aimo:
  mcp-server:
    name: "my-mcp-server"                      # Server name
    version: "1.0.0"                           # Server version
    
    transports:
      http:
        enabled: true                          # Enable HTTP transport
        basePath: "/mcp"                       # Base path
        connectionTimeout: 30000               # Connection timeout (ms)
        readTimeout: 30000                     # Read timeout (ms)
      
      sse:
        enabled: false                         # Enable SSE transport
        basePath: "/mcp/sse"
        connectionTimeout: 300000
        keepAliveInterval: 30000
      
      stdio:
        enabled: false                         # Enable stdio transport
    
    discovery:
      enabled: true                            # Enable auto-discovery
      basePackages: ""                         # Scan all packages if empty
      failIfEmpty: false                       # Fail if no services found
    
    errorHandling:
      includeStackTrace: false                 # Include stack traces in errors
      includeErrorData: true                   # Include error data
      failOnValidationError: true              # Fail on validation error
```

## Error Handling

Tools that throw exceptions are caught and converted to structured MCP errors:

```kotlin
@McpTool
fun riskyOperation(): String {
    throw IllegalArgumentException("Something went wrong")
}
```

Returns:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "Invalid parameters",
    "data": {"cause": "Something went wrong"}
  }
}
```

## Parameter Validation

Parameters are validated before tool invocation:

1. **Required Field Checking** - Required parameters must be provided
2. **Type Validation** - Values are checked against expected types
3. **Type Conversion** - Automatic conversion for compatible types:
   - Numbers ↔ numeric strings
   - Booleans ↔ boolean strings
   - Objects ↔ JSON maps

### Custom Validation

Implement validation in your tool method:

```kotlin
@McpTool
fun processData(
    @McpParam(description = "Data", required = true)
    data: String
): String {
    if (data.length > 1000) {
        throw IllegalArgumentException("Data too large")
    }
    return process(data)
}
```

## Integration with aimo-mcp-client

Services built with `aimo-mcp-server` can be discovered and consumed by `aimo-mcp-client`:

```yaml
# In aimo-mcp-client configuration
aimo:
  mcp:
    servers:
      - id: "my-calculator"
        transport: "http"
        url: "http://localhost:8080/mcp"
        scope: ["global"]
```

## Examples

See `CalculatorService` in the example code for a complete working service.

## Development

### Build

```bash
./gradlew.bat :aimo-mcp-server:build
```

### Test

```bash
./gradlew.bat :aimo-mcp-server:test
```

### Run Example

```bash
./gradlew.bat :aimo-mcp-server:bootRun
```

## Future Enhancements

- Resources (read/list/subscribe) implementation
- Streaming tool results
- Tool result callbacks
- Custom parameter serialization
- Interceptors/middleware system
- Performance optimizations (AOT compilation, native image support)

## License

Same as AIMO project

