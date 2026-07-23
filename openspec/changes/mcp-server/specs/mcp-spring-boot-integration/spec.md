# Specification: mcp-spring-boot-integration

Spring Boot auto-configuration and integration enabling declarative MCP server setup with minimal boilerplate.

## ADDED Requirements

### Requirement: Spring Boot Auto-Configuration
The framework SHALL provide auto-configuration class that registers beans and enables MCP server without manual wiring.

**Details**:
- `@Configuration` class `McpServerAutoConfiguration`
- Auto-discovered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Conditional on `aimo-mcp-server.enabled: true` property
- Creates all framework beans when enabled

#### Scenario: Auto-configuration enables MCP server
```yaml
# application.yml
aimo-mcp-server:
  enabled: true
```
When Spring Boot app starts, MCP server auto-configures and services become available automatically.

#### Scenario: Auto-configuration disables MCP server
```yaml
# application.yml
aimo-mcp-server:
  enabled: false
```
MCP server beans not created; no services available.

### Requirement: Configuration Properties Binding
Application YAML configuration SHALL bind to `McpServerProperties` class for type-safe property access.

**Details**:
- Properties class: `@ConfigurationProperties(prefix = "aimo-mcp-server")`
- Supports nested objects (transports, logging)
- Type validation (enum, boolean, int, string)
- Default values provided

#### Scenario: YAML configuration structure
```yaml
aimo-mcp-server:
  enabled: true
  transports:
    http:
      enabled: true
      port: 8080
      context-path: /mcp
    stdio:
      enabled: true
  logging:
    level: INFO
```
Framework binds to typed properties: `properties.transports.http.enabled`, `properties.transports.http.port`, etc.

### Requirement: Service Discovery and Registration
The framework SHALL discover all `@McpService` beans and register their tools/prompts automatically.

**Details**:
- Scan Spring application context after initialization
- Build `McpServiceRegistry` containing all discovered services
- Extract `@McpTool` and `@McpPrompt` methods from each service
- Generate schemas for all tools/prompts
- Make registry available to handlers

#### Scenario: Multiple services discovered
```kotlin
@McpService
class WeatherService { @McpTool fun getWeather(location: String): String = ... }

@McpService
class NewsService { @McpTool fun getNews(topic: String): String = ... }
```
Framework discovers both services; both tools available to clients.

### Requirement: Spring Component Lifecycle Integration
The framework SHALL integrate with Spring Bean lifecycle, registering HTTP endpoint via @Controller and managing startup/shutdown of transports and services.

**Details**:
- HTTP transport: Spring @Controller registered in DispatcherServlet (no standalone server)
- Stdio transport: Implements Spring's `Lifecycle` interface for graceful startup/shutdown
- HTTP handler available immediately when Spring context starts (Tomcat automatically listening)
- Stdio transport initialization on `ContextRefreshedEvent`
- Responsive to Spring shutdown for graceful connection closure

#### Scenario: HTTP transport integrated with Spring Boot
1. Spring Boot starts with embedded Tomcat
2. Auto-configuration creates `McpController` @Controller bean
3. DispatcherServlet registers `/mcp` POST endpoint
4. HTTP requests immediately routed to MCP handler
5. No separate server initialization needed

#### Scenario: Stdio transport managed by Spring lifecycle
1. Spring Boot starts
2. Auto-configuration creates stdio transport bean (implements `Lifecycle`)
3. `ContextRefreshedEvent` triggered
4. Stdio transport `start()` method called; listening on stdin
5. On shutdown: `ContextClosedEvent` → `stop()` called
6. Stdin connection closed, process exits cleanly

### Requirement: Dependency Injection Support
Services and tools SHALL be able to use all standard Spring dependency injection features.

**Details**:
- Constructor injection: `@Autowired` on constructor
- Field injection: `@Autowired` on fields
- Method injection: `@Autowired` on methods
- Qualifier resolution: `@Qualifier` on fields/parameters
- Property injection: `@Value` for environment variables and properties

#### Scenario: Tool with injected dependencies
```kotlin
@McpService
class SearchService(
  @Autowired private val searchClient: SearchClient,
  @Value("\${search.api-key}") private val apiKey: String
) {
  @McpTool
  fun search(query: String): String {
    return searchClient.search(query, apiKey)
  }
}
```
Spring injects `SearchClient` bean and property value; tool uses both.

### Requirement: Spring Events for Framework Events
The framework SHALL publish Spring events that applications can listen to and react to.

**Details**:
- `McpServerStartedEvent` — when server starts and transports listening
- `McpServerStoppedEvent` — when server stops
- `ToolExecutedEvent` — when tool completes (success or failure)
- Applications listen via `@EventListener` annotation

#### Scenario: Application listens to tool execution
```kotlin
@Component
class ToolMetrics {
  @EventListener
  fun onToolExecuted(event: ToolExecutedEvent) {
    metrics.record(event.toolName, event.executionTimeMs)
    if (event.error != null) metrics.recordError(event.toolName)
  }
}
```
Framework fires event after each tool execution; metrics component records stats.

### Requirement: Profile-Based Configuration
The framework SHALL respect Spring profiles for environment-specific configuration.

**Details**:
- Use `@Profile` on beans for profile activation
- YAML profiles: `application-{profile}.yml`
- Conditional configuration based on active profiles
- Support for multiple profiles per environment

#### Scenario: Different transports in development vs. production
```yaml
# application-dev.yml
aimo-mcp-server:
  transports:
    http:
      port: 8080
    stdio:
      enabled: true

# application-prod.yml
aimo-mcp-server:
  transports:
    http:
      port: 443
    stdio:
      enabled: false
```
In development: HTTP on 8080 + stdio. In production: HTTP on 443 only.

### Requirement: Testing Support
The framework SHALL provide test utilities and configurable test behavior.

**Details**:
- `@SpringBootTest` compatible
- Transports can be disabled in tests (mock HTTP client)
- Test fixtures: mock services, test request builder
- Integration test support with real server

#### Scenario: Unit test with mocked MCP service
```kotlin
@SpringBootTest
class ToolTest {
  @MockBean private lateinit var myService: MyService

  @Test fun testToolCall() {
    // Mocked service behaves as configured
    every { myService.search("test") } returns "result"
    // Verify tool works with mock
  }
}
```

#### Scenario: Integration test with real server
```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
class IntegrationTest {
  @LocalServerPort private var port: Int = 0

  @Test fun testToolViaHttp() {
    val response = restTemplate.postForObject(
      "http://localhost:$port/mcp",
      mapOf("jsonrpc" to "2.0", "method" to "tools/call", "params" to mapOf(...)),
      String::class.java
    )
    // Verify response
  }
}
```


