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

None. `chat-scope-building` and `chat-service-callback-assembly` have no baseline spec in `openspec/specs/` (that directory is currently empty) and no prior change ever added them, so they cannot be "modified" here. The dynamic scope-building behavior that would have been described under those names is instead captured as ADDED requirements under the new `chatservice-provider-infrastructure` capability below.

## Impact

Affected areas include `ChatServiceProvider`, `ChatServiceProviderManager`, `AnnotatedChatServiceProvider`, `AimoConfig`, `ChatScopeProviderImpl`, scope filtering behavior, and tests that currently assume static tool and system message lists. Note: `ChatScope` currently is a plain `data class` constructed directly (with fixed `tools`/`systemMessages` lists) in several existing tests (`ChatScopeDemoTest`, `ChatScopeProviderInterceptorTest`, `TestChatScopeConfig`, `ChatScopeAnnotationDiscoveryTest`); making it resolve dynamically from providers is a breaking shape change to those call sites, not just an internal refactor.
