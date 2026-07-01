## Why

The current core runtime assembles annotated tools and system messages as static lists, which makes it hard to introduce provider-based sources of callbacks and impossible to rebuild scopes dynamically when tool sets change. A provider abstraction is needed before MCP tool consuming can be wired in cleanly.

## What Changes

Introduce a `ChatServiceProvider` abstraction in `aimo-core`, wrap annotated chat services in a provider implementation, add a provider manager, and refactor `AimoConfig` and `ChatScopeProvider` to build scopes from providers at runtime instead of fixed callback lists.

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

- `chatservice-provider-infrastructure`

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

- `chat-scope-building`
- `chat-service-callback-assembly`

## Impact

Affected areas include `ChatServiceProvider`, `ChatServiceProviderManager`, `AnnotatedChatServiceProvider`, `AimoConfig`, `ChatScopeProviderImpl`, scope filtering behavior, and tests that currently assume static tool and system message lists.
