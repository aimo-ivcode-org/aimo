# Mcp Server Request Handling

## Purpose

Request processing pipeline for MCP JSON-RPC protocol: parsing, routing, invocation, and response generation.

## Requirements

### Requirement: JSON-RPC 2.0 Protocol Compliance
The framework SHALL accept and return valid JSON-RPC 2.0 messages conforming to the JSON-RPC 2.0 specification.

**Details**:
- Request format: `jsonrpc: "2.0"`, `method: string`, `params: object (optional)`, `id: integer|string|null`
- Response format: `jsonrpc: "2.0"`, `result: object (success)` OR `error: object (error)`, `id: integer|string|null`
- Error responses: `code: integer`, `message: string`, `data: object (optional)`

#### Scenario: Valid tool call request and response
- **WHEN** client sends valid JSON-RPC 2.0 tool call request with toolName and arguments
- **THEN** framework routes to tool handler, executes tool, and returns JSON-RPC 2.0 response with result

#### Scenario: Error response for unknown method
- **WHEN** client sends request with unknown method name
- **THEN** framework returns JSON-RPC 2.0 error response with code -32601 (Method not found)

### Requirement: Request Method Routing
The framework SHALL route JSON-RPC requests to appropriate handlers based on the `method` field.

**Details**:
- `tools/call` → Tool invocation handler
- `prompts/get` → Prompt handler
- `resources/list` → Resource list handler
- `resources/read` → Resource read handler
- Unknown methods → Return `-32601` (Method not found) error

#### Scenario: Route tool/call to tool handler
- **WHEN** request method is "tools/call" with toolName and arguments
- **THEN** framework routes request to tool invocation handler

#### Scenario: Route prompts/get to prompt handler
- **WHEN** request method is "prompts/get" with promptName parameter
- **THEN** framework routes request to prompt handler

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
**WHEN** a tool call request is received with valid parameters
**THEN** the framework finds the tool, validates parameters, invokes the method, and returns the result
```json
Request: {"jsonrpc": "2.0", "method": "tools/call", "params": {"toolName": "search", "arguments": {"query": "AI"}}, "id": 1}
```
Framework: 1) Finds `search` tool, 2) Validates `query` is string, 3) Calls method with `"AI"`, 4) Returns result

#### Scenario: Type conversion and validation
**WHEN** a tool request provides a string value for an integer parameter (e.g., `"limit": "10"`)
**THEN** the framework converts the value to the correct type (`10` as integer) before invocation
```kotlin
@McpTool
fun getTop(limit: Int): String = ...
```
Request with `"limit": "10"` (string) is converted to `10` (integer) before invocation.

#### Scenario: Tool not found returns error
**WHEN** a request specifies a tool name that does not exist in the registry
**THEN** the server returns an error response with code `-32601` (Method not found)
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
**WHEN** a tool request is missing a required parameter (e.g., `query` for the `search` tool)
**THEN** the server returns an error response with code `-32602` that specifies the missing parameter
Request without required `query` parameter returns:
```json
{"error": {"code": -32602, "message": "Missing required parameter 'query'"}, "id": 1}
```

#### Scenario: Type mismatch detection
**WHEN** a tool request provides a parameter value of the wrong type (e.g., string `"not_a_number"` for an integer parameter)
**THEN** the server returns an error response with code `-32602` that describes the type mismatch
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
**WHEN** a tool method throws an exception during execution (e.g., `ArithmeticException` from division by zero)
**THEN** the framework catches the exception and returns an error response with code `-32603` including the tool name and error details
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
- **WHEN** client requests prompt via "prompts/get" method with promptName parameter
- **THEN** framework invokes prompt method and returns prompt text as result

#### Scenario: Parameterized prompt
- **WHEN** prompt method accepts parameters and request includes arguments
- **THEN** framework binds arguments to parameters and returns prompt with values filled in
