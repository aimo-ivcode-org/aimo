## Why

Currently, scope information is stored separately from callbacks via wrapper classes (`ScopedToolCallback`, `ScopedSystemMessageCallback`). This creates unnecessary indirection since annotations like `@Tool(scope=[...])` already carry scope metadata. The callbacks representing those tools should inherently carry their scope restrictions, eliminating the wrapper layer and simplifying the overall callback architecture.

## Type Renames

This change also renames several types for clarity and consistency:

- `AimoToolCallback` → `ToolCallback`
- `AimoToolDefinition` → `ToolDefinition`
- `MethodAimoToolCallback` → `MethodToolCallback`

These shorter names are clearer within context and align better with the callback naming family.

## What Changes

Move scope metadata (`scopes: Set<String>`) from wrapper data classes into the `ToolCallback` and `SystemMessageCallback` interfaces. This makes scope information an integral part of each callback, not a separate wrapper. Eliminate `ScopedToolCallback` and `ScopedSystemMessageCallback` wrapper classes. Update callback implementations to carry scopes directly, and refactor all code that reads from or constructs callbacks to work with the new structure.

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

- `embedded-callback-scopes` (built-in scopes on callback interfaces)

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

- `chat-service-callback-assembly` (callbacks now carry scope info)

## Impact

Affects all callback implementations (`MethodToolCallback`, `FieldSystemMessageCallback`, `PropertySystemMessageCallback`, `MethodSystemMessageCallback`), callback discovery utilities in `ControllerHelpers.kt`, `AimoConfig` bean setup, and all tests that work with callbacks. The wrapper classes are removed entirely. No external API changes since this is internal refactoring. This change is a prerequisite for the `chatservice-provider-infrastructure` change, which will use callbacks with embedded scopes.
