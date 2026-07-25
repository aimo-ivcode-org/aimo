# Troubleshooting Guide

## Common Issues and Solutions

### Build and Startup Issues

#### Module not found in settings.gradle.kts

**Problem:** Build fails with "Could not find project :aimo-mcp-server"

**Solution:**
1. Verify `aimo-mcp-server` is included in `settings.gradle.kts`:
   ```kotlin
   include("aimo-mcp-server")
   ```
2. Run `./gradlew.bat clean :aimo-mcp-server:build`

#### Missing Spring dependencies

**Problem:** ClassNotFoundException for Spring classes at runtime

**Solution:**
- Ensure your application includes Spring Boot starter dependencies
- `aimo-mcp-server` expects Spring Boot 4.x
- Check `build.gradle.kts` has `spring-boot-starter-webmvc`

#### Auto-configuration not enabled

**Problem:** Tools/prompts not discovered; no `/mcp` endpoints available

**Solution:**
- Add `@EnableMcpServer` annotation to your Spring Boot configuration class
- Alternatively, add to `application.yml`:
  ```yaml
  spring:
    autoconfigure:
      exclude: []  # Don't exclude MCP auto-configuration
  ```

### Service Discovery Issues

#### No services discovered

**Problem:** Logs show "0 services, 0 tools" even though you have `@McpService` classes

**Causes and Solutions:**
1. **Service not a Spring bean**
   - Ensure the class is discovered by Spring component scanning
   - Wrong: `class MyService { ... }`
   - Right: `@McpService class MyService { ... }`
2. **Service not in component scan path**
   - Ensure service is in package scanned by Spring
   - Check `@ComponentScan` configuration
   - Default: scans from application class package downward

3. **Service not public**
   - `@McpService` must be on public class
   - Check class visibility

**Debug:**
```bash
# Enable debug logging
logging:
  level:
    org.ivcode.aimo.server.mcp: DEBUG
```

Check logs for:
```
[DEBUG] Discovering @McpService bean: myServiceBeanName
[DEBUG] Registered tool: myService:toolName
```

#### Tools not appearing

**Problem:** Service discovered but tools not registered

**Causes and Solutions:**
1. **Methods not public**
   - `@McpTool` only works on public methods
   - Wrong: `private fun myTool(): String`
   - Right: `fun myTool(): String`

2. **Wrong annotation**
   - Verify method has `@McpTool` (not `@Tool`, not misspelled)

3. **Invalid return type**
   - Return type should be serializable
   - Avoid: complex nested generics, raw types
   - OK: String, Int, List<String>, Map<String, Any>, data classes

4. **Invalid parameter types**
   - Stick to basic types: String, Int, Double, Boolean, List, Map
   - Complex types may cause schema generation errors

**Debug:**
```kotlin
@McpService
class DebugService {
    @McpTool(description = "Test tool")
    fun testTool(
        @McpParam(description = "Test param") input: String
    ): String {
        println("Tool called with: $input")
        return "Success: $input"
    }
}
```

Then test via HTTP:
```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'
```

### Request Handling Issues

#### Tools not callable

**Problem:** `tools/list` shows tool but `tools/call` returns "tool not found"

**Solution:**
- Ensure tool name in request matches exactly (case-sensitive)
- Tool names are: `[bean-name]:[method-name]`
- Example: request `{"name": "myService:add"}` for service named `myService`

#### Parameter validation errors

**Problem:** Tool returns "Invalid parameters" error

**Causes and Solutions:**
1. **Missing required parameters**
   - All parameters are required by default
   - Mark optional with `@McpParam(required = false)`
   - Provide default values in method signature

2. **Type mismatch**
   - Parameter types must match method signature
   - String → can accept any value (converted to string)
   - Double → must be numeric or numeric string
   - Boolean → must be true/false or "true"/"false"

3. **Wrong parameter names**
   - Parameter names must match method parameter names exactly
   - Java reflection: parameter names available from bytecode
   - Ensure compiled with `-parameters` flag (default in Gradle)

**Fix:**
```kotlin
// Wrong - won't accept optional parameter
@McpTool
fun search(query: String, limit: Int): String { ... }

// Right - parameters are now optional
@McpTool
fun search(
    @McpParam(description = "Query", required = true) query: String,
    @McpParam(description = "Limit", required = false) limit: Int = 10
): String { ... }
```

#### Tool execution fails

**Problem:** `tools/call` returns error, logs show exception

**Causes and Solutions:**
1. **NullPointerException**
   - Tool accessed null parameter or dependency
   - Add null checks: `val param = params ?: throw IllegalArgumentException("Required")`

2. **IOException or network error**
   - Tool tried external API and failed
   - Wrap in try-catch, return meaningful error

3. **Dependency injection failed**
   - Tool depends on Spring bean that wasn't autowired
   - Verify bean exists and is public
   - Check `@Autowired` or constructor injection

**Best practice:**
```kotlin
@McpTool(description = "Call external API")
fun callApi(
    @McpParam(description = "API key") apiKey: String
): String {
    return try {
        val result = externalService.query(apiKey)
        result.toString()
    } catch (e: Exception) {
        throw IllegalArgumentException("API call failed: ${e.message}", e)
    }
}
```

### Transport Issues

#### HTTP endpoint returns 404

**Problem:** `POST /mcp/` returns 404

**Causes and Solutions:**
1. **Application not running**
   - Check logs for startup errors
   - Verify application started successfully

2. **Wrong URL**
   - Default path is `/mcp/`
   - If configured differently, check `application.yml`:
     ```yaml
     aimo:
       mcp:
         transports:
           http:
             basePath: "/mcp"  # or custom path
     ```

3. **Port mismatch**
   - Default Spring Boot port: 8080
   - Custom port in `application.yml`:
     ```yaml
     server:
       port: 9090
     ```

4. **HTTP transport disabled**
   - Check `application.yml`:
     ```yaml
     aimo:
       mcp:
         transports:
           http:
             enabled: true  # Must be true
     ```

#### SSE connection times out

**Problem:** SSE connection closes after 5 minutes

**Solution:**
- This is expected behavior - timeout is configurable:
  ```yaml
  aimo:
    mcp:
      transports:
        sse:
          connectionTimeout: 600000  # 10 minutes
  ```

#### Stdio transport not responding

**Problem:** Stdio transport enabled but application doesn't read stdin

**Causes and Solutions:**
1. **Not configured**
   - Ensure `aimo.mcp.transports.stdio.enabled: true`

2. **Stdin redirected**
   - Check if application runs with stdin connected
   - Avoid: `java -jar app.jar < /dev/null`
   - Use: `java -jar app.jar` (with terminal)

3. **Line buffering**
   - Ensure input ends with newline
   - JSON-RPC requests must be one per line

**Test:**
```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | java -jar app.jar
```

### Configuration Issues

#### Wrong logging level

**Problem:** Can't see debug information

**Solution:**
```yaml
logging:
  level:
    org.ivcode.aimo.server.mcp: DEBUG
```

#### Configuration not loaded

**Problem:** Changes to `application.yml` not applied

**Solutions:**
1. Rebuild application (configuration embedded in JAR)
2. Pass configuration via environment:
   ```bash
   java -Daimo.mcp.enabled=true -jar app.jar
   ```
3. External configuration file:
   ```bash
   java -jar app.jar --spring.config.location=file:./application.yml
   ```

### Integration Issues (with aimo-mcp-client)

#### Server not discovered by aimo-mcp-client

**Problem:** aimo-mcp-client fails to connect to MCP server

**Causes and Solutions:**
1. **URL incorrect**
   - Verify server URL in client configuration
   - Test directly: `curl http://localhost:8080/mcp/tools/list`

2. **Server offline**
   - Check server is running
   - Check network connectivity

3. **aimo-mcp-client not configured**
   - Add server to `application.yml`:
     ```yaml
     aimo:
       mcp:
         servers:
           - id: "my-server"
             url: "http://localhost:8080/mcp"
     ```

4. **Required flag preventing startup**
   - Set `required: false` to allow graceful degradation:
     ```yaml
     aimo:
       mcp:
         required: false
         servers:
           - id: "my-server"
             url: "http://localhost:8080/mcp"
     ```

#### Scope visibility issues

**Problem:** Tools available but not visible in certain scopes

**Solution:**
- Check scope configuration matches between server and client
- Server config:
  ```yaml
  servers:
    - id: "my-server"
      scope: ["admin", "research"]  # Only in these scopes
  ```
- Client must use matching scope

#### Tool names prefixed incorrectly

**Problem:** Tool is `server:my-tool` but expected `my-tool`

**Explanation:**
- This is expected behavior - server ID is prepended to avoid collisions
- Multiple servers can have tools with same name
- Use full name: `server-id:tool-name`

### Memory and Performance Issues

#### High memory usage

**Problem:** Application consumes excessive memory

**Causes and Solutions:**
1. **Many large tools/prompts**
   - Schemas are cached in memory
   - Consider breaking into multiple services

2. **Thread pool too large**
   - Check Spring thread pool configuration
   - May not be MCP-specific

**Monitor:**
```bash
jps  # Find Java process
jmap -heap [PID]  # Heap usage
```

#### Slow startup

**Problem:** Application takes too long to start

**Causes and Solutions:**
1. **Many services to discover**
   - Reduce number of scanned packages:
     ```yaml
     aimo:
       mcp:
         discovery:
           basePackages: "com.mycompany.mcp"
     ```

2. **Expensive service initialization**
   - Delay initialization: use `@Lazy`
   - Load resources lazily in tool methods

## Getting Help

If you encounter issues not covered here:

1. **Enable debug logging** (see above)
2. **Check server logs** for stack traces
3. **Verify configuration** against examples
4. **Test tool directly** via HTTP before integrating
5. **Create minimal reproduction** to isolate issue

## Common Error Messages

| Message | Cause | Fix |
|---------|-------|-----|
| "Tool not found" | Tool name doesn't exist or wrong format | Use `tools/list` to see available tools |
| "Invalid parameters" | Missing required param or type mismatch | Check parameter names and types match schema |
| "Cannot infer type" | Generic type without bounds | Specify concrete type |
| "No MCP services found" | No `@McpService` beans discovered | Check `@Component` + `@McpService` + package path |
| "Unresolved reference" | Annotation not imported | Add `import org.ivcode.aimo.server.mcp.annotation.*` |


