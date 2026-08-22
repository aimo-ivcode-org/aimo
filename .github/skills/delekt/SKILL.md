# Detekt Skill: Fix Code Quality Issues

**Purpose**: Use Detekt (static analysis) to identify and fix code quality issues in the aimo codebase. Focus on real refactoring rather than suppressing warnings.

## Quick Start

Run detekt on a specific module:
```bash
./gradlew.bat :aimo-core:detekt          # Core module
./gradlew.bat :aimo-server:detekt        # Server module
./gradlew.bat :aimo-model-ollama:detekt  # Ollama model provider
./gradlew.bat :aimo-model-bedrock:detekt # Bedrock model provider
./gradlew.bat :aimo-mcp-client:detekt    # MCP client
./gradlew.bat :aimo-mcp-server:detekt    # MCP server
./gradlew.bat :aimo-plugin-ui:detekt     # UI plugin
```

Run detekt on all modules:
```bash
./gradlew.bat detekt
```


## Core Principle

**Fix by default. Suppress only when fixing contradicts design or purpose.**

Use `@Suppress` ONLY when:
- The pattern is **intentional** and serves a specific architectural purpose (e.g., intentional generic exception catching for robustness)
- Fixing would **violate the design intent** of that code section (e.g., DAO validation needs multiple early returns by design)
- The "violation" is **foundational to how that component works** (not a style preference)

**Never suppress just because**:
- It's quick to add `@Suppress` instead of refactoring
- The rule seems overly strict (it probably isn't)
- There are "more important things" to do (technical debt accumulates)

Ask yourself: *"Why does this code need to violate the rule?"* If the answer is architectural (not stylistic), suppress with a clear comment explaining the design reason.


## Workflow

1. **Run detekt** on the module(s) you want to improve
   ```bash
   # Single module
   ./gradlew.bat :aimo-core:detekt
   
   # All modules
   ./gradlew.bat detekt
   ```

2. **Identify issues** by type and examine each one

3. **Fix structural issues**:
   - Simple return consolidations using `break` or `when` expressions
   - Extract helpers to reduce method length and function count
   - Break long lines to improve readability

4. **Document suppressions** only when necessary:
   ```kotlin
   @Suppress("ReturnCount")  // By design: validation requires 3+ early returns
   fun deleteConversation(...): Boolean { }
   ```

5. **Verify after changes**:
   ```bash
   # Run the detekt check again to ensure no new issues were introduced. {module} is optional if you want to build a specific module.
   ./gradlew.bat :{module}:detekt
   
   # Run the full build to ensure no regressions. {module} is optional if you want to build a specific module.
   ./gradlew.bat :{module}:clean :{module}:build
   ```


## When to Keep `@Suppress`

### Production Code
Only suppress when fixing would contradict the intentional design of that code:

1. **Intentional generic exception catching** (architectural choice for robustness):
   ```kotlin
   try {
       objectMapper.readValue(file, SomeClass::class.java)
   } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
       // By design: gracefully handle ANY deserialization failure
       // (corrupt JSON, IO errors, version mismatches, etc.)
       // Catching specific exceptions would lose important error info
       log.warn("Failed to deserialize: {}", e.message, e)
       null
   }
   ```
   **Why**: The "generic" exception is intentional—catching only specific exceptions would miss important error conditions.

2. **DAO validation pattern** (multiple independent checks are foundational to data integrity):
   ```kotlin
   @Suppress("ReturnCount")
   override fun deleteChatConversation(chatId: UUID, metadata: Map<String, Any>): Boolean {
       // Each check is a distinct guard protecting data integrity
       if (getConversation(chatId, metadata) == null) return false  // Exists?
       if (!canDelete(chatId)) return false  // Authorized?
       if (isLocked(chatId)) return false  // Locked?
       getChatDir(chatId).deleteRecursively()
       return true
   }
   ```
   **Why**: These independent checks prevent data corruption. Consolidating them would obscure the validation logic and risk missing a guard.

3. **Annotation discovery helper methods** (must be methods for reflection-based tool discovery):
   ```kotlin
   @Suppress("FunctionOnlyReturnsConstant")
   @Tool
   fun helperTool(): String = "constant-value"
   // By design: must be a method for @Tool annotation to discover it
   // Cannot use 'const val' because annotations don't see module constants
   ```
   **Why**: The framework requires methods for annotation-based discovery; refactoring breaks the architecture.

**Contrast—these should NOT be suppressed** (fix them instead):
- ❌ Long method that's just doing many unrelated things → extract helpers
- ❌ Function with 3 returns because of nested if-statements → use `when` or early returns
- ❌ Line over 120 chars just for brevity → break it up
- ❌ Generic exception because "we don't care what failed" → be specific or document why generic is needed

### Test Classes
Test code has different priorities than production code. **It's more acceptable to use `@Suppress` in tests** when:
- The test itself benefits from clarity and readability over strict style rules
- Refactoring to satisfy detekt would obscure test intent or make setup/assertions harder to follow
- The test needs to be concise to demonstrate a specific behavior
- The complexity exists because the test is exercising complex interactions (not a sign of poor design)

**Examples where suppressions are reasonable in tests**:
```kotlin
class MyServiceTest {
    @Suppress("LongMethod")  // Test setup with many assertions is clear and acceptable
    @Test
    fun shouldHandleMultipleScenarios() {
        val input = setupComplexFixture()
        // ... multiple setup steps ...
        
        val result = service.process(input)
        
        assertThat(result).isNotNull()
        assertThat(result.field1).isEqualTo("expected")
        assertThat(result.field2).isEqualTo(42)
        // ... many more assertions documenting behavior ...
    }

    @Suppress("TooManyFunctions")  // Test class with many focused test methods is good design
    @Test
    fun shouldHandleEdgeCase1() { }

    @Test
    fun shouldHandleEdgeCase2() { }

    @Test
    fun shouldHandleEdgeCase3() { }
}
```

**Guideline**: In test classes, ask "Does fixing this detekt issue make the test clearer or harder to understand?" If harder, the suppression is acceptable.

## Best Practices

**General (Production & Test Code)**:
- ✅ **Do**: Fix structural issues (TooManyFunctions, LongMethod) proactively—they indicate real design problems
- ✅ **Do**: Extract helpers when a method has multiple concerns
- ✅ **Do**: Use `when` expressions to consolidate returns
- ✅ **Do**: Ask "Why does this code need to violate the rule?" before suppressing
- ✅ **Do**: Document every `@Suppress` with a comment explaining the reason
- ✅ **Do**: Run build + detekt together to verify fixes don't break tests
- ✅ **Do**: Review suppressions during code review—they're red flags for design decisions

**Test-Specific**:
- ✅ **Do**: Use suppressions in tests when they improve readability and test clarity
- ✅ **Do**: Prefer test clarity over strict adherence to production code rules
- ✅ **Do**: Document suppressions in tests too—explain why the test structure is the right choice
- ✅ **Do**: Still aim for reasonably concise tests, but prioritize demonstrating behavior clearly

**Avoid in All Code**:
- ❌ **Don't**: Suppress without attempting a real fix first (especially in production code)
- ❌ **Don't**: Add suppressions for style preferences (use `when`, break lines, extract helpers instead)
- ❌ **Don't**: Ignore structural warnings (TooManyFunctions means the class has too many concerns)
- ❌ **Don't**: Add suppressions without explaining the reason in a comment
- ❌ **Don't**: Let technical debt accumulate ("we'll refactor it later" → we won't)
- ❌ **Don't**: Suppress because fixing would be "tedious" in production code
- ❌ **Don't**: Update detekt configuration to relax limits in order to make issues go away

## Configuration

Detekt configuration (`detekt.yml` or `build.gradle.kts`) defines the code quality standards for aimo. **Never update configuration limits to "fix" issues.**

Relaxing limits signals to the team that quality standards have lowered. If a rule consistently conflicts with the codebase design, discuss with the team before adjusting—but this should be rare.

Limits exist to maintain consistency and catch regressions early. Fix the code, not the rules.

