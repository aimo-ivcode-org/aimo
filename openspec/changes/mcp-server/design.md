# Design: mcp-server

## Approach

### Architecture Overview

`aimo-mcp-server` is a Spring Boot framework for building MCP servers using declarative annotations. It:

1. **Discovers annotated components** at startup via Spring's classpath scanning
2. **Generates OpenRPC schemas** from `@McpTool` and `@McpPrompt` method signatures
3. **Routes JSON-RPC requests** to handler methods based on `tools/call` and `prompts/get` methods
4. **Manages transports** — HTTP, SSE, stdio — transparently to developers
5. **Provides request context** to tools (user input, session state, etc.)
6. **Handles errors gracefully** with structured MCP error responses

### Key Design Decisions

1. **Annotation-Driven**: `@McpService` marks a Spring bean as an MCP service container; `@McpTool` and `@McpPrompt` mark methods as MCP callables
2. **Schema Auto-Generation**: Framework inspects method signatures (parameters, return types, JavaDoc) and generates OpenRPC schemas automatically
3. **No Core Dependency**: Standalone module; no imports from aimo-core. Follows AIMO patterns but is independently useful
4. **Pattern Compatibility**: Annotation naming and structure mirrors aimo-core (`@Tool` → `@McpTool`, etc.)
5. **Transport Abstraction**: Single service code works with HTTP, SSE, or stdio via pluggable transports
6. **Graceful Error Handling**: Tool invocation errors return structured MCP error responses; framework continues running
7. **Spring Integration**: Leverages Spring's lifecycle, dependency injection, and configuration management

### Core Components

1. **Annotations** (`@McpService`, `@McpTool`, `@McpPrompt`, `@McpParam`, `@McpContext`)
   - `@McpService` — Marks a class as an MCP service provider
   - `@McpTool` — Marks a method as an MCP tool (callable by LLM)
   - `@McpPrompt` — Marks a method as an MCP prompt (template/workflow)
   - `@McpParam` — Documents tool parameters (descriptions, type info)
   - `@McpContext` — Auto-injects request context into tool methods

2. **Schema Generator** (`McpSchemaGenerator`)
   - Inspects `@McpTool` and `@McpPrompt` methods
   - Extracts parameter metadata, return types, JavaDoc descriptions
   - Generates OpenRPC-compliant schemas
   - Validates schemas for consistency

3. **Service Discovery** (`McpServiceRegistry`)
   - Scans Spring application context for `@McpService` beans
   - Collects all `@McpTool` and `@McpPrompt` methods
   - Builds schema catalog

4. **Transport Layer** (`McpTransport` interface + implementations)
   - HTTP transport — Registered as Spring @Controller with Spring's embedded Tomcat (no standalone server)
   - Stdio transport — Spring `Lifecycle` bean for stdin/stdout local connections
   - Framework leverages Spring's server infrastructure, not standalone socket servers

5. **Request Handler** (`McpRequestHandler`)
   - Parses JSON-RPC requests
   - Routes to appropriate handler (tool call, prompt get, etc.)
   - Manages tool invocation + error handling
   - Returns JSON-RPC responses

6. **Configuration** (`McpServerProperties`, Spring auto-config)
   - YAML-based transport setup
   - Service discovery options
   - Error handling policies

### Implementation Strategy

**Phase 1: Foundations**
- Define annotations and framework interfaces
- Implement schema generator
- Create service registry

**Phase 2: Request Handling**
- Implement MCP JSON-RPC protocol handler
- Route requests to annotated methods
- Basic error handling

**Phase 3: Transports**
- HTTP transport as Spring @Controller registered in DispatcherServlet
- Stdio transport as Spring `Lifecycle` bean for graceful startup/shutdown
- Request routing and Spring lifecycle integration

**Phase 4: Spring Integration**
- Auto-configuration (`@EnableMcpServer`)
- Component discovery and lifecycle
- Properties and YAML binding

**Phase 5: Documentation & Examples**
- Reference MCP server implementations
- Configuration guide
- Integration with aimo-mcp-client

## Components Affected

- **New**: `aimo-mcp-server` module (standalone)
- **Future Integration**: `aimo-mcp-client` will discover servers built with this framework
- **Examples**: Reference MCP server implementations in `examples/` (e.g., `examples/mcp-server-research`)
- **Documentation**: Integration guide for connecting servers to AIMO apps

## Trade-offs

1. **No Core Dependency vs. Code Duplication**
   - Trade-off: Standalone module avoids circular dependencies and keeps MCP server logic independent
   - Cost: May duplicate some patterns from aimo-core (annotation handling, schema generation)
   - Rationale: Allows MCP servers to exist and work outside AIMO ecosystem; AIMO consumes them via aimo-mcp-client

2. **Reflection-Based Discovery vs. Compilation-Time Processing**
   - Trade-off: Runtime reflection for component discovery (matches Spring patterns, developer familiarity)
   - Cost: Slightly slower startup, larger reflection footprint
   - Rationale: Consistency with Spring Boot conventions; future optimization via APT possible

3. **Single Service vs. Multi-Service Support**
   - Trade-off: Each `@McpService` bean is independent; can have multiple services in one app
   - Cost: No built-in aggregation across services (each service has its own schema)
   - Rationale: Keeps design simple; multi-service apps can route manually or via transport layer

4. **Eager Initialization vs. Lazy Discovery**
   - Trade-off: Services and schemas generated at startup (eager)
   - Cost: Slower startup if many services; can't add tools dynamically at runtime
   - Rationale: Predictable behavior, fail-fast validation, consistent with AIMO patterns

5. **Framework Error Handling vs. Developer Control**
   - Trade-off: Framework catches and reports exceptions as MCP errors
   - Cost: Less flexibility for custom error policies
   - Rationale: Ensures server stays responsive; developers can customize via error handlers
