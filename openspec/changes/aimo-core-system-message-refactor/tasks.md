# Tasks: aimo-core-system-message-refactor

## Implementation Tasks

- [ ] Add `name` to `SystemMessageCallback` and update all implementations
- [ ] Rename `ScopedSystemMessageCallbackWithName` to `ScopedSystemMessageCallback`
- [ ] Update `ChatServiceEntity`, `AimoConfig`, and `ChatScopeProviderImpl` type usage
- [ ] Update any helper or serialization code that assumed the wrapper carried the name

## Testing Tasks

- [ ] Update and add unit tests for new callback and wrapper structure
- [ ] Run focused core tests covering chat service entities and scope resolution
