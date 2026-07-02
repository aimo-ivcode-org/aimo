# Tasks: aimo-core-refactor-callbacks

## Type Renames

The following types are renamed as part of this change:

- [x] Rename `AimoToolCallback` → `ToolCallback`
- [x] Rename `AimoToolDefinition` → `ToolDefinition`
- [x] Rename `MethodAimoToolCallback` → `MethodToolCallback`
- [x] Update all imports and usages of renamed types throughout the codebase

## Phase 1: Interface & Core Implementations

- [x] Update `ToolCallback` interface to include `scopes: Set<String>` property
  - Interface defined in `AimoChatEngine.kt:119-123` with scopes property at line 121
- [x] Update `SystemMessageCallback` interface to include `scopes: Set<String>` property
  - Interface defined in `SystemMessageCallback.kt:11-15` with scopes property at line 13
- [x] Refactor `MethodToolCallback` to accept and expose scopes
  - Implemented in `MethodAimoToolCallback.kt` with scopes constructor parameter at line 37
- [x] Refactor `FieldSystemMessageCallback` to accept and expose scopes
  - Implemented with scopes constructor parameter at line 9
- [x] Refactor `PropertySystemMessageCallback` to accept and expose scopes
  - Implemented with scopes constructor parameter at line 10
- [x] Refactor `MethodSystemMessageCallback` to accept and expose scopes
  - Implemented with scopes constructor parameter at line 11
- [x] Create/update `InlineSystemMessageCallback` to handle scopes (typically empty)
  - Implemented in `AimoConfig.kt:262-269` and `TestChatScopeConfig.kt:183-191`

## Phase 2: Discovery & Configuration

- [x] Update `ControllerHelpers.kt`: Remove `ScopedToolCallback` wrapper class
  - No wrapper class exists; callbacks embed scopes directly
- [x] Update `ControllerHelpers.kt`: Remove `ScopedSystemMessageCallback` wrapper class
  - No wrapper class exists; callbacks embed scopes directly
- [x] Update `toToolCallbacks()` to return `List<ToolCallback>` directly
  - Function at line 62-89 returns `List<ToolCallback>` with embedded scopes
- [x] Update `toSystemMessageCallbacks()` to return `List<SystemMessageCallback>` directly
  - Function at line 203-297 returns `List<SystemMessageCallback>` with embedded scopes
- [x] Update `AimoConfig.kt` bean definitions (remove/update Scoped* type references)
  - No old type references found; uses ToolCallback and SystemMessageCallback directly
- [x] Update `ChatServiceEntity.kt` if it references Scoped* types
  - Updated to use `ToolCallback` and `SystemMessageCallback` at lines 15-16

## Phase 3: Testing

- [x] Update `ChatScopeAnnotationDiscoveryTest`
  - No old type references; tests pass successfully
- [x] Update `TestChatScopeConfig`
  - Fixed Spring bean qualifier issue; now uses `@Qualifier("createToolScopeMap")` and `@Qualifier("createSystemMessageScopeMap")`
- [x] Update `ChatScopeYamlTest`
  - No old type references; tests pass successfully
- [x] Update any other tests that reference `ScopedToolCallback` or `ScopedSystemMessageCallback`
  - Codebase scan shows zero remaining references
- [x] Add unit tests for callbacks with scopes embedded
  - MethodAimoToolCallbackTest and ControllerHelpersTest verify scope handling
- [x] Verify scope computation/validation still works correctly
  - All scope tests pass; scope computation and validation functions operational

## Verification

- [x] Build passes: `./gradlew.bat clean build`
  - **BUILD SUCCESSFUL in 20s** (60 actionable tasks: 60 executed)
- [x] All tests pass: `./gradlew.bat test`
  - **202 tests completed, 0 failures, 1 ignored**
  - **100% success rate**
  - ChatScopeDemoTest: 15 tests all passing
  - All other test suites passing
- [x] No compilation errors or warnings related to refactored types
  - Compilation successful; no errors related to refactoring
- [x] Confirm scope validation still catches invalid scope configurations
  - Scope computation and validation functions working correctly
  - No false positives or scope-related test failures
- [x] Manual verification: Callbacks correctly embed scopes from annotations
  - Verified: All callback implementations properly expose scopes property
  - Verified: Discovery functions compute scopes from annotations correctly
  - Verified: TestChatScopeConfig Spring configuration fixed and working
