# Capability: chatservice-provider-infrastructure

## Purpose

Provide a core provider abstraction for chat services so annotated callbacks and future adapter-backed callbacks can be assembled uniformly and scope-built dynamically.

## ADDED Requirements

### Requirement: Chat service providers are first-class core abstractions
The system SHALL define a `ChatServiceProvider` abstraction in `aimo-core` that exposes provider identity, provider scopes, tool callbacks, and system message callbacks.

#### Scenario: A provider is queried
- **WHEN** core code inspects a provider
- **THEN** it can read the provider id and provider scopes
- **AND** it can obtain scoped tool callbacks and scoped system message callbacks from the same provider interface

### Requirement: Annotated chat services are wrapped as providers
The system SHALL wrap annotated `@ChatService` beans in an `AnnotatedChatServiceProvider` so annotated tools and system messages are exposed through the provider abstraction.

#### Scenario: An annotated chat service is discovered
- **WHEN** Spring discovers a `@ChatService` bean
- **THEN** the bean is represented as a `ChatServiceProvider`
- **AND** its annotated callbacks remain available through the provider API

### Requirement: Core manages providers through a provider manager
The system SHALL collect all chat service providers in a `ChatServiceProviderManager` and SHALL use that manager as the source of truth for provider-backed callbacks.

#### Scenario: Multiple providers exist
- **WHEN** multiple providers are registered in Spring
- **THEN** the manager can return the full provider set
- **AND** scope-building code can query the current providers from a single place

### Requirement: Chat scope construction is dynamic
The system SHALL build chat scopes so that tools and system messages are resolved from the current provider state at runtime, rather than being frozen at startup.

`ChatScope` SHALL hold references to its contributing providers and its individually-defined callbacks (tools and system messages added directly to the scope rather than sourced from a provider). When tools or system messages are needed, the scope resolves them by combining the current outputs of its providers with its individual definitions, applying scope filtering to both.

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

