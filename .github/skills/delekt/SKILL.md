---
name: Detekt Skill
description: Use Detekt (static analysis) to identify and fix code quality issues in the aimo codebase. Focus on real refactoring rather than suppressing warnings.
---

# Detekt Skill: Fix Code Quality Issues

**Purpose**: Use Detekt (static analysis) to identify and fix code quality issues in the aimo codebase. Focus on real refactoring rather than suppressing warnings.

## When This Skill Is Invoked

When you type `/skill:delekt` or are asked to use this skill, follow the workflow below **in order, without skipping steps**. The workflow is structured to ensure you understand the rules and configuration before making changes:

1. **FIRST**: Run the Detekt command with the full `clean :module:build :module:detekt` sequence (do not run detekt alone, and do not skip clean or build)
2. Read the Detekt **markdown report** (not HTML) and understand all detected issues
3. Read the **detekt.yml configuration file** to understand the rules, thresholds, and why they exist for this project
4. **🔴 MANDATORY**: **For each issue type found**, read the rule documentation FIRST to understand what the rule checks for, why it exists, and what refactorings are recommended — **BEFORE YOU ATTEMPT ANY FIXES**
5. Fix the structural issues in the code based on that understanding
6. Verify that all changes resolve the issues by running Detekt again

⚠️ **Critical Points**:
- Do not run detekt alone without clean + build
- Do not use the HTML report — use the markdown (`.md`) report
- **🔴 MANDATORY: Do NOT skip reading the rule documentation for each issue type** — this is essential for correct fixes
- Do not attempt to identify issues manually or guess at violations
- **Every issue type requires documentation review before any code changes**

The Detekt command and its reports are the authoritative source of truth for code quality issues.

## Quick Start

**⚠️ Agent Protocol**: When the agent runs Detekt, it will use the full clean + build + detekt sequence:

For the agent (Copilot):
```bash
./gradlew.bat :aimo-core:clean :aimo-core:build :aimo-core:detekt          # Core module
./gradlew.bat :aimo-server:clean :aimo-server:build :aimo-server:detekt        # Server module
./gradlew.bat :aimo-model-ollama:clean :aimo-model-ollama:build :aimo-model-ollama:detekt  # Ollama model provider
./gradlew.bat :aimo-model-bedrock:clean :aimo-model-bedrock:build :aimo-model-bedrock:detekt # Bedrock model provider
./gradlew.bat :aimo-mcp-client:clean :aimo-mcp-client:build :aimo-mcp-client:detekt    # MCP client
./gradlew.bat :aimo-mcp-server:clean :aimo-mcp-server:build :aimo-mcp-server:detekt    # MCP server
./gradlew.bat :aimo-plugin-ui:clean :aimo-plugin-ui:build :aimo-plugin-ui:detekt     # UI plugin
```

For all modules:
```bash
./gradlew.bat clean build detekt
```

**Why clean + build + detekt?** The clean and build steps ensure that:
- Stale artifacts are removed
- The code compiles correctly
- All tests pass
- Detekt analyzes the freshly compiled code

Always run with `clean` and `build` to ensure that the code compiles and tests pass before running detekt. Detekt will analyze the compiled code and report any issues.

## Rules References

**After running Detekt**, immediately consult the module Markdown report at:

`{module}/build/reports/detekt/detekt.md`

⚠️ **IMPORTANT**: Use the **Markdown report** (`.md`), not the HTML report. The markdown report:
- Is the authoritative source for detected issues
- Includes direct links to rule documentation
- Is easier to parse and understand

## Finding Local Rule Documentation

We keep a local copy of all Detekt rule documentation in this repository. **Prefer local documentation** because it works offline and points to the repository version of the docs.

**Local rule documentation files are located at:**
```
.github/skills/delekt/rules/{category}.mdx
```

### Available Rule Categories

The documentation is organized by rule category. When you find an issue in the markdown report, use this table to find the right file:

| Rule Category | Local File Path | Example Rules |
|---|---|---|
| **complexity** | `.github/skills/delekt/rules/complexity.mdx` | LongMethod, TooManyFunctions, NestedBlockDepth, CyclomaticComplexMethod |
| **style** | `.github/skills/delekt/rules/style.mdx` | MaxLineLength, RedundantVisibilityModifier, MagicNumber |
| **naming** | `.github/skills/delekt/rules/naming.mdx` | ClassNaming, FunctionNaming, VariableNaming |
| **potential-bugs** | `.github/skills/delekt/rules/potential-bugs.mdx` | AvoidReferentialEquality, UnsafeCast, UnreachableCode |
| **performance** | `.github/skills/delekt/rules/performance.mdx` | ForEachOnRange, SpreadOperator |
| **exceptions** | `.github/skills/delekt/rules/exceptions.mdx` | TooGenericExceptionCaught, SwallowedException |
| **empty-blocks** | `.github/skills/delekt/rules/empty-blocks.mdx` | EmptyCatchBlock, EmptyFunctionBlock |
| **coroutines** | `.github/skills/delekt/rules/coroutines.mdx` | SleepInsteadOfDelay, GlobalCoroutineUsage |
| **comments** | `.github/skills/delekt/rules/comments.mdx` | UndocumentedPublicClass, OutdatedDocumentation |

### How to Find and Read a Specific Rule

**Example: Finding MaxLineLength documentation**

1. The markdown report shows an issue: `style, MaxLineLength (31)`
2. Look at the table above: MaxLineLength is in the **style** category
3. Open the file: `.github/skills/delekt/rules/style.mdx`
4. Search within the file for `### MaxLineLength` (use Ctrl+F / Cmd+F)
5. Read the entire section: what it checks, why it matters, noncompliant/compliant examples

**Example: Finding LongMethod documentation**

1. The markdown report shows an issue: `complexity, LongMethod (2)`
2. Look at the table above: LongMethod is in the **complexity** category
3. Open the file: `.github/skills/delekt/rules/complexity.mdx`
4. Search within the file for `### LongMethod`
5. Read the entire section

### Reading Strategy for Rule Documentation

When you open a `.mdx` file and find your rule:

1. **Read the rule description** — What does it check for?
2. **Note the configuration** — Look for "Configuration options:" and "Active by default:"
3. **Read Noncompliant Code examples** — See what the rule catches
4. **Read Compliant Code examples** — See how to fix violations
5. **Check thresholds** — For rules with numbers (e.g., `maxLineLength: 120`), note the threshold configured in `detekt.yml`

**🔴 MANDATORY BEFORE ANY CODE CHANGES**: Open and read the rule documentation for EACH issue type. This is essential for understanding what the rule checks for and what refactorings are appropriate.

## ⛔ Do Not Skip Documentation

**This cannot be overemphasized**: You MUST read the rule documentation for each issue type before attempting any fixes. 

- ❌ Do NOT attempt to fix MaxLineLength without reading the MaxLineLength rule documentation
- ❌ Do NOT attempt to fix LongMethod without reading the LongMethod rule documentation  
- ❌ Do NOT attempt to fix any issue without first reading the corresponding rule documentation
- ✅ DO read the entire rule explanation, including rationale and recommended refactorings
- ✅ DO consult `detekt.yml` for project-specific configuration of the rule
- ✅ DO understand why the rule exists and what it's trying to prevent

Skipping documentation leads to:
- Incomplete or incorrect fixes
- Not understanding the root cause of violations
- Using suppression as a shortcut instead of proper refactoring
- Violating the rule again in different ways later

## Configuration

Detekt configuration (`detekt.yml` or `build.gradle.kts`) defines the code quality standards for aimo. **Never update configuration limits to "fix" issues.**

Always reference the repository's canonical `detekt.yml` (located at the repository root) when investigating rule behavior, thresholds, and rule IDs. The `detekt.yml` file is the authoritative source for which rules are enabled, their severities, and any project-specific overrides—use it to look up the exact rule name to place in `@Suppress` and to verify whether a rule's threshold or parameters already explain a finding.

Relaxing limits signals to the team that quality standards have lowered. If a rule consistently conflicts with the codebase design, discuss with the team before adjusting—but this should be rare.

Limits exist to maintain consistency and catch regressions early. Fix the code, not the rules.


## Workflow

**Agent Protocol**: The agent will follow these steps in order without skipping any step:

1. **Run Detekt immediately** on the specified module(s):
     ```bash
     # For a single module (REQUIRED: must include clean and build, do not skip them)
     ./gradlew.bat :aimo-core:clean :aimo-core:build :aimo-core:detekt
     
     # For all modules
     ./gradlew.bat clean build detekt
     ```
     
     ⚠️ **CRITICAL**: You MUST run the full `clean :aimo-core:build :aimo-core:detekt` command sequence. Do not run just `:aimo-core:detekt` alone. The clean step ensures stale artifacts are removed, the build step ensures the code compiles and tests pass, and only then does detekt analyze the freshly compiled code.
     
     Do not attempt to identify issues manually. Wait for the Detekt command to complete and review its output.

2. **Read the Detekt markdown report** (NOT HTML) from:
     ```
     {module}/build/reports/detekt/detekt.md
     ```
     
     ⚠️ **CRITICAL**: Use the MARKDOWN report (`.md` file), not the HTML report (`.html` file). The markdown report is the authoritative source for all code quality issues and includes direct links to rule documentation. Open the `.md` file and read it carefully.

3. **Read the detekt configuration file** (`detekt.yml`) to understand the rules and thresholds:
     ```
     detekt.yml (at the repository root)
     ```
     
     This file defines all the code quality standards for the aimo project. Before attempting any fixes:
     - Open the `detekt.yml` file at the repository root
     - Search for each rule you need to fix (e.g., `MaxLineLength`, `LongMethod`, `TooManyFunctions`)
     - Read the rule configuration: what are the thresholds (`maxLineLength`, `allowedLines`, etc.)?
     - Note if any rules are disabled (`active: false`)
     - Understand why these thresholds exist and what they enforce for this project
     
     ⚠️ **Important**: The configuration file is your reference for understanding what each rule checks for and what the current project standards are.

4. **🔴 MANDATORY: For each issue type (e.g., MaxLineLength, LongMethod), read the rule documentation BEFORE attempting any fixes**:
     
     > **This step must happen for EVERY issue type. Do not skip it. Do not attempt to fix code without understanding the rule first.**
     
      a. **Step 4.1: Find and open the rule documentation**:
         - The Detekt markdown report identifies which rule triggered (e.g., `style, MaxLineLength` or `complexity, LongMethod`)
         - Use the **Rules Reference table in the "Finding Local Rule Documentation" section** above to find the correct `.mdx` file
         - Open the file and search (Ctrl+F) for the rule name (e.g., search for `### MaxLineLength`)
         - Read the entire rule section including description, configuration, and code examples
         - ⚠️ Do NOT skip this step. Understanding the rule is critical before attempting fixes
     
     b. **Step 4.2: Read the ENTIRE rule explanation**:
        - ✅ What the rule checks for
        - ✅ Why it matters for code quality
        - ✅ What refactorings are recommended
        - ✅ Any edge cases or special considerations
        - ⚠️ **DO NOT SKIP THIS STEP** — Understanding the rule is essential before fixing the code. Failing to read the documentation often leads to incorrect or incomplete fixes.
     
     c. **Step 4.3: Group issues of the same type** together (e.g., all MaxLineLength violations in one batch)
     
     d. **Step 4.4: Document your understanding**:
        - Summarize what you learned from the rule documentation
        - Note the specific refactoring strategy for this rule type
        - Identify any project-specific context from detekt.yml

5. **Fix structural issues** by refactoring the code based on your understanding of the rule:
     
     ⚠️ **Before fixing any code, verify you have completed step 4 for this issue type.** If you have not read the rule documentation, stop and read it now.
     
     Apply rule-specific refactoring strategies:
     - **For MaxLineLength**: Break long lines intelligently. Extract variables with descriptive names to shorten expressions. Use helper functions for complex inline logic.
     - **For LongMethod**: Extract helper methods with clear names. Break complex logic into smaller pieces with single responsibility. Each helper should be testable and understandable.
     - **For TooManyFunctions**: Refactor classes into focused single-responsibility components. Move related functions into dedicated classes or extract concern-specific helpers.
     - **For LongParameterList**: Use data classes or value objects to group related parameters. Consider using a builder or configuration object.
     - **For NestedBlockDepth**: Extract nested logic into helper functions. Use guard clauses to reduce nesting early in methods.
     - **For other rules**: Apply the recommended refactorings from the rule documentation you read in step 4.
     
     Do not suppress warnings with `@Suppress` unless absolutely necessary (see Notes section below).

6. **Verify after changes** by running Detekt again to confirm all issues are resolved:
     ```bash
     ./gradlew.bat :{module}:clean :{module}:build :{module}:detekt
     ```
     
     Ensure no new issues were introduced and the build succeeds.
     
     If issues remain:
     - Return to step 4 for that issue type
     - Review the rule documentation again for any missed guidance
     - Apply the recommended refactorings more thoroughly
     - Re-run detekt to verify

## Notes

### Documentation Review (Mandatory)

**Before you attempt to fix ANY issue**, you MUST:

1. ✅ Read the rule documentation
2. ✅ Understand what the rule checks for and why
3. ✅ Identify the recommended refactoring strategy
4. ✅ Check `detekt.yml` for project-specific configuration

Only after these steps should you begin code changes. If you find yourself about to fix code without having read the rule documentation, STOP and read it first.

### `@Suppress`

Avoid using `@Suppress` unless absolutely necessary. Suppression should be a last resort when a rule is not applicable to a specific case. If you must use `@Suppress`, always include a comment explaining why the suppression is necessary and reference the rule ID.

⚠️ **Before considering suppression**, ensure you have:
- ✅ Read the rule documentation thoroughly  
- ✅ Checked `detekt.yml` for the rule configuration
- ✅ Considered all recommended refactorings
- ✅ Determined that refactoring is truly not applicable to this specific case

Examples of when suppression is not appropriate:
- Suppressing a rule because the code is "too complex" without refactoring it.
- Suppressing a rule because the code is "too long" without extracting helper methods or breaking it into smaller functions.
- Suppressing a rule because the code is "too many functions" without refactoring the class or module to reduce the number of functions.

Examples of when suppression may be appropriate:
- "generic exception" when catching "Exception" is deliberate and necessary for a specific use case, and the code is well-documented to explain why.
- Unit tests that intentionally violate a rule to test the rule's behavior (with clear documentation).