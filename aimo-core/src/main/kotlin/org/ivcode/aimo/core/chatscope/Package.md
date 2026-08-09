
# Package org.ivcode.aimo.core.chatscope

This package implements AIMO's Chat Scope concept and the related helpers used
to define, validate, and resolve which system messages and tools are available
inside a conversation scope.

Responsibilities
----------------
- Define the Chat Scope abstraction and runtime representation used by the core
  builder and runtime to select available tools and system messages.
- Provide discovery and filtering utilities to build scope-aware lists of
  tools and system messages from configured `@ChatService` beans and MCP
  providers.
- Validate scope declarations at startup (ensure scoped members are subsets of
  their parent `@ChatService` scope and fail fast on invalid configurations).
- Expose APIs that the chat client builder and runtime use to select a
  `ChatScope` (for example a builder `withChatScope(...)` helper) and to query
  scope membership at runtime.

Key concepts
------------
- Global scope: every runtime includes a built-in `global` scope that contains
  unrestricted tools and system messages.
- Named scopes: application configuration can pre-define named scopes which
  list the tool and system-message refs that belong to the scope (`aimo.scope.*`).
- Inheritance / empty-scope semantics: a member declared with an empty scope
  inherits the parent service's scope when the parent is scoped; otherwise it
  is visible in all scopes.
- Runtime selection: callers choose the active chat scope explicitly (e.g.
  via the builder) or fall back to the global scope. `ChatScopeProvider`
  constructs the filtered lists consumers use.

Integration notes
-----------------
- Scopes are discovered from `@ChatService`-annotated beans and from MCP
  `ChatServiceProvider` instances. MCP tool/prompt names are prefixed with the
  server id (e.g. `my-server:search`).
- The package integrates with `aimo-core` configuration (see `aimo.scope.*`)
  and participates in Dokka-documented APIs used by plugins and UIs.

Operational guidance
--------------------
- Prefer explicit package-level `Module.md` files (this file) or `package.kt`
  KDoc so documentation is picked up by Dokka and by IDEs.
- When adding scopes, ensure any scoped tool or system message is listed in the
  parent service's scope and that YAML configuration matches the declared
  refs. Startup validation will catch mismatches, but keeping config and code
  consistent reduces surprises.

This package focuses on policy and visibility for tools/system messages — it
does not implement tool execution or model-specific behavior itself. Those
responsibilities live in adapter modules and the core client implementation.

