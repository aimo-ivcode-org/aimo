# Mcp Server Error Handling

## Purpose

Comprehensive error handling strategy ensuring server resilience, clear error messages, and graceful degradation.

## Requirements

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
- **WHEN** client sends malformed JSON to framework
- **THEN** framework returns JSON-RPC 2.0 Parse Error (-32700) with message "Parse error"

#### Scenario: Invalid parameters error
- **WHEN** tool `search` expects `query: string` but request provides `query: 123`
- **THEN** framework returns error code -32602 with message "Parameter 'query' has invalid type. Expected string, got number"

### Requirement: Tool Execution Error Handling
The framework SHALL catch all exceptions during tool execution, log them, and return appropriate error responses.

**Details**:
- Catch all exceptions (checked and unchecked)
- Log full exception with stack trace for debugging
- Convert exception to appropriate error code
- Include user-friendly error message in response
- Tool failure doesn't affect server or other tools

#### Scenario: Tool throws RuntimeException
- **WHEN** tool method throws an `IllegalArgumentException` during execution
- **THEN** framework catches the exception and returns error code -32603 with appropriate user-friendly error message

#### Scenario: Tool timeout
- **WHEN** tool execution exceeds configured timeout limit
- **THEN** framework stops execution and returns error code -32603 with message "Tool execution timeout exceeded"

### Requirement: Annotation Validation at Startup
The framework SHALL validate all annotations at application startup, failing fast with clear error messages for invalid usage.

**Details**:
- `@McpService` only on classes
- `@McpTool` and `@McpPrompt` only on public methods
- `@McpContext` only on `Map<String, Any>` parameters
- No duplicate tool/prompt names within service
- Verification happens before service is registered

#### Scenario: Invalid @McpTool usage detected at startup
- **WHEN** `@McpTool` annotation is used on a private method
- **THEN** framework detects invalid usage at startup and logs error "Only public methods supported"

#### Scenario: Duplicate tool names fail validation
- **WHEN** multiple `@McpTool` methods in same service have the same name
- **THEN** framework detects duplication at startup and logs error "Duplicate tool name 'X' in service Y"

### Requirement: Schema Generation Error Handling
The framework SHALL detect unsupported types and parameter configurations during schema generation.

**Details**:
- Unsupported parameter types reported with helpful suggestions
- Complex custom types attempted with fallback to generic object
- Missing or invalid JavaDoc/descriptions handled gracefully
- Schema generation errors logged; service still registered with generic schema

#### Scenario: Unsupported parameter type
- **WHEN** tool method has a parameter with a non-JSON-serializable type
- **THEN** framework logs helpful error message suggesting JSON-serializable alternatives and continues with generic schema

### Requirement: Graceful Server Degradation
If a service experiences errors, other services and tools SHALL remain available.

**Details**:
- Single service initialization failure doesn't prevent other services from starting
- Single tool execution failure doesn't prevent other tools from being called
- Transport errors logged but don't crash server
- Server continues accepting requests even if some tools fail

#### Scenario: One service fails to initialize
- **WHEN** one of multiple configured services fails initialization
- **THEN** framework logs error for failing service but continues starting remaining services

### Requirement: Comprehensive Logging and Debugging
The framework SHALL provide structured logs at multiple levels for troubleshooting.

**Details**:
- DEBUG: Request details, parameter binding, method invocation
- INFO: Service startup, tool registration, schema generation
- WARN: Schema generation fallback, deprecated patterns
- ERROR: Service/tool execution failures, validation errors
- Include context: request ID, tool name, parameters (sanitized), execution time

#### Scenario: Debug-level logging for tool invocation
- **WHEN** tool invocation occurs at DEBUG log level
- **THEN** framework logs detailed information: request ID, method, tool name, parameter binding, and execution time

### Requirement: Context Information in Error Messages
Error messages SHALL include sufficient context for users to understand and resolve issues.

**Details**:
- Tool name that failed
- Parameter names and types
- Actual values provided (if reasonable)
- Suggestions for correction
- Request ID for traceability

#### Scenario: Detailed parameter validation error
- **WHEN** parameter validation fails during tool invocation
- **THEN** error message includes tool name, parameter details, type mismatch information, and request ID
