# Specification: mcp-error-handling

Comprehensive error handling strategy ensuring server resilience, clear error messages, and graceful degradation.

## ADDED Requirements

### Requirement: Error Code Classification and Response Format
The framework SHALL classify errors into standard JSON-RPC 2.0 error codes and return structured error responses.

**Details**:
- Parse errors: `-32700`
- Invalid requests: `-32600`
- Method not found: `-32601`
- Invalid parameters: `-32602`
- Internal errors: `-32603`
- Each error includes message and optional data object

#### Scenario: Parse error for invalid JSON
```json
Request: {invalid json}
Response: {"jsonrpc": "2.0", "error": {"code": -32700, "message": "Parse error"}, "id": null}
```

#### Scenario: Invalid parameters error
Tool `search` expects `query: string`, request provides `query: 123`:
```json
{"error": {"code": -32602, "message": "Parameter 'query' has invalid type. Expected string, got number"}}
```

### Requirement: Tool Execution Error Handling
The framework SHALL catch all exceptions during tool execution, log them, and return appropriate error responses.

**Details**:
- Catch all exceptions (checked and unchecked)
- Log full exception with stack trace for debugging
- Convert exception to appropriate error code
- Include user-friendly error message in response
- Tool failure doesn't affect server or other tools

#### Scenario: Tool throws RuntimeException
```kotlin
@McpTool
fun search(query: String): String {
  throw IllegalArgumentException("Query too long")
}
```
Response:
```json
{"error": {"code": -32602, "message": "Parameter validation failed: Query too long"}}
```

#### Scenario: Tool timeout
Tool execution exceeds timeout limit; framework:
1. Stops tool execution
2. Returns error: `{"code": -32603, "message": "Tool execution timeout exceeded"}`

### Requirement: Annotation Validation at Startup
The framework SHALL validate all annotations at application startup, failing fast with clear error messages for invalid usage.

**Details**:
- `@McpService` only on classes
- `@McpTool` and `@McpPrompt` only on public methods
- `@McpContext` only on `Map<String, Any>` parameters
- No duplicate tool/prompt names within service
- Verification happens before service is registered

#### Scenario: Invalid @McpTool usage detected at startup
```kotlin
@McpService
class MyService {
  @McpTool
  private fun search(query: String): String = ...  // ERROR: not public
}
```
Framework logs: `@McpTool annotation on private method 'search'. Only public methods supported.`

#### Scenario: Duplicate tool names fail validation
```kotlin
@McpService
class MyService {
  @McpTool(name = "search")
  fun method1(): String = ...

  @McpTool(name = "search")  // ERROR: duplicate
  fun method2(): String = ...
}
```
Framework logs: `Duplicate tool name 'search' in service MyService`

### Requirement: Schema Generation Error Handling
The framework SHALL detect unsupported types and parameter configurations during schema generation.

**Details**:
- Unsupported parameter types reported with helpful suggestions
- Complex custom types attempted with fallback to generic object
- Missing or invalid JavaDoc/descriptions handled gracefully
- Schema generation errors logged; service still registered with generic schema

#### Scenario: Unsupported parameter type
```kotlin
@McpTool
fun process(data: CustomClass): String = ...
```
If `CustomClass` is not JSON-serializable, framework logs:
`Parameter type 'CustomClass' in method 'process' is not JSON-serializable. Consider using String or List<String>`

### Requirement: Graceful Server Degradation
If a service experiences errors, other services and tools SHALL remain available.

**Details**:
- Single service initialization failure doesn't prevent other services from starting
- Single tool execution failure doesn't prevent other tools from being called
- Transport errors logged but don't crash server
- Server continues accepting requests even if some tools fail

#### Scenario: One service fails to initialize
Three services configured; service B fails at startup:
- Service A: ✓ Available
- Service B: ✗ Initialization failed (logged)
- Service C: ✓ Available
Server starts and services A and C remain fully functional.

### Requirement: Comprehensive Logging and Debugging
The framework SHALL provide structured logs at multiple levels for troubleshooting.

**Details**:
- DEBUG: Request details, parameter binding, method invocation
- INFO: Service startup, tool registration, schema generation
- WARN: Schema generation fallback, deprecated patterns
- ERROR: Service/tool execution failures, validation errors
- Include context: request ID, tool name, parameters (sanitized), execution time

#### Scenario: Debug-level logging for tool invocation
```
[DEBUG] Request received: id=123, method=tools/call, toolName=search
[DEBUG] Binding parameters: query="AI"
[DEBUG] Invoking tool: search(query="AI")
[DEBUG] Tool execution completed: 45ms, result="Found 5 articles"
```

### Requirement: Context Information in Error Messages
Error messages SHALL include sufficient context for users to understand and resolve issues.

**Details**:
- Tool name that failed
- Parameter names and types
- Actual values provided (if reasonable)
- Suggestions for correction
- Request ID for traceability

#### Scenario: Detailed parameter validation error
```
Error: "Parameter validation failed for tool 'search'
  - Parameter 'query': expected string, got number (123)
  - Parameter 'limit': expected integer 1-100, got -5
Request ID: req-12345"
```


