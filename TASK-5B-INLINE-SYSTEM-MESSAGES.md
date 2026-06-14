# Task 5b Details: Inline System Message Callbacks (NEW)
# PLUS Task 5c: System Message Naming Registry (NEW)
# PLUS Task 5d: Scope Inheritance & Validation (NEW)

## Design Change 1: Support System Messages Defined in YAML

**Problem**: System messages discovered via `@SystemMessage` are identified by array index (fragile). User needs ability to define custom system messages per scope in YAML configuration.

**Solution**: Add `system-messages: Map<String, String>` field to `AimoChatScopeProperties` for inline system message definitions.

## Design Change 2: Named System Messages with Registry (NEW)

**Problem**: How do we reference system messages? Array indices are fragile (break if order changes).

**Solution**: 
1. Add `name: String = ""` property to `@SystemMessage` annotation
2. If name is blank: auto-generate based on method/field name
3. Build a registry at startup: `systemMessageName → SystemMessageCallback`
4. Detect conflicts fail-fast (same as tools)
5. YAML references use meaningful system message names, not indices

## Design Change 3: Scope Inheritance & Validation (NEW)

**Problem**: How do nested scopes work? Can a tool live in scopes not declared on its parent service?

**Solution**: 
1. `@ChatService.scope` applies to entire class (parent scope)
2. `@Tool.scope` and `@SystemMessage.scope` must be valid subsets:
   - Empty = inherit parent all scopes
   - Non-empty = compute intersection with parent
   - Zero intersection = ERROR
   - Scope outside parent = ERROR
3. Fail-fast validation at startup

## Annotation Changes

```kotlin
@ChatService(scope = ["admin", "research"])  // Parent scope for this class
class AnalysisService {
    
    @Tool(scope = ["research"])  // Subset: OK (intersection = ["research"])
    fun analyzeData() { ... }
    
    @Tool(scope = [])  // Empty: OK (inherits ["admin", "research"])
    fun help() { ... }
    
    @Tool(scope = ["public"])  // ERROR: "public" not in parent ["admin", "research"]
    fun publicHelp() { ... }
    
    @SystemMessage(name = "research_guide", scope = ["research"])
    fun researchGuide(): String = "..."
}
```

## Scope Intersection Rules

| Parent Scope | Tool Scope | Result | Status |
|---|---|---|---|
| ["admin", "research"] | [] | ["admin", "research"] | ✅ inherit |
| ["admin", "research"] | ["admin"] | ["admin"] | ✅ intersection |
| ["admin", "research"] | ["research"] | ["research"] | ✅ intersection |
| ["admin", "research"] | ["admin", "research"] | ["admin", "research"] | ✅ intersection |
| ["admin", "research"] | ["public"] | empty | ❌ ERROR |
| ["admin", "research"] | ["admin", "public"] | ❌ ERROR |  "public" outside parent |
| [] (empty parent) | ["admin"] | ["admin"] | ✅ tool scopes apply |

## Discovery & Registry

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/ControllerHelpers.kt`

**New logic in `toAimoToolCallbacks(controller, objectMapper, parentServiceScopes)`**:
```kotlin
// For each @Tool method:
val declaredScopes = toolAnnotation.scope.toSet()
val actualScopes = if (declaredScopes.isEmpty()) {
    parentServiceScopes  // inherit parent
} else {
    val intersection = declaredScopes.intersect(parentServiceScopes)
    require(intersection.isNotEmpty()) {
        "Tool '${method.name}' scopes $declaredScopes have zero intersection " +
        "with parent service scopes $parentServiceScopes"
    }
    require(declaredScopes.all { it in parentServiceScopes }) {
        "Tool '${method.name}' has scopes not in parent service: " +
        "${declaredScopes - parentServiceScopes}"
    }
    intersection
}
```

Same validation applies to `@SystemMessage`.

## Files to Change

- ✅ `Annotations.kt` - Add `name` property to `@SystemMessage`, enhance scope documentation
- ✅ `ControllerHelpers.kt` - Extract names, validate scope intersection, build registry
- ✅ `AimoConfig.kt` - Create registry bean, validate global uniqueness
- ✅ `AimoProperties.kt` - Already has `systemMessages` and `systemMessageRefs`
- ✅ `application-phase2-chatscopes-example.yaml` - Show named references with inheritance
- ✅ `plan-chatscopes-detailed.md` - Update with Task 5d

## Updated Plan Tasks

- **Task 4b (NEW)**: Add `name` and scope inheritance to `@SystemMessage` annotation
- **Task 4c (NEW)**: Update discovery with scope inheritance validation + registry
- **Task 5a (RENAMED)**: Use named system message registry in AimoConfig
- **Task 5b**: Create inline system message callbacks from YAML
- **Task 5c (NEW)**: Fail-fast validation for duplicate system message names
- **Task 5d (NEW)**: Scope inheritance/intersection validation at startup

**Problem**: System messages discovered via `@SystemMessage` are identified by array index (fragile). User needs ability to define custom system messages per scope in YAML configuration.

**Solution**: Add `system-messages: Map<String, String>` field to `AimoChatScopeProperties` for inline system message definitions.

## Design Change 2: Named System Messages with Registry (NEW)

**Problem**: How do we reference system messages? Array indices are fragile (break if order changes).

**Solution**: 
1. Add `name: String = ""` property to `@SystemMessage` annotation
2. If name is blank: auto-generate based on method/field name
3. Build a registry at startup: `systemMessageName → SystemMessageCallback`
4. Detect conflicts fail-fast (same as tools)
5. YAML references use meaningful system message names, not indices

## Annotation Changes

```kotlin
@SystemMessage(
    name = "research_guide",  // NEW: optional explicit name
    scope = ["admin", "research"]
)
fun researchGuide(): String = """
    You are a research expert...
"""

@SystemMessage  // name auto-generated: "researchGuide" or similar
fun analyzeData(): String = "..."
```

## Discovery & Registry

**File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatservice/ControllerHelpers.kt`

**New logic in `toSystemMessageCallbacks()`**:
1. Extract `name` from `@SystemMessage` annotation
2. If empty, auto-generate from method/field/property name
3. Track all names and detect conflicts
4. Fail fast if duplicate names found

**New Registry**:
```kotlin
data class SystemMessageRegistry(
    val callbacks: List<SystemMessageCallback>,
    val nameToCallback: Map<String, SystemMessageCallback>,
    val nameToIndex: Map<String, Int>
)
```

## YAML Configuration

```yaml
aimo.scope:
  research:
    display-name: "Research Assistant"
    tool-refs: ["search", "summarize"]
    system-messages:
      # Inline custom system messages (scope-specific)
      research_instructions: |
        You are a research expert...
    system-message-refs:
      # Reference pre-defined @SystemMessage beans by name
      - "research_guide"
      - "data_analysis"
```

## Semantics

1. **`@SystemMessage(name="research_guide")`** → explicit name
2. **`@SystemMessage`** → auto-generate name from method/field name
3. **Conflict detection** → fail fast if duplicate names
4. **Inline messages** (YAML) → scope-specific custom prompts
5. **Pre-defined messages** (annotations) → referenced by index in `system-message-refs`
6. **Global scope** → includes all pre-defined `@SystemMessage` beans

## Files to Change

- ✅ `Annotations.kt` - Add `name` property to `@SystemMessage`
- ✅ `ControllerHelpers.kt` - Extract names, build registry, detect conflicts
- ✅ `AimoConfig.kt` - Create registry bean, validate uniqueness
- ✅ `AimoProperties.kt` - Already has `systemMessages` and `systemMessageRefs`
- ✅ `application-phase2-chatscopes-example.yaml` - Update to show named references
- ✅ `plan-chatscopes-detailed.md` - Update with new tasks

## Updated Plan Tasks

- **Task 4b (NEW)**: Add `name` property to `@SystemMessage` annotation
- **Task 4c (NEW)**: Update discovery to extract names and build registry
- **Task 5a (RENAMED)**: Update AimoConfig to use named system message registry
- **Task 5b**: Create inline system message callbacks from YAML
- **Task 5c (NEW)**: Fail-fast validation for duplicate system message names



