# Design: aimo-core-system-message-refactor

## Approach

Move the system message name from the scoped wrapper onto the callback itself so all callback metadata lives with the callback implementation.

The refactor will:

1. Add `val name: String` to `SystemMessageCallback`.
2. Update `FieldSystemMessageCallback`, `MethodSystemMessageCallback`, and `PropertySystemMessageCallback` to expose a stable name.
3. Rename `ScopedSystemMessageCallbackWithName` to `ScopedSystemMessageCallback` and remove the duplicate `name` field from the wrapper.
4. Update all type references so chat/service and scope-building code consume the new structure.
5. Update tests and any serialization or helper code that previously expected the wrapper to carry the name.

## Components Affected

`aimo-core` callback interfaces, controller helper wrappers, chat service entities, scope provider logic, configuration wiring, and tests.

## Trade-offs

This is a small but breaking API cleanup. It simplifies the model and aligns system messages with tools, but it requires coordinated updates across core code and tests.
