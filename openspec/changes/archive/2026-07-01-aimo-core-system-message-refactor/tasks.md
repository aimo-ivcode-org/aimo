# Tasks: aimo-core-system-message-refactor

## Implementation Tasks

- [x] Add `name` to `SystemMessageCallback` and update all implementations
- [x] Rename `ScopedSystemMessageCallbackWithName` to `ScopedSystemMessageCallback`
- [x] Update `ChatServiceEntity`, `AimoConfig`, and `ChatScopeProviderImpl` type usage
- [x] Update any helper or serialization code that assumed the wrapper carried the name

## Testing Tasks

- [x] Update and add unit tests for new callback and wrapper structure
- [x] Run focused core tests covering chat service entities and scope resolution
