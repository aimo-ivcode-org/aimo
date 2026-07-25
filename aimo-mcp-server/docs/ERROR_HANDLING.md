# Error Handling Patterns and Best Practices

## Overview

This guide covers error handling patterns in `aimo-mcp-server` applications. Tools that throw exceptions are automatically converted to structured MCP error responses.

## Basic Error Handling

### Throwing Exceptions

The framework catches all exceptions from tools and converts them to MCP errors:

```kotlin
@McpTool(description = "Divide two numbers")
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

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "divide",
    "arguments": {"a": 10, "b": 0}
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32096,
    "message": "Cannot divide by zero"
  }
}
```

### Error Codes

Standard MCP error codes:

| Code | Name | Usage |
|------|------|-------|
| -32700 | PARSE_ERROR | JSON parsing failed (automatic) |
| -32600 | INVALID_REQUEST | Malformed request (automatic) |
| -32601 | METHOD_NOT_FOUND | Unknown method (automatic) |
| -32602 | INVALID_PARAMS | Invalid parameters (validation failure) |
| -32603 | INTERNAL_ERROR | Unexpected server error |
| -32099 | TOOL_NOT_FOUND | Tool doesn't exist (automatic) |
| -32098 | PROMPT_NOT_FOUND | Prompt doesn't exist (automatic) |
| -32097 | INVALID_TOOL_PARAMS | Tool parameter validation failed |
| -32096 | TOOL_EXECUTION_FAILED | Tool threw exception |

## Exception Types and Handling

### IllegalArgumentException

Use for invalid input validation:

```kotlin
@McpTool(description = "Validate email")
fun validateEmail(
    @McpParam(description = "Email address") email: String
): String {
    if (!email.contains("@")) {
        throw IllegalArgumentException("Invalid email: must contain @")
    }
    return "Valid"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "Invalid email: must contain @"
  }
}
```

### Custom Exceptions

Define domain-specific exceptions:

```kotlin
class PaymentException(message: String) : Exception(message)
class NotAuthorizedException(message: String) : Exception(message)

@McpTool(description = "Process payment")
fun processPayment(
    @McpParam(description = "Amount") amount: Double
): String {
    if (amount <= 0) {
        throw IllegalArgumentException("Amount must be positive")
    }
    
    try {
        val result = paymentService.process(amount)
        return "Payment processed: $result"
    } catch (e: PaymentException) {
        throw Exception("Payment failed: ${e.message}", e)
    }
}
```

### External Service Errors

Wrap external API errors:

```kotlin
@McpTool(description = "Fetch from API")
fun fetchData(
    @McpParam(description = "Resource ID") id: String
): String {
    return try {
        val data = apiClient.fetch(id)
        data.toString()
    } catch (e: HttpClientErrorException.NotFound) {
        throw Exception("Resource not found: $id")
    } catch (e: HttpClientErrorException) {
        throw Exception("API error: ${e.statusCode} - ${e.message}", e)
    } catch (e: Exception) {
        throw Exception("Failed to fetch resource: ${e.message}", e)
    }
}
```

## Parameter Validation

### Early Validation

Validate parameters at method entry:

```kotlin
@McpTool(description = "Create user")
fun createUser(
    @McpParam(description = "Username", required = true) username: String,
    @McpParam(description = "Email", required = true) email: String,
    @McpParam(description = "Age", required = false) age: Int?
): String {
    // Validate username
    if (username.length < 3) {
        throw IllegalArgumentException("Username must be at least 3 characters")
    }
    if (username.length > 20) {
        throw IllegalArgumentException("Username must be at most 20 characters")
    }
    
    // Validate email
    if (!email.contains("@")) {
        throw IllegalArgumentException("Invalid email format")
    }
    
    // Validate age if provided
    if (age != null && (age < 0 || age > 150)) {
        throw IllegalArgumentException("Age must be between 0 and 150")
    }
    
    // Proceed with creation
    return userService.create(username, email, age)
}
```

### Type Conversion Errors

The framework handles type conversion, but you can add custom validation:

```kotlin
@McpTool(description = "Parse date")
fun parseDate(
    @McpParam(description = "Date in YYYY-MM-DD format") dateStr: String
): String {
    return try {
        val date = LocalDate.parse(dateStr)
        "Parsed: $date"
    } catch (e: java.time.format.DateTimeParseException) {
        throw IllegalArgumentException("Invalid date format, expected YYYY-MM-DD: $dateStr")
    }
}
```

## Async and Timeout Handling

### Timeout Handling

Implement timeout protection:

```kotlin
@McpTool(description = "Long running operation")
fun longOp(
    @McpParam(description = "Duration in seconds") duration: Int
): String {
    if (duration > 300) {
        throw IllegalArgumentException("Operation timeout: max 5 minutes")
    }
    
    return try {
        val result = withTimeoutOrNull(duration.seconds) {
            expensiveOperation()
        }
        
        if (result == null) {
            throw Exception("Operation timed out after $duration seconds")
        }
        result
    } catch (e: Exception) {
        throw Exception("Long operation failed: ${e.message}", e)
    }
}
```

### Partial Failure Recovery

Handle partial failures gracefully:

```kotlin
@McpTool(description = "Batch process items")
fun batchProcess(
    @McpParam(description = "Item IDs") ids: List<String>
): String {
    val successful = mutableListOf<String>()
    val failed = mutableMapOf<String, String>()
    
    for (id in ids) {
        try {
            val result = processItem(id)
            successful.add(result)
        } catch (e: Exception) {
            failed[id] = e.message ?: "Unknown error"
        }
    }
    
    return if (failed.isEmpty()) {
        "All items processed successfully"
    } else {
        "Processed with errors: ${successful.size} succeeded, ${failed.size} failed"
    }
}
```

## Logging and Diagnostics

### Structured Logging

Log important events and errors:

```kotlin
private val logger = LoggerFactory.getLogger(javaClass)

@McpTool(description = "Process order")
fun processOrder(
    @McpParam(description = "Order ID") orderId: String,
    @McpContext context: Map<String, Any?>
): String {
    val requestId = context["requestId"]
    
    logger.info("Processing order {} for request {}", orderId, requestId)
    
    return try {
        val order = orderService.getOrder(orderId)
        logger.debug("Order details: {}", order)
        
        val result = orderService.process(order)
        logger.info("Order {} processed successfully", orderId)
        result.toString()
    } catch (e: NotFoundException) {
        logger.warn("Order not found: {}", orderId)
        throw Exception("Order $orderId not found", e)
    } catch (e: Exception) {
        logger.error("Error processing order {}: {}", orderId, e.message, e)
        throw Exception("Failed to process order: ${e.message}", e)
    }
}
```

### Debug Information

Include useful debug data in error messages:

```kotlin
@McpTool(description = "Debug calculation")
fun debugCalc(
    @McpParam(description = "Values") values: String
): String {
    return try {
        val nums = values.split(",").map { it.trim().toDouble() }
        "Sum: ${nums.sum()}, Avg: ${nums.average()}"
    } catch (e: NumberFormatException) {
        val debug = "Failed to parse: '$values', error: ${e.message}"
        logger.debug(debug)
        throw Exception("Invalid number format: ${e.message}")
    }
}
```

## Error Recovery Patterns

### Retry Logic

Implement retry for transient failures:

```kotlin
@McpTool(description = "Call remote service")
fun callService(
    @McpParam(description = "Request") request: String
): String {
    var lastException: Exception? = null
    
    for (attempt in 1..3) {
        try {
            return remoteService.call(request)
        } catch (e: TemporaryException) {
            lastException = e
            logger.warn("Attempt {} failed: {}, retrying...", attempt, e.message)
            Thread.sleep(100 * attempt.toLong())
        }
    }
    
    throw Exception("Failed after 3 attempts: ${lastException?.message}", lastException)
}
```

### Fallback Strategies

Provide fallback behavior:

```kotlin
@McpTool(description = "Get user preferences")
fun getUserPrefs(
    @McpParam(description = "User ID") userId: String
): String {
    return try {
        preferenceService.get(userId)
    } catch (e: Exception) {
        logger.warn("Failed to get preferences, using defaults: {}", e.message)
        "Default preferences"
    }
}
```

## Best Practices

### Do

✅ **Validate early**
```kotlin
if (param == null || param.isEmpty()) {
    throw IllegalArgumentException("Parameter cannot be empty")
}
```

✅ **Include context in errors**
```kotlin
throw Exception("Failed to process user $userId: ${e.message}", e)
```

✅ **Use specific exception types**
```kotlin
if (!isAuthorized()) throw UnauthorizedException("...")
if (!found) throw NotFoundException("...")
```

✅ **Log with context**
```kotlin
logger.error("Error processing {}: {}", itemId, e.message, e)
```

### Don't

❌ **Catch and ignore**
```kotlin
try { 
    risky() 
} catch (e: Exception) { 
    // Don't do this!
}
```

❌ **Generic exceptions**
```kotlin
throw Exception("Error")  // Too vague
```

❌ **Log sensitive data**
```kotlin
logger.info("User password: {}", password)  // Never!
```

❌ **Return error in success response**
```kotlin
return """{"error": "something failed"}"""  // Wrong - use thrown exception
```

## Error Response Examples

### Validation Error

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "Invalid parameters: Age must be between 0 and 150",
    "data": {
      "parameter": "age",
      "value": 200
    }
  }
}
```

### Resource Not Found

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "error": {
    "code": -32603,
    "message": "User not found: user123",
    "data": {
      "userId": "user123"
    }
  }
}
```

### Service Unavailable

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "error": {
    "code": -32603,
    "message": "External service unavailable: Connection timeout",
    "data": {
      "service": "payment-api",
      "timeout": 5000
    }
  }
}
```

## See Also

- [Annotation Reference](ANNOTATIONS.md) - Parameter validation annotations
- [Troubleshooting Guide](TROUBLESHOOTING.md) - Common problems and solutions
- [Transport Configuration](TRANSPORT_CONFIG.md) - Transport-specific error handling

