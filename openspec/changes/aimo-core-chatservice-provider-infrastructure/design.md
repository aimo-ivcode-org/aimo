# Design: aimo-core-chatservice-provider-infrastructure

## Approach

Add a provider layer between discovered chat-service beans and scope construction.

The design will:

1. Define `ChatServiceProvider` in `aimo-core` as the common abstraction for anything that contributes tools or system messages.
2. Wrap annotated `@ChatService` beans in `AnnotatedChatServiceProvider` so their callbacks are exposed through the same interface as future adapter-backed providers.
3. Collect all providers in a `ChatServiceProviderManager` so scope building can query the current provider set at runtime.
4. Refactor `AimoConfig` to expose provider beans instead of only static callback lists.
5. Refactor `ChatScopeProviderImpl` to build scopes dynamically from the provider manager and filter callbacks by scope at build time.

This keeps core independent from any specific adapter module while making future dynamic discovery possible.

## Components Affected

`aimo-core` chat-service interfaces, provider manager, annotated service wrapper, Spring configuration, scope provider implementation, and tests around scope resolution and callback assembly.

## Trade-offs

The refactor introduces an extra abstraction layer and changes how scope data is assembled, but it removes the static-list bottleneck and creates a clean seam for adapter modules.
