# Annotation Reference Guide

This guide documents all annotations available in `aimo-mcp-server` for building MCP services.

## @McpService

Marks a Spring bean as an MCP service provider.

### Usage

```kotlin
@McpService
class MyCalculator {
    // All public @McpTool and @McpPrompt methods are discovered
}
```

### Properties

None. The annotation is a marker.

### Behavior

- The class must be a Spring bean (e.g., `@Component`, `@Service`, or defined in `@Bean` method)
- All public methods annotated with `@McpTool` or `@McpPrompt` are automatically discovered at startup
- Private and protected methods are ignored
- The service instance is available from the application context

### Scopes

`@McpService` does not support scope restrictions. All tools/prompts from a service are available globally unless individually restricted.

### Example

```kotlin
@McpService
class WeatherService {
    @McpTool
    fun getWeather(city: String): String { ... }
    
    @McpPrompt
    fun getHelp(): String { ... }
}
```

## @McpTool

Marks a method as a callable MCP tool.

### Usage

```kotlin
@McpTool(
    name = "my-tool",           // Optional; defaults to method name
    description = "What it does"  // Optional
)
fun myTool(
    @McpParam(description = "First param") param1: String,
    param2: Int = 10              // Default values supported
): String {
    return "result"
}
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | String | Method name | Custom tool name (should be lowercase, dash-separated) |
| `description` | String | Empty | Human-readable description of what the tool does |

### Behavior

- Method return type becomes the tool result
- Method parameters become the tool input schema
- Return type should be serializable (String, primitives, data classes, etc.)
- Exceptions thrown are caught and converted to MCP errors
- No parameters means no input schema

### Type Support

The framework automatically generates schemas for:

| Type | OpenRPC Type |
|------|--------------|
| `String` | `string` |
| `Int`, `Integer` | `integer` |
| `Long` | `integer` |
| `Double`, `Float` | `number` |
| `Boolean` | `boolean` |
| `List<*>`, `Collection<*>` | `array` |
| `Map<*,*>` | `object` |
| Other | `string` (serialized) |

### Examples

**Simple addition:**
```kotlin
@McpTool(description = "Add two numbers")
fun add(
    @McpParam(description = "First number") a: Double,
    @McpParam(description = "Second number") b: Double
): Double = a + b
```

**Tool with optional parameters:**
```kotlin
@McpTool(description = "Search the web")
fun search(
    @McpParam(description = "Search query", required = true) query: String,
    @McpParam(description = "Max results", required = false) limit: Int = 10
): String { ... }
```

**Tool with context:**
```kotlin
@McpTool(description = "Get user profile")
fun getUserProfile(
    @McpParam(description = "User ID") userId: String,
    @McpContext context: Map<String, Any?>
): String {
    val requestId = context["requestId"]
    // ...
}
```

## @McpPrompt

Marks a method as an MCP prompt (reusable template).

### Usage

```kotlin
@McpPrompt(
    name = "my-prompt",                    // Optional; defaults to method name
    description = "What this prompt does"   // Optional
)
fun myPrompt(
    @McpParam(description = "Topic") topic: String = "all"
): String {
    return "Prompt text for $topic"
}
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | String | Method name | Custom prompt name (should be lowercase, dash-separated) |
| `description` | String | Empty | Human-readable description of the prompt's purpose |

### Behavior

- Signature must be `() -> String?` or `(context) -> String?`
- Return value is the prompt text
- Can accept parameters via `@McpParam` (optional)
- Parameters are similar to tool parameters
- Prompts are templates for system messages or user instructions

### Valid Signatures

```kotlin
// No parameters
@McpPrompt
fun getHelp(): String? { ... }

// With parameters
@McpPrompt
fun getTutorial(topic: String): String? { ... }

// With context
@McpPrompt
fun getPrompt(@McpContext context: Map<String, Any?>): String? { ... }

// With both parameters and context
@McpPrompt
fun getPrompt(
    topic: String,
    @McpContext context: Map<String, Any?>
): String? { ... }
```

### Examples

**Simple help prompt:**
```kotlin
@McpPrompt(description = "Get calculator help")
fun help(): String = """
    # Calculator Help
    
    Available operations:
    - add(a, b)
    - subtract(a, b)
    - multiply(a, b)
    - divide(a, b)
""".trimIndent()
```

**Parameterized prompt:**
```kotlin
@McpPrompt(description = "Get tutorial for topic")
fun tutorial(
    @McpParam(description = "Tutorial topic", required = true) topic: String
): String {
    return when(topic.lowercase()) {
        "addition" -> "Addition combines two numbers..."
        "subtraction" -> "Subtraction finds the difference..."
        else -> "Unknown topic: $topic"
    }
}
```

## @McpParam

Documents a tool/prompt parameter.

### Usage

```kotlin
@McpTool
fun myTool(
    @McpParam(description = "User name", required = true)
    name: String
): String { ... }
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `description` | String | Empty | Human-readable description of the parameter |
| `required` | Boolean | `true` | Whether the parameter is mandatory |

### Behavior

- Applies to method parameters
- Description appears in generated OpenRPC schema
- Required parameters must be provided in requests
- Optional parameters can be omitted (if method allows)
- Type is inferred from the parameter's type annotation

### Examples

**Required parameter:**
```kotlin
@McpParam(description = "The user ID", required = true)
userId: String
```

**Optional parameter:**
```kotlin
@McpParam(description = "Maximum results (default: 10)", required = false)
limit: Int = 10
```

**No annotation (still required by default):**
```kotlin
// Parameter 'query' is required, no description in schema
query: String
```

## @McpContext

Injects request context into a tool/prompt method.

### Usage

```kotlin
@McpTool
fun myTool(
    @McpParam(description = "User input") input: String,
    @McpContext context: Map<String, Any?>
): String {
    val requestId = context["requestId"]
    val toolName = context["toolName"]
    // ...
}
```

### Properties

None. The annotation is a marker.

### Behavior

- Injects a `Map<String, Any?>` containing request context
- Context is automatically excluded from input schema
- Only one `@McpContext` parameter per method allowed
- Context keys vary by transport and request type

### Available Context Keys

| Key | Type | Description |
|-----|------|-------------|
| `requestId` | String? | JSON-RPC request ID |
| `toolName` | String | Name of the tool being invoked |
| `promptName` | String | Name of the prompt being invoked |

### Examples

**Log request information:**
```kotlin
@McpTool(description = "Expensive operation")
fun expensiveOp(
    @McpContext context: Map<String, Any?>
): String {
    val requestId = context["requestId"]
    logger.info("Processing request: $requestId")
    // ...
}
```

**Usage tracking:**
```kotlin
@McpTool
fun search(
    @McpParam(description = "Search query") query: String,
    @McpContext context: Map<String, Any?>
): String {
    val requestId = context["requestId"]
    val toolName = context["toolName"]
    metrics.increment("tool.calls", mapOf("tool" to toolName))
    // ...
}
```

## Annotation Combinations

### Valid Combinations

```kotlin
// Tool with parameters and context
@McpTool(name = "my-tool", description = "Does something")
fun process(
    @McpParam(description = "Input") input: String,
    @McpContext context: Map<String, Any?>
): String { ... }

// Prompt with optional parameters
@McpPrompt(name = "tutorial", description = "Learn about topics")
fun tutorial(
    @McpParam(description = "Topic", required = false) topic: String = "all"
): String { ... }

// Multiple parameters
@McpTool(description = "Complex operation")
fun complex(
    @McpParam(description = "Param 1", required = true) param1: String,
    @McpParam(description = "Param 2", required = true) param2: Int,
    @McpParam(description = "Param 3", required = false) param3: Boolean = false,
    @McpContext context: Map<String, Any?>
): String { ... }
```

### Invalid Combinations

```kotlin
// ❌ Tool without @McpService - won't be discovered
@McpTool
fun tool(): String { ... }

// ❌ Multiple @McpContext parameters
@McpTool
fun tool(
    @McpContext ctx1: Map<String, Any?>,
    @McpContext ctx2: Map<String, Any?>  // ERROR!
): String { ... }

// ❌ Prompt with invalid signature
@McpPrompt
fun prompt(name: String, age: Int): String { ... }  // Too many params

// ❌ Private tool - won't be discovered
@McpService
class MyService {
    @McpTool
    private fun privateTool(): String { ... }  // Private - ignored
}
```

## Best Practices

### Naming

- Use lowercase, dash-separated names for tools/prompts (e.g., `web-search`, `get-help`)
- Avoid special characters except hyphens
- Be descriptive but concise

```kotlin
@McpTool(name = "web-search")  // Good
fun search(): String { ... }

@McpTool(name = "ws")          // Avoid - too cryptic
fun search(): String { ... }
```

### Documentation

- Always include descriptions for tools and parameters
- Keep descriptions concise and clear
- Use imperative verbs for tools (e.g., "Search the web", "Get user info")

```kotlin
@McpTool(description = "Search the web for information")  // Good
fun search(
    @McpParam(description = "Search query") query: String
): String { ... }

@McpTool  // Bad - no description
fun s(q: String): String { ... }
```

### Parameters

- Make parameters required unless they have sensible defaults
- Provide descriptions for all parameters
- Use type names that match the OpenRPC schema

```kotlin
@McpTool
fun fetch(
    @McpParam(description = "URL to fetch", required = true) url: String,
    @McpParam(description = "Timeout in seconds", required = false) timeout: Int = 30
): String { ... }
```

### Error Handling

- Throw meaningful exceptions in tools (they become MCP errors)
- Include context in error messages
- Validate parameters early

```kotlin
@McpTool
fun divide(
    @McpParam(description = "Dividend") a: Double,
    @McpParam(description = "Divisor") b: Double
): String {
    if (b == 0.0) {
        throw IllegalArgumentException("Cannot divide by zero")
    }
    return (a / b).toString()
}
```

## See Also

- [Framework README](../README.md) - Overview and quick start
- [Transport Configuration](TRANSPORT_CONFIG.md) - Transport setup details
- [Integration with aimo-mcp-client](AIMO_INTEGRATION.md) - Using servers with AIMO

