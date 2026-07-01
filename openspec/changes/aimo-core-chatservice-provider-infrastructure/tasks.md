# Tasks: aimo-core-chatservice-provider-infrastructure

## Implementation Tasks

- [ ] Add `ChatServiceProvider` and `ChatServiceProviderManager` to `aimo-core`
- [ ] Create `AnnotatedChatServiceProvider` to wrap annotated chat services
- [ ] Refactor `AimoConfig` to register provider beans and inject the provider manager
  - Keep existing static tool/message beans (`createToolCallbacks()`, `createScopedToolCallbacks()`, etc.) unchanged; do not remove them in this change
- [ ] Refactor `ChatScopeProviderImpl` to build scopes dynamically from providers and scope filters
- [ ] Update `ChatScope` to hold references to its contributing providers and its individually-defined callbacks; when tools or system messages are requested, query the providers for their current output and return the combined result
  - `AimoChatScopeProperties` (inheritGlobal, toolRefs, systemMessageRefs, inline system messages) is static YAML config applied by the scope-building layer — it does not need to be stored on `ChatScope`
  - Note: `ChatClientBuilderFactoryImpl` holds `toolCallbacks`/`systemMessages` constructor params but does not use them — all tools are delivered through the scope provider, so no change to those static lists is required

## Testing Tasks

- [ ] Unit tests for provider aggregation and annotated-provider wrapping
- [ ] Unit tests for dynamic scope filtering against provider-supplied callbacks
- [ ] Regression tests for existing chat scope behavior
