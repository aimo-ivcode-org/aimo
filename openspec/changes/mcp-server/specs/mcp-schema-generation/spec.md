# Specification: mcp-schema-generation

Automatic OpenRPC schema generation from annotated methods, enabling MCP clients to discover available tools and prompts with full parameter documentation.

## ADDED Requirements

### Requirement: Type Mapping and Schema Generation
The framework SHALL automatically convert Kotlin/Java types to JSON Schema types for all tool parameters and return types.

**Details**: Type mapping includes:
- Primitives: `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`
- Collections: `List<T>`, `Map<String, V>`
- Dates: `LocalDateTime`, `Date` (serialized as ISO 8601 strings)
- Custom classes: Inspected recursively to generate object schemas
- Nullable types: Kotlin `T?` and Java `Optional<T>` marked as optional

#### Scenario: String parameter mapped to string schema
```kotlin
@McpTool
fun search(query: String): String = ...
// Generated schema: {"type": "string", "description": "..."}
```

#### Scenario: Complex type with nested objects
```kotlin
data class SearchResult(val title: String, val url: String)

@McpTool
fun search(query: String): SearchResult = ...
// Return type schema: {"type": "object", "properties": {"title": {...}, "url": {...}}}
```

#### Scenario: List parameter
```kotlin
@McpTool
fun bulkAnalyze(items: List<String>): String = ...
// Parameter schema: {"type": "array", "items": {"type": "string"}}
```

### Requirement: Parameter Documentation in Schema
The framework SHALL include parameter descriptions, examples, and requirements in generated OpenRPC schemas.

**Details**:
- Description from `@McpParam(description=...)` or JavaDoc
- Example values from `@McpParam(example=...)`
- Required/optional based on `@McpParam(required=...)` or Kotlin nullability
- All parameters indexed in `properties` and `required` arrays
- `@McpContext` parameters excluded from schema

#### Scenario: Complete parameter with all metadata
```kotlin
@McpTool
fun search(
  @McpParam(description = "Search query text", example = "climate change", required = true)
  query: String,
  @McpParam(description = "Max results", example = "10", required = false)
  limit: Int? = 10
): String = ...
// Schema includes descriptions, examples, and marks 'query' as required, 'limit' as optional
```

#### Scenario: Context parameter excluded from schema
```kotlin
@McpTool
fun analyze(
  text: String,
  @McpContext
  context: Map<String, Any>
): String = ...
// Schema only includes 'text'; context parameter not visible to MCP clients
```

### Requirement: OpenRPC Schema Compliance
Generated schemas SHALL conform to the OpenRPC 1.0 specification for complete interoperability with MCP clients.

**Details**:
- Tool schemas follow OpenRPC method definition structure
- Prompt schemas follow OpenRPC prompt structure
- Method names become schema names (or overridden via annotation)
- All field names and types match OpenRPC spec
- Schemas serializable to JSON for transmission to clients

#### Scenario: Tool schema structure
```json
{
  "name": "search",
  "description": "Search for documents",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": {"type": "string", "description": "Search query"}
    },
    "required": ["query"]
  }
}
```

### Requirement: Schema Caching and Reuse
The framework SHALL generate schemas at startup, cache them in memory, and reuse them for all client requests without regeneration.

**Details**:
- Schemas generated during `@McpService` bean initialization
- Cached in `McpServiceRegistry` with service ID + tool name as key
- Reused for all subsequent schema requests
- No runtime performance cost for schema generation after startup

#### Scenario: Multiple clients requesting same schema
First client request triggers schema generation; subsequent clients receive cached schema immediately.

### Requirement: Schema Validation at Startup
The framework SHALL validate all generated schemas at application startup, detecting configuration errors before runtime.

**Details**:
- Verify all parameters have JSON-serializable types
- Check for duplicate tool/prompt names within service
- Validate parameter names are valid JSON identifiers
- Detect unsupported types with helpful error messages
- Fail-fast with clear error messages including method name and type details

#### Scenario: Unsupported parameter type detected
```kotlin
@McpTool
fun process(data: CustomUnserializableType): String = ...
// At startup, framework logs:
// "Parameter type 'CustomUnserializableType' in method 'process' is not JSON-serializable"
```

#### Scenario: Duplicate tool names in same service
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
```kotlin
@McpTool
/** Search the knowledge base */
fun search(query: String): String = ...
// Schema includes description: "Search the knowledge base"
```

#### Scenario: Explicit annotation overrides JavaDoc
```kotlin
@McpTool(description = "New description")
/** Old description */
fun search(query: String): String = ...
// Schema uses: "New description"
```


