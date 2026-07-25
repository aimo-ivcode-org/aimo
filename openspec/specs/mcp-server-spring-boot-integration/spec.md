# Mcp Server Spring Boot Integration

## Purpose

Spring Boot auto-configuration and integration enabling declarative MCP server setup with minimal boilerplate.

## Requirements

### Requirement: Spring Boot Auto-Configuration
The framework SHALL provide auto-configuration class that registers beans and enables MCP server without manual wiring.

**Details**:
- `@Configuration` class `McpServerAutoConfiguration`
- Auto-discovered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Conditional on `aimo-mcp-server.enabled: true` property
- Creates all framework beans when enabled

#### Scenario: Auto-configuration enables MCP server
- **WHEN** application.yml sets `aimo-mcp-server.enabled: true`
- **THEN** Spring Boot auto-configuration registers MCP server beans and services become available

#### Scenario: Auto-configuration disables MCP server
- **WHEN** application.yml sets `aimo-mcp-server.enabled: false`
- **THEN** Spring Boot auto-configuration skips bean creation and MCP server is not available

### Requirement: Configuration Properties Binding
Application YAML configuration SHALL bind to `McpServerProperties` class for type-safe property access.

**Details**:
- Properties class: `@ConfigurationProperties(prefix = "aimo-mcp-server")`
- Supports nested objects (transports, logging)
- Type validation (enum, boolean, int, string)
- Default values provided

#### Scenario: YAML configuration structure
- **WHEN** application.yml contains nested transport and logging configuration under `aimo-mcp-server` prefix
- **THEN** framework binds configuration to typed properties with full nesting (transports.http.port, transports.stdio.enabled, logging.level)

### Requirement: Service Discovery and Registration
The framework SHALL discover all `@McpService` beans and register their tools/prompts automatically.

**Details**:
- Scan Spring application context after initialization
- Build `McpServiceRegistry` containing all discovered services
- Extract `@McpTool` and `@McpPrompt` methods from each service
- Generate schemas for all tools/prompts
- Make registry available to handlers

#### Scenario: Multiple services discovered
- **WHEN** Spring application context contains multiple `@McpService` beans
- **THEN** framework discovers all services and registers all their tools and prompts

### Requirement: Spring Component Lifecycle Integration
The framework SHALL integrate with Spring Bean lifecycle, registering HTTP endpoint via @Controller and managing startup/shutdown of transports and services.

**Details**:
- HTTP transport: Spring @Controller registered in DispatcherServlet (no standalone server)
- Stdio transport: Implements Spring's `Lifecycle` interface for graceful startup/shutdown
- HTTP handler available immediately when Spring context starts (Tomcat automatically listening)
- Stdio transport initialization on `ContextRefreshedEvent`
- Responsive to Spring shutdown for graceful connection closure

#### Scenario: HTTP transport integrated with Spring Boot
- **WHEN** Spring Boot application starts with embedded Tomcat
- **THEN** auto-configuration registers McpController and MCP HTTP endpoint is immediately available for requests

#### Scenario: Stdio transport managed by Spring lifecycle
- **WHEN** Spring application context refreshes (ContextRefreshedEvent)
- **THEN** stdio transport starts listening; on shutdown (ContextClosedEvent) it gracefully closes stdin connection

### Requirement: Dependency Injection Support
Services and tools SHALL be able to use all standard Spring dependency injection features.

**Details**:
- Constructor injection: `@Autowired` on constructor
- Field injection: `@Autowired` on fields
- Method injection: `@Autowired` on methods
- Qualifier resolution: `@Qualifier` on fields/parameters
- Property injection: `@Value` for environment variables and properties

#### Scenario: Tool with injected dependencies
- **WHEN** `@McpService` class has constructor parameters with `@Autowired` and `@Value` annotations
- **THEN** Spring injects beans and properties; tool can use injected dependencies

### Requirement: Spring Events for Framework Events
The framework SHALL publish Spring events that applications can listen to and react to.

**Details**:
- `McpServerStartedEvent` — when server starts and transports listening
- `McpServerStoppedEvent` — when server stops
- `ToolExecutedEvent` — when tool completes (success or failure)
- Applications listen via `@EventListener` annotation

#### Scenario: Application listens to tool execution
- **WHEN** tool is executed and completes (successfully or with error)
- **THEN** framework publishes ToolExecutedEvent that application can listen to via @EventListener

### Requirement: Profile-Based Configuration
The framework SHALL respect Spring profiles for environment-specific configuration.

**Details**:
- Use `@Profile` on beans for profile activation
- YAML profiles: `application-{profile}.yml`
- Conditional configuration based on active profiles
- Support for multiple profiles per environment

#### Scenario: Different transports in development vs. production
- **WHEN** Spring profile activates (dev or prod) with matching application-{profile}.yml
- **THEN** framework applies profile-specific configuration (HTTP port, stdio enabled/disabled)

### Requirement: Testing Support
The framework SHALL provide test utilities and configurable test behavior.

**Details**:
- `@SpringBootTest` compatible
- Transports can be disabled in tests (mock HTTP client)
- Test fixtures: mock services, test request builder
- Integration test support with real server

#### Scenario: Unit test with mocked MCP service
- **WHEN** test uses `@SpringBootTest` with `@MockBean` for service
- **THEN** Spring provides test context with mocked service

#### Scenario: Integration test with real server
- **WHEN** test uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@LocalServerPort`
- **THEN** Spring starts real server on random port and test can send HTTP requests to localhost
