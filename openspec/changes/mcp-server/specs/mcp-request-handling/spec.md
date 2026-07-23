# Specification: mcp-request-handling

Request processing pipeline for MCP JSON-RPC protocol: parsing, routing, invocation, and response generation.

## ADDED Requirements

### Requirement: JSON-RPC 2.0 Protocol Compliance
The framework SHALL accept and return valid JSON-RPC 2.0 messages conforming to the JSON-RPC 2.0 specification.

**Details**:
- Request format: `jsonrpc: "2.0"`, `method: string`, `params: object (optional)`, `id: integer|string|null`
- Response format: `jsonrpc: "2.0"`, `result: object (success)` OR `error: object (error)`, `id: integer|string|null`
- Error responses: `code: integer`, `message: string`, `data: object (optional)`

#### Scenario: Valid tool call request and response
```json
Request: {"jsonrpc": "2.0", "method": "tools/call", "params": {"toolName": "search", "arguments": {"query": "test"}}, "id": 1}
Response: {"jsonrpc": "2.0", "result": {"content": "search results"}, "id": 1}
```

#### Scenario: Error response for unknown method
```json
Request: {"jsonrpc": "2.0", "method": "unknown_method", "params": {}, "id": 2}
Response: {"jsonrpc": "2.0", "error": {"code": -32601, "message": "Method not found"}, "id": 2}
```

### Requirement: Request Method Routing
The framework SHALL route JSON-RPC requests to appropriate handlers based on the `method` field.

**Details**:
- `tools/call` → Tool invocation handler
- `prompts/get` → Prompt handler
- `resources/list` → Resource list handler
- `resources/read` → Resource read handler
- Unknown methods → Return `-32601` (Method not found) error

#### Scenario: Route tool/call to tool handler
Request with `method: "tools/call"` is routed to tool invocation handler with parameters `toolName` and `arguments`.

#### Scenario: Route prompts/get to prompt handler
Request with `method: "prompts/get"` is routed to prompt handler with parameter `promptName`.

### Requirement: Tool Invocation with Parameter Binding
The framework SHALL invoke tool methods with automatic parameter type conversion and validation.

**Details**:
- Look up tool by name in service registry
- Extract parameters from request `arguments` object
- Validate parameter types against schema
- Convert JSON values to Kotlin/Java types
- Inject `@McpContext` parameters automatically
- Invoke method via reflection
- Return result or error

#### Scenario: Simple tool invocation
```json
Request: {"jsonrpc": "2.0", "method": "tools/call", "params": {"toolName": "search", "arguments": {"query": "AI"}}, "id": 1}
```
Framework: 1) Finds `search` tool, 2) Validates `query` is string, 3) Calls method with `"AI"`, 4) Returns result

#### Scenario: Type conversion and validation
```kotlin
@McpTool
fun getTop(limit: Int): String = ...
```
Request with `"limit": "10"` (string) is converted to `10` (integer) before invocation.

#### Scenario: Tool not found returns error
Request with `toolName: "nonexistent"` returns:
```json
{"jsonrpc": "2.0", "error": {"code": -32601, "message": "Tool not found: nonexistent"}, "id": 1}
```

### Requirement: Parameter Validation and Error Reporting
The framework SHALL validate parameters against generated schemas and return clear error messages for invalid input.

**Details**:
- Type checking (string, int, float, bool, array, object)
- Required field validation
- Optional parameter handling (null values)
- Format validation (if defined in schema)
- Error codes: `-32602` for invalid parameters
- Error messages include parameter name and expected type

#### Scenario: Missing required parameter
Request without required `query` parameter returns:
```json
{"error": {"code": -32602, "message": "Missing required parameter 'query'"}, "id": 1}
```

#### Scenario: Type mismatch detection
Tool expects `count: Int`, request provides `"count": "not_a_number"` returns:
```json
{"error": {"code": -32602, "message": "Parameter 'count' has invalid type. Expected integer, got string"}, "id": 1}
```

### Requirement: Tool Execution Error Handling
The framework SHALL catch exceptions during tool execution and convert them to structured JSON-RPC error responses.

**Details**:
- All exceptions caught and logged
- Exception type mapped to MCP error code
- Error message includes root cause
- Tool execution failures don't crash server
- Other tools remain callable

#### Scenario: Tool throws exception
```kotlin
@McpTool
fun divide(a: Int, b: Int): String {
  return (a / b).toString()  // Throws ArithmeticException if b=0
}
```
Request with `b=0` returns:
```json
{"error": {"code": -32603, "message": "Tool 'divide' execution failed: Division by zero"}, "id": 1}
```

### Requirement: Prompt Invocation Handler
The framework SHALL invoke prompt methods similarly to tools, supporting prompts that may take parameters.

**Details**:
- Route `prompts/get` method to prompt handler
- Support parameterized and non-parameterized prompts
- Parameter binding same as tools
- Return type: String (prompt text)

#### Scenario: Simple parameterless prompt
Request: `{"jsonrpc": "2.0", "method": "prompts/get", "params": {"promptName": "research_guide"}, "id": 1}`
Returns prompt text as result.

#### Scenario: Parameterized prompt
```kotlin
@McpPrompt
fun analyzeTemplate(topic: String): String = "Analyze this topic: $topic"
```
Request with `arguments: {"topic": "AI"}` returns prompt with topic filled in.


