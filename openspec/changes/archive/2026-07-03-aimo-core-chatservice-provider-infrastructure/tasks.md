# Tasks: aimo-core-chatservice-provider-infrastructure

## Implementation Tasks

- [x] Add `ChatServiceProvider` (with `id`, provider-level `scopes: Set<String>`, `getTools(): List<ToolCallback>`, `getSystemMessages(): List<SystemMessageCallback>`) and `ChatServiceProviderManager` to `aimo-core`
  - Use current type names (`ToolCallback`, `SystemMessageCallback`) — not `AimoToolCallback`/`ScopedToolCallback`, which no longer exist after the `aimo-core-refactor-callbacks` change
- [x] Create `AnnotatedChatServiceProvider` to wrap annotated chat services
  - Reuse the existing `List<ChatServiceEntity>` (already assembled by `AimoConfig.createControllerEntities`) instead of re-scanning the `ApplicationContext` for `@ChatService` beans
  - Always report an empty provider-level `scopes` set (global/unrestricted at the provider level), since this single provider aggregates beans from many independently-scoped `@ChatService` classes — restriction stays entirely at the callback level, unchanged from today
- [x] Refactor `AimoConfig` to register provider beans and inject the provider manager
  - Keep existing static tool/message beans (`createToolCallbacks()`, `createSystemMessageCallbacks()`, etc.) unchanged; do not remove them in this change
- [x] Refactor `ChatScopeProviderImpl` to build scopes dynamically from providers and scope filters
  - Filter callbacks using the two-condition AND: provider-level `scopes` allows the requested scope id AND the callback's own `scopes` allows it (empty set = allows all)
- [x] Update `ChatScope` to hold references to its contributing providers and its individually-defined callbacks; when tools or system messages are requested, query the providers for their current output and return the combined result
  - `AimoChatScopeProperties` (inheritGlobal, toolRefs, systemMessageRefs, inline system messages) is static YAML config applied by the scope-building layer — it does not need to be stored on `ChatScope`
  - Note: `ChatClientBuilderFactoryImpl` holds `toolCallbacks`/`systemMessages` constructor params but does not use them — all tools are delivered through the scope provider, so no change to those static lists is required
  - This changes `ChatScope` from a plain `data class` with fixed lists to a class with dynamically-resolved tools/system messages; update all direct constructions of `ChatScope(...)` in existing tests (`ChatScopeDemoTest`, `ChatScopeProviderInterceptorTest`, `TestChatScopeConfig`, `ChatScopeAnnotationDiscoveryTest`) to match the new shape

## Testing Tasks

- [x] Unit tests for provider aggregation and annotated-provider wrapping
- [x] Unit tests for dynamic scope filtering against provider-supplied callbacks, including provider-level `scopes` vs callback-level `scopes` combinations (both empty, only one restricted, both restricted to same/different scope ids)
- [x] Regression tests for existing chat scope behavior
