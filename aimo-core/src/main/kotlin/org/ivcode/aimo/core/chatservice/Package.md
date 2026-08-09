# Package org.ivcode.aimo.core.chatservice

Tool and system message discovery, registration, and lifecycle management for AIMO.

This package implements the annotation-driven service discovery system that allows applications
to expose tools (LLM-callable functions) and system messages (dynamic prompts) to the chat client.

Responsibilities
----------------
- Discover and register `@ChatService` beans via reflection at application startup
- Parse and validate `@Tool` and `@SystemMessage` annotations on service members
- Generate JSON schemas for tool parameters from method signatures and `@ToolParam` documentation
- Provide `ChatServiceProvider` interface for both local services and remote (MCP) tool/prompt sources
- Support scope-aware filtering: ensure scoped tools/messages are subsets of parent service scopes
- Coordinate with the chat scope system to filter available tools/messages at runtime
- Handle special parameter injection: `context: Map<String, Any>` is auto-injected and excluded from schemas
- Manage tool callback lifecycle: reflection-based method binding, parameter marshalling, error handling

Key Concepts
------------
- **Annotation-driven discovery**: Services are decorated with `@ChatService`, tools with `@Tool`, 
  messages with `@SystemMessage`. Reflection discovers and registers them at startup.
- **Tool callbacks**: `ToolCallback` represents an LLM-callable tool with name, description, 
  parameter schema, and a closure to invoke the underlying method.
- **System message callbacks**: `SystemMessageCallback` represents a dynamic prompt: either 
  a constant string or a function `() -> String?` or `(SystemMessageContext) -> String?`.
- **Scope inheritance**: Empty `scope = []` on a tool/message inherits the parent `@ChatService` scope; 
  non-empty scopes must be subsets of parent scope.
- **Parameter context injection**: Methods can declare a `context: Map<String, Any>` parameter 
  that receives request context (chatId, requestId, conversation) automatically.
- **Provider abstraction**: `ChatServiceProvider` unifies local `@ChatService` reflection 
  (via `AnnotatedChatServiceProvider`) and remote tool/prompt sources (e.g., MCP servers).

Integration Notes
-----------------
- The configuration module (`aimo-core/conf/AimoConfig.kt`) discovers all `@ChatService` beans 
  and registers them with `ChatServiceProviderRegistry`.
- Scopes are validated at startup: tools and messages are checked to ensure their scopes 
  are valid subsets of their parent service scopes.
- Tool names and system message names can be prefixed (e.g., `"my-server:search"` for MCP tools) 
  to avoid collisions across multiple providers.
- Scope filtering happens at the chat client builder level: `ChatScopeProvider` constructs 
  filtered lists of tools/messages based on the selected scope.

Operational Guidance
--------------------
- When adding a new service, use `@ChatService` on the class and `@Tool`/`@SystemMessage` on members.
- Document tool parameters with `@ToolParam("description")` so generated JSON schemas are user-friendly.
- If a tool needs request context (chatId, userId, etc.), declare a `context: Map<String, Any>` 
  parameter—it will be auto-injected.
- Use non-empty scopes only when you need to restrict visibility; prefer empty scopes 
  (which inherit from parent) for simplicity.
- Startup validation catches most mistakes; review build logs for scope conflict warnings.
- MCP tool/prompt names follow the pattern `"{serverId}:{toolName}"` to avoid collisions.

This package focuses on tool/message registration and discovery, not execution. Tool execution 
happens in the chat client loop; message rendering happens at prompt construction time.


