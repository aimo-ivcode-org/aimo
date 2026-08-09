# Mcp Server Schema Generation

## Purpose

Automatic OpenRPC schema generation from annotated methods, enabling MCP clients to discover available tools and prompts with full parameter documentation.

## Requirements

### Requirement: Type Mapping and Schema Generation
The framework SHALL automatically convert Kotlin/Java types to JSON Schema types for all tool parameters and return types.

**Details**: Type mapping includes:
- Primitives: `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`
- Collections: `List<T>`, `Map<String, V>`
- Dates: `LocalDateTime`, `Date` (serialized as ISO 8601 strings)
- Custom classes: Inspected recursively to generate object schemas
- Nullable types: Kotlin `T?` and Java `Optional<T>` marked as optional

#### Scenario: String parameter mapped to string schema
- **WHEN** tool method has a String parameter
- **THEN** generated schema maps parameter type to JSON Schema type "string"

#### Scenario: Complex type with nested objects
- **WHEN** tool method returns a data class with multiple properties
- **THEN** generated schema creates object type with nested properties for each field

#### Scenario: List parameter
- **WHEN** tool method has a `List<String>` parameter
- **THEN** generated schema maps to JSON Schema array type with string items

### Requirement: Parameter Documentation in Schema
The framework SHALL include parameter descriptions, examples, and requirements in generated OpenRPC schemas.

**Details**:
- Description from `@McpParam(description=...)` or JavaDoc
- Example values from `@McpParam(example=...)`
- Required/optional based on `@McpParam(required=...)` or Kotlin nullability
- All parameters indexed in `properties` and `required` arrays
- `@McpContext` parameters excluded from schema

#### Scenario: Complete parameter with all metadata
- **WHEN** tool parameter has `@McpParam` annotation with description, example, and required flag
- **THEN** generated schema includes all metadata: description, example, and marks parameter as required/optional

#### Scenario: Context parameter excluded from schema
- **WHEN** tool method has `@McpContext` parameter of type `Map<String, Any>`
- **THEN** generated schema excludes context parameter from properties and required arrays

### Requirement: OpenRPC Schema Compliance
Generated schemas SHALL conform to the OpenRPC 1.0 specification for complete interoperability with MCP clients.

**Details**:
- Tool schemas follow OpenRPC method definition structure
- Prompt schemas follow OpenRPC prompt structure
- Method names become schema names (or overridden via annotation)
- All field names and types match OpenRPC spec
- Schemas serializable to JSON for transmission to clients

#### Scenario: Tool schema structure
- **WHEN** tool is registered in framework
- **THEN** generated schema follows OpenRPC structure with name, description, inputSchema with properties and required arrays

### Requirement: Schema Caching and Reuse
The framework SHALL generate schemas at startup, cache them in memory, and reuse them for all client requests without regeneration.

**Details**:
- Schemas generated during `@McpService` bean initialization
- Cached in `McpServiceRegistry` with service ID + tool name as key
- Reused for all subsequent schema requests
- No runtime performance cost for schema generation after startup

#### Scenario: Multiple clients requesting same schema
**WHEN** multiple clients request the schema for the same tool
**THEN** the first request triggers schema generation; subsequent requests receive the cached schema immediately without regeneration

### Requirement: Schema Validation at Startup
The framework SHALL validate all generated schemas at application startup, detecting configuration errors before runtime.

**Details**:
- Verify all parameters have JSON-serializable types
- Check for duplicate tool/prompt names within service
- Validate parameter names are valid JSON identifiers
- Detect unsupported types with helpful error messages
- Fail-fast with clear error messages including method name and type details

#### Scenario: Unsupported parameter type detected
**WHEN** the framework encounters a parameter type that is not JSON-serializable during schema generation
**THEN** the framework logs a clear error message identifying the method, parameter type, and suggesting alternatives
```kotlin
@McpTool
fun process(data: CustomUnserializableType): String = ...
// At startup, framework logs:
// "Parameter type 'CustomUnserializableType' in method 'process' is not JSON-serializable"
```

#### Scenario: Duplicate tool names in same service
**WHEN** the framework detects multiple tools with the same name during schema validation
**THEN** it logs an error message during startup and fails to register the duplicate tool
```kotlin
@McpService
class MyService {
  @McpTool
  fun search(query: String): String = ...

  @McpTool(name = "search")  // ERROR: Duplicate name
  fun find(q: String): String = ...
}
// At startup: "Duplicate tool name 'search' in service MyService"
```

### Requirement: Documentation Extraction from Multiple Sources
The framework SHALL extract descriptions from both explicit annotations and JavaDoc, with explicit annotations taking priority.

**Details**:
- Priority order: `@McpTool(description=...)` > JavaDoc > auto-generated
- JavaDoc extracted from method's `/** ... */` comment
- Parameter JavaDoc extracted for parameter descriptions if available
- Example values from `@McpParam(example=...)`

#### Scenario: JavaDoc serves as fallback description
- **WHEN** tool method has JavaDoc comment but no explicit `@McpTool(description=...)`
- **THEN** framework extracts description from JavaDoc and includes in schema

#### Scenario: Explicit annotation overrides JavaDoc
- **WHEN** tool method has both explicit `@McpTool(description=...)` and JavaDoc
- **THEN** framework uses explicit annotation description, not JavaDoc
