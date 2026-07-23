# Specification: mcp-annotation-framework

Declarative annotation-based framework for defining MCP services, tools, and prompts in Spring applications.

## ADDED Requirements

### Requirement: Core Annotations
The framework SHALL provide five core annotations that developers can use to declare MCP services, tools, and prompts with full Spring integration.

**Details**: Framework provides five core annotations:
1. `@McpService` — Marks a Spring bean as MCP service provider (optional: `id`, `name`, `description`)
2. `@McpTool` — Marks public methods as LLM-callable tools (optional: `name`, `description`, `category`)
3. `@McpPrompt` — Marks public methods as MCP prompts returning String (optional: `name`, `description`)
4. `@McpParam` — Documents method parameters (optional: `description`, `example`, `required`)
5. `@McpContext` — Auto-injects request context into `Map<String, Any>` parameters

#### Scenario: Define a simple MCP service with one tool
```kotlin
@McpService(id = "weather", name = "Weather Service")
class WeatherService {
  @McpTool(description = "Get current weather for a location")
  fun getWeather(
    @McpParam(description = "City name", example = "Seattle")
    location: String
  ): String = "72°F and sunny"
}
```

#### Scenario: Tool with context parameter
```kotlin
@McpTool
fun analyzeData(
  data: String,
  @McpContext
  context: Map<String, Any>  // Automatically injected; excluded from schema
): String = "Analyzed: $data"
```

### Requirement: Service Discovery
The framework SHALL automatically discover all `@McpService` beans during Spring application startup and register them as available MCP services.

**Details**: 
- Classpath scanning via Spring's component discovery
- Bean registration in Spring context
- All `@McpTool` and `@McpPrompt` methods extracted and indexed
- Services remain available throughout application lifetime

#### Scenario: Auto-discovery in multi-service application
When Spring Boot app starts with multiple `@McpService` classes, all are discovered and registered without manual configuration.

#### Scenario: Tool extraction for schema generation
After service discovery, all `@McpTool` methods are extracted and their signatures used for OpenRPC schema generation.

### Requirement: Metadata Extraction
The framework SHALL extract metadata from annotations and method signatures to support schema generation and documentation.

**Details**:
- Method inspection: name, return type, parameters, types, annotations
- Documentation from: explicit annotations, JavaDoc comments, `@McpParam` descriptions
- Type information used for schema generation and runtime validation
- JavaDoc serves as fallback description source

#### Scenario: Generate documentation from JavaDoc
```kotlin
@McpTool
/** Get weather forecast for a location. */
fun getWeather(location: String): String = ...
```
Description extracted from JavaDoc: "Get weather forecast for a location."

#### Scenario: Prioritize explicit annotation over JavaDoc
```kotlin
@McpTool(description = "Current weather") // This takes priority
/** Old description */
fun getWeather(location: String): String = ...
```
Schema uses "Current weather", not the JavaDoc.

### Requirement: Annotation Validation
The framework SHALL validate all annotations at application startup, failing fast with clear error messages for invalid usage.

**Details**:
- `@McpService` only on classes
- `@McpTool` and `@McpPrompt` only on public methods in `@McpService` classes
- `@McpContext` only on `Map<String, Any>` type parameters
- No duplicate tool/prompt names within a service
- Annotations must have `@Retention(RetentionPolicy.RUNTIME)`

#### Scenario: Invalid annotation usage detected at startup
```kotlin
@McpTool  // ERROR: @McpTool on non-public method
private fun search(query: String): String = ...
```
Framework logs: "@McpTool can only be applied to public methods"

#### Scenario: Context parameter with wrong type fails validation
```kotlin
@McpTool
fun analyze(
  @McpContext
  context: String  // ERROR: Should be Map<String, Any>
): String = ...
```
Framework logs: "@McpContext parameter must be Map<String, Any>, got String"


