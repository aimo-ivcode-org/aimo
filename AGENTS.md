# AGENTS Guide for `aimo`

## Big Picture
- `aimo` is a multi-module Gradle workspace: core orchestration (`aimo-core`), transport (`aimo-server`), model adapters (`aimo-model-ollama`, `aimo-model-bedrock`), optional runtime cache (`aimo-session-cache-ehcache`), UI plugin (`aimo-plugin-ui`), React UI (`aimo-ui`), runnable apps in `examples/*`.
- Runtime composition happens in example apps (`examples/simple-ollama`, `examples/simple-bedrock`): they wire `aimo-server` + `aimo-plugin-ui` + one model provider + DAO bean (`AimoChatClientDaoMemory`).
- Core seam: `AimoChatModelProviderFactory` -> `AimoChatModel` -> `AimoChatEngine` (`aimo-core/src/main/kotlin/org/ivcode/aimo/core/model/AimoChatEngine.kt`). Keep provider-specific logic in adapter modules.

## Request/Data Flow (important)
- HTTP entrypoint is `POST /aimo-api/chat/{chatId}` (`aimo-server/.../controller/ChatController.kt`), streamed as NDJSON via `StreamingResponseBody`.
- `ChatService` merges request metadata + conversation durable metadata into chat context before calling core (`aimo-server/.../service/ChatService.kt`).
- `AimoChatClientImpl` loop (`aimo-core/.../client/chat/AimoChatClientImpl.kt`): system messages -> history budget -> model call -> optional tool calls -> persist prompt + generated messages.
- History source is cache-first then DAO lazy-load; persistence always goes through `AimoConversationClient.addMessages`.
- Durable metadata lives in DAO (`writeChatProperty`/`deleteChatProperty`), runtime-only metadata lives in session cache (`writeRuntimeProperty`) (`aimo-core/.../client/conversation/AimoConversationClientImpl.kt`).

## Project-Specific Conventions
- Tool/system discovery is reflection-based from `@ChatController` beans (`aimo-core/.../conf/AimoConfig.kt`).
- LLM-callable tools use `@Tool`; parameter docs use `@ToolParam`; a parameter named `context` of type `Map` is auto-injected and excluded from generated JSON schema (`aimo-core/.../controller/ControllerHelpers.kt`, `MethodAimoToolCallback.kt`).
- System messages can be `@SystemMessage` field/property/method; method signature must be `() -> String?` or `(SystemMessageContext) -> String?`.
- Context keys are fixed (`chatId`, `requestId`, `conversation-client`) in `aimo-core/.../util/Extensions.kt`; server adds `requestMetadata` (`aimo-server/.../util/ContextExtensions.kt`).
- Title behavior is strict: assistant cannot overwrite a USER-set title (`aimo-plugin-ui/.../chatcontroller/TitleChatController.kt`).

## Integration Points
- API prefix constant is `API_CONTROLLER_CONTEXT = "aimo-api"` (`aimo-server/.../consts/AimoServerConsts.kt`).
- Frontend clients are hand-maintained wrappers at `aimo-ui/src/api/aimo-client` and `aimo-ui/src/api/aimo-ui-client`; default base URL is hardcoded to `http://localhost:8080`.
- Frontend streaming parser (`ResponseBuilder`) expects newline-delimited JSON and aggregates partial assistant chunks (`aimo-ui/src/api/aimo-client/ResponseBuilder.test.ts`).
- `aimo-plugin-ui` depends on `aimo-ui` and forwards `/` to static `index.html` (`aimo-plugin-ui/.../config/WebConfig.kt`).
- Session cache provider is optional and auto-wired with `@ConditionalOnMissingBean(AimoSessionCacheProvider::class)` (`aimo-session-cache-ehcache/.../SessionCacheEhcacheConfig.kt`).

## Model Provider Rules
- Exactly one primary model must resolve globally; if multiple models exist and none are primary, startup fails (`aimo-core/.../conf/AimoConfig.kt`).
- Provider-specific factories also enforce at most one provider-local `primary=true` (`OllamaChatModelFactory`, `BedrockChatModelFactory`).
- Bedrock pools clients by `(region, credentials)` and validates all-or-none explicit credentials.

## Developer Workflows
- Run composed app: `./gradlew.bat :examples:simple-ollama:bootRun` or `:examples:simple-bedrock:bootRun`.
- Root checks: `./gradlew.bat build` and `./gradlew.bat test`.
- Focused JVM tests: `./gradlew.bat :aimo-core:test --tests "*AimoChatClientImplMessageIdTest"`.
- Frontend dev: in `aimo-ui`, run `npm install`, `npm run dev`; verify with `npm run type-check`, `npm run test`, `npm run build`.
- Building `aimo-ui` from Gradle triggers npm install/build via `build-resources` task (`aimo-ui/build.gradle.kts`).

## Commit Notes
- Before committing, review *all* staged and unstaged changes across the workspace (not just the file you edited) so unrelated edits are caught early.
- Build the commit message from the full diff: mention each meaningful behavior/config/API/test change included in that commit.
- Prefer a single commit per coherent change set; if the diff mixes concerns, split into multiple commits with separate messages.
