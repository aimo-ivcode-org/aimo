# Design: aimo-core-refactor-callbacks

**Note:** This change is a prerequisite for `chatservice-provider-infrastructure`. It refactors callback interfaces to carry scope information directly, enabling providers to return naked callbacks without wrapper layers.

## Type Renames

As part of this refactoring, the following types are renamed for clarity and consistency:

| Old Name | New Name | Reason |
|----------|----------|--------|
| `AimoToolCallback` | `ToolCallback` | Shorter, clearer; "Tool" is sufficient context |
| `AimoToolDefinition` | `ToolDefinition` | Consistent with tool callback rename |
| `MethodAimoToolCallback` | `MethodToolCallback` | Consistent with interface rename |

## Approach

### 1. Update Callback Interfaces

Add `scopes: Set<String>` property to the base callback interfaces:

- `ToolCallback` (in `aimo-core/.../model/AimoChatEngine.kt`)
  ```kotlin
  interface ToolCallback {
      val toolDefinition: ToolDefinition
      val scopes: Set<String>  // NEW: scope restrictions
      fun call(argumentsJson: String, context: Map<String, Any>): String
  }
  ```

- `SystemMessageCallback` (in `aimo-core/.../chatservice/SystemMessageCallback.kt`)
  ```kotlin
  interface SystemMessageCallback {
      val name: String
      val scopes: Set<String>  // NEW: scope restrictions
      fun call(context: SystemMessageContext): String?
  }
  ```

### 2. Update All Callback Implementations

- `MethodAimoToolCallback`: Accept `scopes` in constructor, expose as property
- `FieldSystemMessageCallback`: Accept `scopes` in constructor, expose as property
- `PropertySystemMessageCallback`: Accept `scopes` in constructor, expose as property
- `MethodSystemMessageCallback`: Accept `scopes` in constructor, expose as property
- `InlineSystemMessageCallback`: Accept `scopes` (typically empty for inline messages)

### 3. Refactor Discovery Logic

Update `ControllerHelpers.kt`:
- `toToolCallbacks()`: Compute scopes, pass to `MethodToolCallback` constructor. Return `List<ToolCallback>` directly (no wrapper).
- `toSystemMessageCallbacks()`: Compute scopes, pass to callback constructors. Return `List<SystemMessageCallback>` directly (no wrapper).

### 4. Remove Wrapper Classes

Delete from `ControllerHelpers.kt`:
- `data class ScopedToolCallback`
- `data class ScopedSystemMessageCallback`

### 5. Update Configuration

Update `AimoConfig.kt`:
- Remove all `Scoped*Callback` imports and bean definitions
- Beans now return `List<ToolCallback>` and `List<SystemMessageCallback>` directly
- Build scope maps directly from callbacks: `tools.associate { it.toolDefinition.name to it.scopes }`



## Components Affected

**Core Interfaces:**
- `ToolCallback` (add scopes property)
- `SystemMessageCallback` (add scopes property)

**Implementations:**
- `MethodToolCallback`
- `FieldSystemMessageCallback`, `PropertySystemMessageCallback`, `MethodSystemMessageCallback`
- `InlineSystemMessageCallback`

**Discovery & Configuration:**
- `ControllerHelpers.kt` (remove wrappers, update utilities)
- `AimoConfig.kt` (update bean definitions that reference Scoped* types)
- `ChatServiceEntity.kt` (may be updated if it references Scoped* types)

**Tests:**
- All tests using `ScopedToolCallback` or `ScopedSystemMessageCallback`
- Tests in `ChatScopeYamlTest`, `ChatScopeAnnotationDiscoveryTest`, `TestChatScopeConfig`, etc.

## Trade-offs

**Pros:**
- Simpler interface: No wrapper classes to track
- Clearer semantics: Callbacks inherently carry scope metadata
- Easier to understand provider pattern: Providers return naked callbacks
- Less allocations: No intermediate wrapper objects

**Cons:**
- Callback interface changes require updates to all implementations
- Tests need refactoring to work with updated interfaces
- Slightly larger memory per callback (one additional `Set<String>` field)

**Mitigation:**
- Scope sets are typically small (1-3 elements) or empty (global tools)
- Constructor pattern keeps creation code centralized
- Benefits (clarity, reduced indirection) outweigh small performance cost
