# Tasks: mcp-server

## Implementation Tasks

### Phase 1: Foundations
- [ ] Create `aimo-mcp-server` module with `build.gradle.kts` and directory structure
- [ ] Define core annotations: `@McpService`, `@McpTool`, `@McpPrompt`, `@McpParam`, `@McpContext`
- [ ] Implement `McpSchemaGenerator` — converts method signatures to OpenRPC schemas
- [ ] Implement `McpServiceRegistry` — discovers `@McpService` beans and builds schema catalog
- [ ] Create basic request/response domain classes (MCP JSON-RPC protocol objects)

### Phase 2: Request Handling
- [ ] Implement `McpRequestHandler` — routes JSON-RPC requests to handlers
- [ ] Implement tool call handler — invokes `@McpTool` methods with parameter binding
- [ ] Implement prompt get handler — invokes `@McpPrompt` methods
- [ ] Implement resource list/read handlers (stub for Phase 2)
- [ ] Add error handling — structured MCP error responses
- [ ] Add parameter validation — type checking, required fields, etc.

### Phase 3: Transports
- [ ] Create `McpTransport` interface — abstract transport layer
- [ ] Implement HTTP transport — REST endpoint for JSON-RPC requests
- [ ] Implement SSE transport — Server-Sent Events for streaming connections
- [ ] Implement stdio transport — stdin/stdout for local connections
- [ ] Add transport selection logic — pick transport based on configuration

### Phase 4: Spring Integration
- [ ] Create `McpServerProperties` class with `@ConfigurationProperties`
- [ ] Implement Spring Boot auto-configuration class (`@EnableMcpServer`)
- [ ] Create component discovery (scan classpath for `@McpService` beans)
- [ ] Implement service lifecycle management (startup, shutdown)
- [ ] Add YAML configuration support — transports, service discovery options

### Phase 5: Documentation & Examples
- [ ] Write framework architecture documentation
- [ ] Create example MCP server (e.g., "weather-service" or "calculator")
- [ ] Document annotation usage and patterns
- [ ] Document transport configuration (YAML examples)
- [ ] Create integration guide for connecting to `aimo-mcp-client`

## Testing Tasks

### Unit Tests
- [ ] Test `McpSchemaGenerator` — verify schema generation for various method signatures
- [ ] Test parameter binding — validate parameter extraction and type conversion
- [ ] Test error handling — verify structured error responses
- [ ] Test annotation processing — verify service/tool/prompt discovery
- [ ] Test request routing — verify requests route to correct handlers

### Integration Tests
- [ ] Test HTTP transport — verify request/response flow over HTTP
- [ ] Test SSE transport — verify streaming connection and message delivery
- [ ] Test stdio transport — verify stdin/stdout communication
- [ ] Test Spring integration — verify auto-configuration and bean discovery
- [ ] Test tool invocation end-to-end — full request → tool call → response flow
- [ ] Test prompt invocation end-to-end
- [ ] Test YAML configuration loading and transport selection
- [ ] Test multi-service scenarios (multiple `@McpService` beans in app)

### Example Tests
- [ ] Build and run example MCP server
- [ ] Test example server with MCP test client
- [ ] Verify example server integrates with `aimo-mcp-client`

## Documentation Tasks
- [ ] Write README with feature overview and quick start
- [ ] Document all annotations and their usage
- [ ] Create transport configuration guide
- [ ] Write troubleshooting guide
- [ ] Document error handling patterns
- [ ] Create patterns/best practices guide
