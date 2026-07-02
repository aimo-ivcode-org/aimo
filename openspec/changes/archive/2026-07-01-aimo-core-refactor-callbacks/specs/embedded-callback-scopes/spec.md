# Capability: embedded-callback-scopes

## Purpose

Embed scope restrictions directly into tool and system message callback interfaces as first-class properties, eliminating wrapper classes and simplifying callback architecture.

## Type Renames

This capability includes the following type renames for clarity and consistency:

- `AimoToolCallback` → `ToolCallback`
- `AimoToolDefinition` → `ToolDefinition`
- `MethodAimoToolCallback` → `MethodToolCallback`

These shorter names are clearer in context and consistent across the callback family.

## MODIFIED Requirements

### Requirement: Tool callbacks carry scope information
The system SHALL add a `scopes: Set<String>` property to the `ToolCallback` interface. Each tool callback must carry its scope restrictions (empty set means global tool available to all scopes).

#### Scenario: A tool callback is created with scopes
- **WHEN** a tool is discovered from a `@Tool(scope=[...])` annotation
- **THEN** the resulting `ToolCallback` includes a `scopes` property
- **AND** the `scopes` property contains the computed scope set for that tool

#### Scenario: A tool callback is used
- **WHEN** code accesses a tool callback
- **THEN** it can read the `scopes` property directly without looking up a separate map
- **AND** an empty `scopes` set indicates a global tool

### Requirement: System message callbacks carry scope information
The system SHALL add a `scopes: Set<String>` property to the `SystemMessageCallback` interface. Each system message callback must carry its scope restrictions (empty set means global message available to all scopes).

#### Scenario: A system message callback is created with scopes
- **WHEN** a system message is discovered from a `@SystemMessage(scope=[...])` annotation
- **THEN** the resulting `SystemMessageCallback` includes a `scopes` property
- **AND** the `scopes` property contains the computed scope set for that message

#### Scenario: A system message callback is used
- **WHEN** code accesses a system message callback
- **THEN** it can read the `scopes` property directly without looking up a separate map
- **AND** an empty `scopes` set indicates a global message

### Requirement: Scope computation remains unchanged
The system SHALL compute callback scopes using the existing rules (inheritance from parent `@ChatService`, intersection validation, etc.). Scope computation happens at callback creation time and is immutable.

#### Scenario: Scope validation occurs at creation
- **WHEN** a callback is constructed
- **THEN** its scopes are validated against parent service scopes
- **AND** invalid scope combinations raise errors immediately
- **AND** the computed scopes cannot be changed after construction

#### Scenario: Existing scope rules apply
- **WHEN** a tool declares scopes within a parent service
- **THEN** the computed scopes follow the existing rules (inheritance, intersection, etc.)
- **AND** behavior is identical to the previous wrapper-based approach

### Requirement: Callback discovery returns naked callbacks
The system SHALL refactor `toToolCallbacks()` and `toSystemMessageCallbacks()` in `ControllerHelpers.kt` to return callbacks with embedded scopes directly, with no wrapper layer.

#### Scenario: Discovery utilities return callbacks
- **WHEN** discovery utilities scan for `@Tool` or `@SystemMessage` annotations
- **THEN** they return `List<ToolCallback>` and `List<SystemMessageCallback>` directly
- **AND** each callback in the list has its scopes already computed and embedded
- **AND** no `ScopedToolCallback` or `ScopedSystemMessageCallback` wrappers are used

#### Scenario: Configuration beans adapt to new types
- **WHEN** Spring beans are wired for callbacks
- **THEN** `AimoConfig` provides `List<ToolCallback>` and `List<SystemMessageCallback>` beans
- **AND** downstream code receives callbacks with embedded scopes
- **AND** scope maps are built directly from the callbacks (e.g., `tools.associate { it.toolDefinition.name to it.scopes }`)

### Requirement: Wrapper classes are removed
The system SHALL remove `ScopedToolCallback` and `ScopedSystemMessageCallback` wrapper classes from `ControllerHelpers.kt`. All code that previously consumed wrapped callbacks SHALL be refactored to read scopes from callbacks directly.

#### Scenario: Wrapper classes no longer exist
- **WHEN** code is compiled after this change
- **THEN** `ScopedToolCallback` and `ScopedSystemMessageCallback` cannot be imported
- **AND** any attempt to use the old wrapper classes results in compilation error

#### Scenario: Consumers read scopes from callbacks
- **WHEN** code needs to access a tool's or message's scopes
- **THEN** it reads from `callback.scopes` directly
- **AND** no scope map lookup is required

