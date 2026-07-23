## Why

The AIMO project needs a robust, Spring-based framework for building MCP (Model Context Protocol) servers. Currently, building MCP servers requires low-level JSON-RPC handling and transport management. By creating a declarative, annotation-driven framework, developers can focus on business logic rather than protocol mechanics, and servers built with this framework can be discovered and consumed by `aimo-mcp-client`.

## What Changes

- New standalone module `aimo-mcp-server` with annotation-based tool/prompt declaration (`@McpService`, `@McpTool`, `@McpPrompt`)
- Spring Boot auto-configuration for MCP server setup with minimal boilerplate
- Multi-transport support: HTTP, SSE, and stdio for client connections
- Automatic OpenRPC schema generation from annotated methods
- JSON-RPC protocol handling and tool invocation routing
- Comprehensive error handling with structured responses
- Request context injection for tools via `@McpContext`

## Capabilities

### New Capabilities
- `mcp-annotation-framework`: Declarative `@McpService`, `@McpTool`, `@McpPrompt` annotations for tool/prompt definition
- `mcp-schema-generation`: Automatic OpenRPC schema generation from annotated methods
- `mcp-transport-support`: HTTP, SSE, and stdio transports for client connections
- `mcp-request-handling`: JSON-RPC protocol handling and tool invocation routing
- `mcp-error-handling`: Structured error reporting, validation, and fault tolerance
- `mcp-spring-boot-integration`: Spring Boot auto-configuration and component discovery

### Modified Capabilities
<!-- None at this stage; this is a new module with no existing capabilities -->

## Impact

- **New module**: `aimo-mcp-server` — standalone, no dependency on aimo-core (but follows same patterns)
- **New dependencies**: MCP Java SDK, Jackson, Spring Boot
- **Future integration**: `aimo-mcp-client` will discover and consume servers built with this framework
- **Examples**: Reference MCP server implementations will be created (e.g., research, web search, file access)
