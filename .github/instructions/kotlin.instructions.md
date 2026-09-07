---
applyTo: "**/*.kt,**/*.kts"
---
# Kotlin file instructions (repository-specific)

These instructions apply to Kotlin source files and Gradle Kotlin DSL files in this repository. They are read by Copilot when generating or editing Kotlin code in this project. Keep suggestions focused on idiomatic Kotlin, Spring Boot patterns used in this codebase, and the repository's build and test workflows.

Key expectations
----------------
- Use idiomatic Kotlin: prefer `val` for immutable values, use `data class` for DTOs, prefer expression bodies for simple functions, and prefer extension functions for utility helpers.
- Use coroutines for asynchronous flows where present; prefer suspend functions over blocking threads.
- Public APIs may be unstable during this pre-1.0 development phase: prefer simplicity, clarity, and minimal surface area for public APIs over backward-compatibility. It is acceptable to make breaking changes during active development as long as the change is documented (KDoc + PR description) and rationale is recorded.

Documentation expectations
--------------------------
- Document every class, interface, and object with KDoc when it introduces behavior, state, or a reusable contract. The KDoc should say what the type owns, why it exists, and any important invariants.
- Document every function and method with KDoc when it is public or internal. Use the term *function* for both top-level and member functions; if you prefer *method*, treat it as the member-function equivalent.
- For each documented function or method, include:
  - a short summary line,
  - `@param` entries for every parameter,
  - `@return` when the function returns a value,
  - side effects or thread-safety notes when those details matter.
- Private helpers do not need exhaustive KDoc if the name and structure are already obvious, but add KDoc whenever a helper encodes important rules, transforms data, or exists to make non-obvious control flow easier to read.
- If a function is so small that a comment would add no value, keep the implementation simple and still prefer a brief one-line KDoc when the code is likely to be read outside its immediate context.

  When the project approaches a stable release, follow stricter versioning and compatibility practices; until then prioritize developer productivity and clear, small APIs.
- Preserve package declarations, license headers, and existing file-level annotations.

Object-oriented principles and method-level guidance
-------------------------------------------------
- Prefer clear object-oriented design. Keep classes focused (Single Responsibility Principle): each class should have one reason to change.
- Keep methods short and focused. Aim for methods that are easy to understand at a glance (recommended guideline: prefer methods under ~40 lines; when a method grows beyond this, extract private helper methods with descriptive names).
- Favor composition over deep inheritance hierarchies. Use interfaces for behavior contracts and small concrete implementations.
- Document all classes, methods, and functions with KDoc when they are visible beyond a tiny local scope. Keep the language direct: describe the responsibility, the contract, and any non-obvious constraints.
- KDoc for functions and methods should include a brief summary line, parameter descriptions, and the return value when applicable. For non-trivial functions, include a short note about side effects and thread-safety when relevant.
- Inside methods, add inline comments that document each major code chunk or decision point. Comments should explain "why" (intent) not just "what" — the code shows what; the comment explains intent and important invariants.
- Treat a "major code block" as a contiguous section of logic that does one meaningful job, such as loading inputs, validating state, building data, invoking external dependencies, or persisting results. In practice, a major block is usually separated by a blank line, a guard clause, a loop, or a distinct transition in responsibility.
- Do not comment every small statement; comment the start of each meaningful block so the reader can follow the method at a high level without re-reading every line.
- Use private helper methods to make the top-level method read like a sequence of high-level steps; each helper should have a clear name and a short KDoc if non-trivial.
- When modifying existing code, don't remove existing explanatory comments unless they are obsolete — update them to reflect the new behavior.

Project patterns to follow
------------------------
- Follow Spring Boot conventions used in the project (annotation-based components, `@Configuration`, `@Bean`, `@Service`, `@Controller`).
- Use the project's core abstractions where applicable: `AimoChatEngine`, `AimoChatModelProviderFactory`, `ChatService`, `@Tool`, `@SystemMessage` (see `AGENTS.md`).
- For tools and system messages discovered via MCP, respect naming and scoping rules: remote names must be prefixed with `{serverId}:` and `scope` values must be subsets of their parent `@ChatService`.
- Controller routes use `API_CONTROLLER_CONTEXT = "aimo-api"` as the prefix where appropriate.

Formatting & linting
--------------------
- Match the repository's existing formatting. Do not change indentation style (tabs vs spaces) in a file.
- If you add or suggest formatting tools (e.g., ktfmt, ktlint), include configuration and a Gradle task, and add CI steps to validate them.

Formatting note (Detekt long-method rule)
---------------------------------------
- This repository uses Detekt for static analysis. Reference the `detekt.yml` configuration for rules and thresholds.

Testing & validation guidance
----------------------------
- Add unit tests for behavior changes under the same package in `src/test/kotlin`.
- When suggesting code that affects serialization, add tests that assert backward compatibility (round-trip or migration tests).
- Run `./gradlew.bat test` for module-level validation and `./gradlew.bat build` for repository-wide checks.

Security
--------
- Never embed secrets, API keys, or credentials in code. Use configuration (`application.yml`, environment variables) and document where to set them.

If uncertain
-----------
- Follow nearby files in the same package for style and patterns.
- When a pattern conflicts with these instructions, prefer local project patterns and add a short code comment explaining why a different choice was necessary.

Examples of preferred phrasing for Copilot suggestions
---------------------------------------------------
- "Generate an idiomatic Kotlin data class with kotlinx.serialization (or Jackson) annotations, using `val` for properties and a concise constructor." 
- "Create a suspend function that uses coroutines to call the model provider and returns a typed result." 

Last updated: 2026-08-22

