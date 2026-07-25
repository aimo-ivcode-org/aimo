# Mcp Server Annotation Framework

## Purpose

Declarative annotation-based framework for defining MCP services, tools, and prompts in Spring applications.

## Requirements

### Requirement: Core Annotations
The framework SHALL provide five core annotations that developers can use to declare MCP services, tools, and prompts with full Spring integration.

**Details**: Framework provides five core annotations:
1. `@McpService` — Marks a Spring bean as MCP service provider (optional: `id`, `name`, `description`)
2. `@McpTool` — Marks public methods as LLM-callable tools (optional: `name`, `description`, `category`)
3. `@McpPrompt` — Marks public methods as MCP prompts returning String (optional: `name`, `description`)
4. `@McpParam` — Documents method parameters (optional: `description`, `example`, `required`)
5. `@McpContext` — Auto-injects request context into `Map<String, Any>` parameters

#### Scenario: Define a simple MCP service with one tool
- **WHEN** developer creates class with `@McpService` and public method with `@McpTool` annotation
- **THEN** framework discovers service, registers tool, and generates schema for client discovery

#### Scenario: Tool with context parameter
- **WHEN** tool method has parameter with `@McpContext` annotation of type `Map<String, Any>`
- **THEN** framework automatically injects request context; parameter is excluded from generated schema

### Requirement: Service Discovery
The framework SHALL automatically discover all `@McpService` beans during Spring application startup and register them as available MCP services.

**Details**: 
- Classpath scanning via Spring's component discovery
- Bean registration in Spring context
- All `@McpTool` and `@McpPrompt` methods extracted and indexed
- Services remain available throughout application lifetime

#### Scenario: Auto-discovery in multi-service application
- **WHEN** Spring Boot application starts with multiple `@McpService` classes
- **THEN** framework automatically discovers and registers all services without manual configuration

#### Scenario: Tool extraction for schema generation
- **WHEN** service is discovered and registered
- **THEN** framework extracts all `@McpTool` methods and generates OpenRPC schemas for client discovery

### Requirement: Metadata Extraction
The framework SHALL extract metadata from annotations and method signatures to support schema generation and documentation.

**Details**:
- Method inspection: name, return type, parameters, types, annotations
- Documentation from: explicit annotations, JavaDoc comments, `@McpParam` descriptions
- Type information used for schema generation and runtime validation
- JavaDoc serves as fallback description source

#### Scenario: Generate documentation from JavaDoc
- **WHEN** tool method has JavaDoc comment but no explicit `@McpTool(description=...)`
- **THEN** framework extracts description from JavaDoc and includes in schema

#### Scenario: Prioritize explicit annotation over JavaDoc
- **WHEN** tool method has both explicit `@McpTool(description=...)` and JavaDoc comment
- **THEN** framework uses explicit annotation value as description, ignoring JavaDoc

### Requirement: Annotation Validation
The framework SHALL validate all annotations at application startup, failing fast with clear error messages for invalid usage.

**Details**:
- `@McpService` only on classes
- `@McpTool` and `@McpPrompt` only on public methods in `@McpService` classes
- `@McpContext` only on `Map<String, Any>` type parameters
- No duplicate tool/prompt names within a service
- Annotations must have `@Retention(RetentionPolicy.RUNTIME)`

#### Scenario: Invalid annotation usage detected at startup
- **WHEN** `@McpTool` annotation is applied to private method
- **THEN** framework detects invalid usage at application startup and logs error "@McpTool can only be applied to public methods"

#### Scenario: Context parameter with wrong type fails validation
- **WHEN** `@McpContext` annotation is used on parameter not of type `Map<String, Any>`
- **THEN** framework detects type mismatch at startup and logs error "@McpContext parameter must be Map<String, Any>, got [actualType]"
