# Design: aimo-core-chatservice-provider-infrastructure

## Approach

Add a provider layer between discovered chat-service beans and scope construction.

The design will:

1. Define `ChatServiceProvider` in `aimo-core` as the common abstraction for anything that contributes tools or system messages. It exposes an `id`, a provider-level `scopes: Set<String>` (empty = unrestricted), and `getTools(): List<ToolCallback>` / `getSystemMessages(): List<SystemMessageCallback>` (both callback types already carry their own embedded `scopes`, per the existing `chatservice` callback interfaces).
2. Wrap annotated `@ChatService` beans in `AnnotatedChatServiceProvider` so their callbacks are exposed through the same interface as future adapter-backed providers. It reuses the existing `ChatServiceEntity` discovery/assembly (already built by `AimoConfig`) instead of re-scanning the `ApplicationContext`, and always reports an empty provider-level `scopes` (since it aggregates many independently-scoped `@ChatService` classes — restriction stays entirely at the callback level, unchanged from today).
3. Collect all providers in a `ChatServiceProviderManager` so scope building can query the current provider set at runtime.
4. Refactor `AimoConfig` to expose provider beans in addition to the existing static callback lists (which remain, unchanged, for other consumers such as `ChatClientBuilderFactoryImpl`).
5. Refactor `ChatScopeProviderImpl` to build scopes dynamically from the provider manager, filtering callbacks by **both** the provider's own `scopes` and the callback's own `scopes` (see capability spec for the exact two-condition AND semantics).

This keeps core independent from any specific adapter module while making future dynamic discovery possible.

## Components Affected

`aimo-core` chat-service interfaces (`ToolCallback`, `SystemMessageCallback` — note: not `AimoToolCallback`, which was renamed in the prior `aimo-core-refactor-callbacks` change), provider manager, annotated service wrapper, Spring configuration, scope provider implementation, and tests around scope resolution and callback assembly.

`ChatScope` is currently a plain `data class` with fixed `tools`/`systemMessages` lists, constructed directly in several existing tests (`ChatScopeDemoTest`, `ChatScopeProviderInterceptorTest`, `TestChatScopeConfig`, `ChatScopeAnnotationDiscoveryTest`). Making it resolve `tools`/`systemMessages` dynamically from providers is a breaking shape change to those call sites (likely moving from stored properties to computed accessors, and losing simple data-class equality), not just an internal refactor — implementation tasks must account for updating those tests.

## Trade-offs

The refactor introduces an extra abstraction layer and changes how scope data is assembled, but it removes the static-list bottleneck and creates a clean seam for adapter modules.
