# Tasks: aimo-mcp-client module

## Implementation Tasks

- [ ] Create `aimo-mcp-client` module scaffolding, register in root `settings.gradle.kts`
  - Identify and add the MCP Java SDK dependency to `build.gradle.kts` — no MCP SDK is currently in the codebase; evaluate options compatible with Spring Boot 4.x (e.g. Spring AI MCP Client)
  - Module structure follows the same pattern as `aimo-model-ollama`: `api(project(":aimo-core"))` + Spring Boot starter
- [ ] Add YAML-backed MCP server configuration to `application.yml`
  - Include `aimo.mcp.discovery-interval-minutes` property (default `5`; `0` = disabled)
- [ ] Implement MCP client management: one persistent client per server, eager connect at startup, fail fast on unreachable server
- [ ] Implement startup tool discovery and in-memory caching per server
- [ ] Implement `McpSchemaConverter`: map MCP OpenRPC tool schemas to `AimoToolDefinition` (JSON Schema Draft 2020-12); fail fast on unconvertible schemas
- [ ] Apply `"{serverId}:{toolName}"` naming to all discovered tools
- [ ] Enforce MCP server scope semantics: same rules as annotated `@ChatService` beans — `[]` = unrestricted (global), named list = restricted to those scopes only; no wildcards; fail fast on unknown scope names
- [ ] Add scope validation and tool-refs validation across annotated + MCP tools
- [ ] Implement `POST /aimo-api/admin/mcp-servers/refresh` endpoint (refreshes all servers, returns per-server result, invalidates scope cache)
- [ ] Implement periodic discovery scheduler driven by `discovery-interval-minutes`

## Testing Tasks

- [ ] Unit tests for discovery, schema conversion, naming, and validation
- [ ] Unit tests for all scope config semantics (empty = unrestricted, named = restricted, unknown = fail fast)
- [ ] Unit tests for fail-fast on unreachable server and invalid schema
- [ ] Integration tests for startup discovery and refresh behavior (manual + scheduled)
- [ ] Documentation for MCP configuration, naming, scope assignment, refresh, and troubleshooting

## Example Tasks

- [ ] Add `aimo-mcp-client` dependency to `examples/simple-ollama/build.gradle.kts`
- [ ] Add example MCP server configuration to `examples/simple-ollama/src/main/resources/application.yml`
