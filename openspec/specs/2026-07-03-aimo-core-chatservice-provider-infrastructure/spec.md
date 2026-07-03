# Chatservice Provider Infrastructure

## Purpose

## Requirements

### Requirement: Chat service providers are first-class core abstractions
The system SHALL define a `ChatServiceProvider` abstraction in `aimo-core` that exposes provider identity, a provider-level `scopes: Set<String>` restriction, tool callbacks (`ToolCallback`, which already carry their own `scopes`), and system message callbacks (`SystemMessageCallback`, which already carry their own `scopes`).

An empty provider-level `scopes` set means the provider itself is unrestricted (global): its callbacks are then gated only by each callback's own `scopes`. A non-empty provider-level `scopes` set means the provider only contributes callbacks when building one of those specific scopes, regardless of the individual callback's own `scopes`.

#### Scenario: A provider is queried
- **WHEN** core code inspects a provider
- **THEN** it can read the provider id and the provider's own `scopes` set
- **AND** it can obtain tool callbacks and system message callbacks (each already carrying their own `scopes`) from the same provider interface

### Requirement: Annotated chat services are wrapped as providers
The system SHALL wrap annotated `@ChatService` beans in a single `AnnotatedChatServiceProvider` so annotated tools and system messages are exposed through the provider abstraction, reusing the existing bean discovery and callback assembly (`ChatServiceEntity`, `toToolCallbacks`, `toSystemMessageCallbacks`) rather than re-implementing discovery.

Because a single `AnnotatedChatServiceProvider` aggregates beans from many independently-scoped `@ChatService` classes, it SHALL always report an empty provider-level `scopes` set (global/unrestricted at the provider level). Per-scope restriction for annotated tools and system messages continues to be enforced entirely through each callback's own `scopes`, exactly as today.

#### Scenario: An annotated chat service is discovered
- **WHEN** Spring discovers a `@ChatService` bean
- **THEN** the bean's callbacks are represented through the single `AnnotatedChatServiceProvider`
- **AND** its annotated callbacks remain available through the provider API, unchanged from their current scope behavior
- **AND** the provider's own `scopes` set is empty

### Requirement: Core manages providers through a provider manager
The system SHALL collect all chat service providers in a `ChatServiceProviderManager` and SHALL use that manager as the source of truth for provider-backed callbacks.

#### Scenario: Multiple providers exist
- **WHEN** multiple providers are registered in Spring
- **THEN** the manager can return the full provider set
- **AND** scope-building code can query the current providers from a single place

### Requirement: Chat scope construction is dynamic
The system SHALL build chat scopes so that tools and system messages are resolved from the current provider state at runtime, rather than being frozen at startup.

`ChatScope` SHALL hold references to its contributing providers and its individually-defined callbacks (tools and system messages added directly to the scope rather than sourced from a provider). When tools or system messages are needed, the scope resolves them by combining the current outputs of its providers with its individual definitions, applying scope filtering to both.

A provider's or callback's `scopes` set "allows" a requested scope id when the set is empty (unrestricted/global) or when the set contains that scope id.

#### Scenario: A chat scope is built
- **WHEN** `ChatScopeProviderImpl` builds a scope for a requested scope id
- **THEN** it queries the provider manager for current callbacks
- **AND** it includes a callback only when **both** the provider's scope set allows the requested scope id AND the callback's own scope set allows the requested scope id (two-condition AND; either condition alone is not sufficient)

#### Scenario: Providers change over time
- **WHEN** the provider set changes between requests (e.g. an MCP server refreshes its tool list)
- **THEN** the next access to the scope's tools or system messages reflects the updated provider state without requiring a restart

#### Scenario: Individually-defined callbacks coexist with provider-sourced callbacks
- **WHEN** a scope has both provider-sourced callbacks and individually-defined callbacks (tools or system messages added directly to the scope)
- **THEN** both sources are included when the scope resolves its available tools and system messages

### Requirement: Callbacks inherit parent @ChatService scopes when not explicitly scoped
The system SHALL apply scope inheritance rules when a `@Tool` or `@SystemMessage` annotation does not explicitly specify a scope.

When a callback is created during annotation processing:
- If the callback's declared `scope` is empty (not specified): the callback inherits all scopes from its parent `@ChatService`
- If the callback's declared `scope` is non-empty: the system SHALL validate that all declared scopes are contained within the parent's scopes (subset rule), and reject the annotation if any scope is outside the parent's scopes

This ensures that:
- A callback with no explicit scope automatically works in all of the parent service's scopes
- A callback scoped to a specific subset of parent scopes is valid only if that subset is contained within the parent
- A callback cannot declare a scope that doesn't exist in the parent (this is an error at build time)

#### Scenario: Tool has no scope and parent is scoped
- **WHEN** a `@Tool` method has no `scope` attribute (or `scope=[]`) and its parent `@ChatService` has `scope=["admin", "research"]`
- **THEN** the tool callback is created with scopes `["admin", "research"]`
- **AND** the tool is available in both "admin" and "research" scopes at runtime

#### Scenario: System message has no scope and parent is scoped
- **WHEN** a `@SystemMessage` method has no `scope` attribute (or `scope=[]`) and its parent `@ChatService` has `scope=["admin"]`
- **THEN** the system message callback is created with scopes `["admin"]`
- **AND** the system message is only available in the "admin" scope at runtime

#### Scenario: Callback scope is subset of parent scope
- **WHEN** a `@Tool` specifies `scope=["admin"]` and its parent `@ChatService` has `scope=["admin", "research", "public"]`
- **THEN** the tool callback is created with scopes `["admin"]` (the intersection)
- **AND** the tool is only available in the "admin" scope, not in "research" or "public"

#### Scenario: Callback scope contains value outside parent scope
- **WHEN** a `@Tool` specifies `scope=["superadmin"]` but its parent `@ChatService` has `scope=["admin", "research"]`
- **THEN** the annotation is rejected with an error
- **AND** the build fails

#### Scenario: Provider-level scope validation
- **WHEN** a provider (such as an MCP server adapter) has non-empty `scopes: Set<String>` and contains callbacks
- **THEN** all callbacks in that provider MUST have scopes that are subsets of the provider's scopes
- **AND** an empty callback scope `[]` means the callback inherits all of the provider's scopes
- **AND** a callback scope outside the provider's scopes causes a validation error
