## Why

`SystemMessageCallback` currently has no stable name on the callback itself, which forces the name onto the scoped wrapper type. That is inconsistent with `AimoToolCallback`, makes callback metadata awkward to reason about, and blocks the provider-based refactor needed for MCP tool consuming.

## What Changes

Add `name` to `SystemMessageCallback`, update the concrete system message callback implementations to provide names, and rename the scoped wrapper so it only carries the callback plus scopes.

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

- `system-message-callback`

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

- `chat-service-callbacks`

## Impact

Affected files include `SystemMessageCallback.kt`, `FieldSystemMessageCallback.kt`, `MethodSystemMessageCallback.kt`, `PropertySystemMessageCallback.kt`, `ControllerHelpers.kt`, `ChatServiceEntity.kt`, `AimoConfig.kt`, `ChatScopeProviderImpl.kt`, and the test suite references that still use `ScopedSystemMessageCallbackWithName`.
