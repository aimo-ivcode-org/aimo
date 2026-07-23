# AGENTS Guide for `aimo`

## Big Picture
- `aimo` is a multi-module Gradle workspace: core orchestration (`aimo-core`), transport (`aimo-server`), model adapters (`aimo-model-ollama`, `aimo-model-bedrock`), MCP client integration (`aimo-mcp-client`), UI plugin (`aimo-plugin-ui`), React UI (`aimo-ui`), runnable apps in `examples/*`.
- Runtime composition happens in example apps (`examples/simple-ollama`, `examples/simple-bedrock`, `examples/mcp-client-ollama`): they wire `aimo-server` + `aimo-plugin-ui` + one model provider + DAO bean (`AimoChatClientDaoMemory`); MCP example adds `aimo-mcp-client`.
- Core seam: `AimoChatModelProviderFactory` -> `AimoChatModelConfig` -> `AimoChatEngine` (`aimo-core/src/main/kotlin/org/ivcode/aimo/core/model/AimoChatEngine.kt`). Keep provider-specific logic in adapter modules.
- MCP integration: `aimo-mcp-client` discovers tools and prompts from external Model Context Protocol servers, integrating them into AIMO's existing tool/system message system with scope awareness.

## Request/Data Flow (important)
- HTTP entrypoint is `POST /aimo-api/chat/{chatId}` (`aimo-server/.../controller/ChatController.kt`), streamed as NDJSON via `StreamingResponseBody`.
- `ChatService` merges request metadata + conversation durable metadata into chat context before calling core (`aimo-server/.../service/ChatService.kt`).
- `AimoChatClientImpl` loop (`aimo-core/.../client/chat/AimoChatClientImpl.kt`): system messages -> fetch history from DAO -> prompt budget -> model call -> optional tool calls -> persist prompt + generated messages.
- All message history is read from DAO; persistence always goes through `Conversation.addMessages`.
- Durable metadata lives in DAO (`writeChatProperty`/`deleteChatProperty`) (`aimo-core/.../conversation/ConversationImpl.kt`).

## Project-Specific Conventions
- Tool/system discovery is reflection-based from `@ChatService` beans (`aimo-core/.../conf/AimoConfig.kt`) plus MCP servers from `aimo-mcp-client` (registered as `ChatServiceProvider` instances).
- LLM-callable tools use `@Tool` (local) or MCP `tools/call` RPC (remote via `aimo-mcp-client`); parameter docs use `@ToolParam`; a parameter named `context` of type `Map` is auto-injected and excluded from generated JSON schema (`aimo-core/.../controller/ControllerHelpers.kt`, `MethodAimoToolCallback.kt`). MCP tool names are prefixed with `"{serverId}:"` to avoid collisions (e.g., `my-server:search`).
- System messages can be `@SystemMessage` field/property/method (local) or MCP prompts (remote via `aimo-mcp-client`); method signature must be `() -> String?` or `(SystemMessageContext) -> String?`. MCP prompt names are similarly prefixed (e.g., `my-server:research-guidelines`).
- Context keys are fixed (`chatId`, `requestId`, `conversation-client`) in `aimo-core/.../util/Extensions.kt`; server adds `requestMetadata` (`aimo-server/.../util/ContextExtensions.kt`).
- Title behavior is strict: assistant cannot overwrite a USER-set title (`aimo-plugin-ui/.../chatcontroller/TitleChatController.kt`).

## Chat Scopes (Phase 2)
- **Scope concept**: ChatScopes define which tools and system messages are available in a conversation. Every instance has a built-in `"global"` scope with unrestricted tools and system messages only.
- **Scope definition**: Use `@ChatService(scope=["admin", "research"])` on class and `@Tool(scope=[...])` / `@SystemMessage(scope=[...])` on members to restrict visibility.
- **Empty scope semantics**: Empty `scope = []` on tool/message inherits the parent `@ChatService(scope=...)` when the parent is scoped; available to all scopes only when parent has no scope restriction. Empty on `@ChatService` means the service has no scope restrictions.
- **Scope validation**: At startup, scopes on tools/messages are validated as subsets of parent `@ChatService` scope; fail-fast if invalid.
- **Named system messages**: System messages get stable names via `@SystemMessage(name="...")` or auto-generated from method/field name. YAML `system-message-refs` reference by name.
- **Inline system messages**: Scopes can define custom system messages in YAML under `system-messages: {id: "text"}`. These are always included in the scope.
- **Runtime resolution**: Builder uses explicit `withChatScope()` selection or defaults to global scope.
- **Scope filtering**: Scopes are constructed by `ChatScopeProvider` with tools/system messages already filtered; the builder only selects which `ChatScope` to use.
- **Configuration**: Scopes are pre-defined in `application.yml` under `aimo.scope.*`; each scope lists `tool-refs` and `system-message-refs` (`aimo-core/.../properties/AimoProperties.kt`).

## MCP Client Integration
- **Module**: `aimo-mcp-client` discovers tools and prompts from Model Context Protocol servers (stdio, HTTP, SSE transports) and exposes them via `ChatServiceProvider` (one per server).
- **Configuration**: MCP servers are defined in `application.yml` under `aimo.mcp.servers[]` with transport type, command/URL, optional auth, and scope list (`aimo-mcp-client/.../config/McpProperties.kt`).
- **Tool/Prompt naming**: Remote tools/prompts are named `"{serverId}:{toolName}"` and `"{serverId}:{promptName}"` to avoid collisions with local tools and between servers.
- **Scope visibility**: MCP servers respect AIMO's scope rules—empty `scope: []` makes tools available in global scope; non-empty scopes restrict to those scopes only.
- **Graceful degradation**: Set `aimo.mcp.required: false` in config to allow startup even if MCP servers are unreachable; tools/prompts become unavailable.
- **Periodic refresh**: `aimo.mcp.discovery-interval-minutes` controls automatic re-discovery of tools/prompts; set to `0` to disable scheduler.
- **Discovery entry point**: `McpAutoConfiguration` registers MCP servers as `ChatServiceProvider` beans via `ChatServiceProviderRegistry` at startup (`aimo-mcp-client/.../config/McpAutoConfiguration.kt`).

## Integration Points
- API prefix constant is `API_CONTROLLER_CONTEXT = "aimo-api"` (`aimo-server/.../consts/AimoServerConsts.kt`).
- Frontend clients are hand-maintained wrappers at `aimo-ui/src/api/aimo-client` and `aimo-ui/src/api/aimo-ui-client`; default base URL is hardcoded to `http://localhost:8080`.
- Frontend streaming parser (`ResponseBuilder`) expects newline-delimited JSON and aggregates partial assistant chunks (`aimo-ui/src/api/aimo-client/ResponseBuilder.test.ts`).
- `aimo-plugin-ui` depends on `aimo-ui` and forwards `/` to static `index.html` (`aimo-plugin-ui/.../config/WebConfig.kt`).

## Model Provider Rules
- Exactly one primary model must resolve globally; if multiple models exist and none are primary, startup fails (`aimo-core/.../conf/AimoConfig.kt`).
- Provider-specific factories also enforce at most one provider-local `primary=true` (`OllamaChatModelFactory`, `BedrockChatModelFactory`).
- Bedrock pools clients by `(region, credentials)` and validates all-or-none explicit credentials.

## Developer Workflows
- Run composed app: `./gradlew.bat :examples:simple-ollama:bootRun` or `:examples:simple-bedrock:bootRun` or `:examples:mcp-client-ollama:bootRun` (MCP + Ollama).
- Root checks: `./gradlew.bat build` and `./gradlew.bat test`.
- Focused JVM tests: `./gradlew.bat :aimo-core:test --tests "*AimoChatClientImplMessageIdTest"`.
- Frontend dev: in `aimo-ui`, run `npm install`, `npm run dev`; verify with `npm run type-check`, `npm run test`, `npm run build`.
- Building `aimo-ui` from Gradle triggers npm install/build via `build-resources` task (`aimo-ui/build.gradle.kts`).

## Commit Notes
- Before committing, review *all* staged and unstaged changes across the workspace (not just the file you edited) so unrelated edits are caught early.
- Build the commit message from the full diff: mention each meaningful behavior/config/API/test change included in that commit.
- Prefer a single commit per coherent change set; if the diff mixes concerns, split into multiple commits with separate messages.
