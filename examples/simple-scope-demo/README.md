# ChatScopes Demo - Pure Annotation-Based Example

This is a minimal example demonstrating ChatScopes using **only annotations** - no YAML scope configuration required.

## Key Point

Scopes are **automatically discovered** from `@ChatService(scope = [...])` annotations. The application creates scopes on-the-fly based on what it finds in your code.

## Quick Start

### 1. Start Ollama (if not running)
```bash
ollama serve
```

### 2. Pull a model (if needed)
```bash
ollama pull mistral
```

### 3. Run the application
```bash
cd aimo/examples/simple-scope-demo
../../gradlew.bat bootRun
```

The server will start on `http://localhost:8080`

## Architecture

This example demonstrates **5 scopes** using **only `@ChatService(scope=[...])` annotations**:

At startup, the system:
1. Scans for all `@ChatService` beans and their scope declarations
2. Automatically creates scopes for each unique scope ID found
3. Assigns tools to scopes based on the annotations
4. **No YAML configuration needed** - everything is discovered from code

### Scope Pattern

**Global Tools** (no scope = available everywhere):
```kotlin
@ChatService  // No scope specified = all scopes
class GlobalTools {
    @Tool fun getHelp(): String { ... }
    @Tool fun getStatus(): String { ... }
}
```
These tools appear in: **public, admin, research, AND global** scopes

**Scoped Tools** (specific scope):
```kotlin
@ChatService(scope = ["admin"])
class AdminTools {
    @Tool fun deleteConversation(): String { ... }
}
```
These tools appear **ONLY** in the **admin** scope

### 1. **GLOBAL Scope** ✨
- Available **everywhere** (no scope restriction)
- Tools: `getHelp`, `getStatus`
- **These tools appear in every scope** by default
- This is the pattern for "cross-cutting" tools needed by all scopes
- Available to general users
- **Tools**: `add`, `multiply`, `greet`
- **Services**: `PublicTools`

### 2. **ADMIN Scope**
- Restricted to administrators
- **Tools**: `deleteConversation`, `banUser`
- **Services**: `AdminTools`

### 3. **RESEARCH Scope**
- For research-focused conversations
- **Tools**: `searchPapers`, `analyzeData`
- **Services**: `ResearchTools`

### 4. **MULTI-SCOPE Service** (MixedTools)
- A single service with tools available in multiple scopes
- `publicHelp` - available in all scopes (empty `scope = []`)
- `adminHelp` - only in admin scope
- `researchHelp` - only in research scope

## Tool Scoping Rules

**How tools are scoped by annotations:**

| Pattern | Scope Availability | Use Case |
|---------|-------------------|----------|
| `@ChatService` (no scope) | ALL scopes | Cross-cutting tools (help, status) |
| `@ChatService(scope = ["admin"])` | Only admin | Admin-only tools |
| `@ChatService(scope = ["public", "admin"])` | Public + admin | Tools in multiple specific scopes |
| `@Tool(scope = [])` (within multi-scope service) | Inherit parent service scopes | Tools inherited from parent |
| `@Tool(scope = ["admin"])` (within multi-scope service) | Only admin (intersection) | Override parent scope with restriction |

### Using curl to test different scopes

#### 1. Create a conversation in PUBLIC scope
```bash
curl -X POST http://localhost:8080/aimo-api/chat/test-conv-1 \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "What is 5 plus 3?",
    "chatScope": "public"
  }'
```
✅ **Expected**: Will have access to `add`, `multiply`, `greet` tools  
❌ **Should NOT have**: `deleteConversation`, `banUser`, `searchPapers`

#### 2. Create a conversation in ADMIN scope
```bash
curl -X POST http://localhost:8080/aimo-api/chat/test-conv-2 \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Ban the user spam-bot because they are spamming",
    "chatScope": "admin"
  }'
```
✅ **Expected**: Will have access to `deleteConversation`, `banUser` tools  
❌ **Should NOT have**: `add`, `multiply`, `searchPapers`

#### 3. Create a conversation in RESEARCH scope
```bash
curl -X POST http://localhost:8080/aimo-api/chat/test-conv-3 \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Search for papers on machine learning",
    "chatScope": "research"
  }'
```
✅ **Expected**: Will have access to `searchPapers`, `analyzeData` tools  
❌ **Should NOT have**: `add`, `banUser`, `deleteConversation`

### Using the Web UI

1. Open `http://localhost:8080` in your browser
2. Create a new conversation
3. Look for a scope selector (should show: public, admin, research, global)
4. Select a scope and verify which tools appear in the assistant's recommendations

## Code Inspection

To verify scoping is working correctly, inspect:

### Tool Definitions
```kotlin
@ChatService(scope = ["admin"])
class AdminTools {
    @Tool(description = "Ban a user (admin only)")
    fun banUser(@ToolParam("User ID") userId: String): String { ... }
}
```
- `@ChatService(scope = ["admin"])` - Restricts entire service to admin scope
- Tools in this service are **only available** when using `chatScope: "admin"`

### Mixed Scope Tools
```kotlin
@ChatService(scope = ["public", "admin", "research"])
class MixedTools {
    @Tool(scope = ["admin"])
    fun adminHelp(): String { ... }
    
    @Tool(scope = ["research"])
    fun researchHelp(): String { ... }
    
    @Tool  // No scope = available in all parent scopes
    fun publicHelp(): String { ... }
}
```
- Parent service is available in 3 scopes
- `adminHelp()` has `scope = ["admin"]` - only available in admin scope
- `researchHelp()` has `scope = ["research"]` - only available in research scope
- `publicHelp()` has no scope - available in all parent scopes (public, admin, research)

## Key Files

| File | Purpose |
|------|---------|
| `Main.kt` | Application entry point + all `@ChatService` service definitions |
| `application.yml` | **Minimal configuration** - only model and logging settings (scopes are auto-discovered) |

## What Makes This Work

1. **Pure Annotation Discovery**: When Spring starts, `AimoConfig.createChatScopeProvider()` automatically:
   - Discovers all `@ChatService(scope = [...])` beans
   - Extracts scope IDs from annotations
   - Creates ChatScope objects for each discovered scope
   - Filters tools/messages based on annotations

2. **No YAML Scope Declarations**: Unlike Phase 1 which required `aimo.scope.*` YAML entries with `tool-refs`, this is completely code-driven.

3. **Zero Configuration**: You literally just define services with `@ChatService` and the system handles the rest.

## Verifying Scope Filtering

The best way to verify scopes are working:

1. **Review the code** - open `Main.kt` and see:
   - `@ChatService(scope = ["public"])` defines tools in the public scope
   - `@ChatService(scope = ["admin"])` defines tools in the admin scope
   - etc.

2. **Run the tests**:
```bash
./gradlew.bat :examples:simple-scope-demo:test
```
The test output will show which tools are in each scope (printed to console).

3. **Watch at startup** - when Spring starts, logs show scopes being auto-discovered:
```
[INFO] Auto-discovering chat scopes from @ChatService annotations...
[INFO] Discovered scope: public
[INFO] Discovered scope: admin
[INFO] Discovered scope: research
```

4. **Verify at runtime** - The scope provider automatically filters tools based on the scope selected.

## Expected Behavior

### When you build a ChatClient with a specific scope:

```kotlin
val chatClient = chatClientBuilderFactory
    .builder(conversation)
    .withChatScope("admin")  // Select admin scope
    .build()
```

**The ChatClient will**:
1. Load all `@ChatService` beans
2. Discover all `@Tool` methods
3. **Filter tools** to only those matching the "admin" scope
4. Pass filtered tools to the model
5. Model can only call tools in the "admin" scope

**Result**: If the model tries to call a tool outside the scope, it will fail with a "tool not found" error.

## Troubleshooting

### No tools appear in any scope
- Check that `@ChatService` classes are marked with `@ChatService` annotation
- Verify class locations match Spring's component scan paths
- Check that `@Tool` methods have correct signatures

### Tools appear in wrong scope
- Verify `@ChatService(scope = [...])` matches YAML `tool-refs`
- Check that `@Tool(scope = [...])` is a valid subset of parent scope
- Look for log errors about scope validation failures

### Runtime errors about missing tools
- Enable DEBUG logging to see which tools are available in each scope
- Verify the tool name matches exactly (case-sensitive)
- Confirm scope is being passed to builder via `withChatScope()`

## Next Steps

1. **Add to existing project**: Copy the `@ChatService` pattern from `Main.kt` to your own services
2. **Add system messages**: Use `@SystemMessage` annotations to provide scope-specific prompts
3. **Test with your model**: Update `application.yml` with your Ollama/Bedrock configuration

## References

- [**AGENTS.md**](../../AGENTS.md) - Technical reference for ChatScopes
- [**README.md**](../../README.md) - Main project documentation
- [**ROADMAP.md**](../../ROADMAP.md) - implementation roadmap









