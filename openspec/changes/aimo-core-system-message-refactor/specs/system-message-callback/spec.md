# Capability: system-message-callback

## Purpose

Normalize system message callbacks so the callback itself owns its stable name, matching how tool callbacks carry their own identity.

## Requirements

### Requirement: System message callbacks expose their own name
The system SHALL require every `SystemMessageCallback` implementation to expose a stable `name` property.

#### Scenario: A system message callback is created
- **WHEN** a `FieldSystemMessageCallback`, `MethodSystemMessageCallback`, or `PropertySystemMessageCallback` is instantiated
- **THEN** it provides a stable callback name through the interface
- **AND** downstream code can identify the callback without relying on wrapper metadata

#### Scenario: Existing code reads callback metadata
- **WHEN** chat or scope code inspects a system message callback
- **THEN** the callback name is available directly on the callback instance

### Requirement: Scoped system message wrappers carry only callback and scopes
The system SHALL represent scoped system messages with a wrapper that contains the callback and scope set, and SHALL not duplicate the callback name in the wrapper.

#### Scenario: Scoped wrapper is created
- **WHEN** a system message callback is wrapped for scoped use
- **THEN** the wrapper stores the callback instance and scopes only
- **AND** the wrapper name is resolved from the callback itself

#### Scenario: Call sites are updated to the new wrapper shape
- **WHEN** core code or tests reference the old `ScopedSystemMessageCallbackWithName` shape
- **THEN** they are updated to use the renamed wrapper and callback-owned name

