# AIMO Roadmap: Goals

## Backend

### Phase 1: Configuration ✅ **COMPLETE**
**Goal**: Make the system configurable through properties and runtime builders

**Status**: Phase 1 is **100% complete** with all objectives achieved.

**What Was Implemented**:

1. **Factory-Based Architecture**:
   - Removed legacy `Aimo` facade
   - Implemented `ConversationFactory` for conversation management
   - Implemented `ChatClientBuilderFactory` for flexible chat client composition
   - Each conversation is independently managed with no global singleton

2. **Builder Pattern**:
   - `ChatClientBuilder` provides fluent API for runtime composition
   - Supports model selection and custom interceptors
   - Deferred construction - builds only when `build()` is called
   - Context injection for request metadata

3. **Conversation Abstraction**:
   - `Conversation` interface abstracts storage (memory, file, RDS, MongoDB ready)
   - Conversations manage message history and metadata
   - Storage-agnostic design via `AimoChatClientDao`

4. **Interceptor Infrastructure**:
   - Unified interceptor mechanism for cross-cutting concerns
   - Two types: `ChatClientInterceptor` (request/response) and `ConversationInterceptor` (storage)
   - Built-in interceptors: Logging, Tracing, ErrorHandling
   - Chain of responsibility pattern for composable behavior
   - Spring-managed beans automatically registered

5. **Configuration System**:
   - YAML-based configuration under `aimo.*` prefix
   - Provider-specific model configuration (`aimo.model.ollama`, `aimo.model.bedrock`)
   - Interceptor configuration with property-based enablement
   - Primary model resolution with validation
   - All interceptors disabled by default (opt-in)

6. **Documentation**:
   - Comprehensive README with builder pattern examples
   - Configuration guide with examples
   - Programmatic usage guide
   - Example application documentation

**Example Usage**:
```kotlin
@Service
class ChatService(
    private val conversationFactory: ConversationFactory,
    private val chatClientBuilderFactory: ChatClientBuilderFactory
) {
    fun chat(chatId: UUID, userId: String, message: String): AimoChatResponse {
        val conversation = conversationFactory.getConversation(chatId, userId)
            ?: throw NotFoundException("Conversation not found: chatId=$chatId")
        val chatClient = chatClientBuilderFactory
            .builder(conversation)
            .withModel("gpt-oss")  // Optional model override
            .build()

        return chatClient.chat(AimoChatRequest(prompt = message, context = emptyMap()))
    }
}
```

**Configuration Example**:
```yaml
aimo:
  data-dir: ./data/conversations
  global-user-id: default-user
  
  model:
    ollama:
      gpt-oss:
        base-url: http://localhost:11434
        primary: true
        options:
          model: gpt-oss:20b
          temperature: 0.7
  
  interceptors:
    logging:
      enabled: true
      level: DEBUG
```

### Phase 1.5: Rename `@ChatController` to `@ChatService` ✅ **COMPLETE**
**Goal**: Rename the `@ChatController` annotation to `@ChatService` for clearer semantics

**Status**: Phase 1.5 is **100% complete**. The annotation has been renamed and all code updated.

**What Was Changed**:
1. **Annotation Renamed**: `@ChatController` → `@ChatService`
2. **Package Renamed**: `org.ivcode.aimo.core.controller` → `org.ivcode.aimo.core.chatservice`
3. **Entity Renamed**: `ChatControllerEntity` → `ChatServiceEntity`
4. **All Usages Updated**: In `aimo-core`, `aimo-plugin-ui`, and `examples/`
5. **Old Code Deleted**: Previous package and annotations completely removed

**Current State**:
- All tools and system messages use `@ChatService` annotation
- Discovery mechanism updated in `AimoConfig`
- No backwards compatibility - clean migration

**Example**:
```kotlin
@ChatService
class CalculatorService {
    @Tool(description = "Add two numbers")
    fun add(
        @ToolParam("First number") a: Int,
        @ToolParam("Second number") b: Int
    ): Int = a + b
}
```

### Phase 2: ChatScopes ✅ **COMPLETE**
**Goal**: Define ChatScopes as scoped collections of tools with customizable system messages

**Status**: Phase 2 is **100% complete** with all objectives achieved.

**What Was Implemented**:

1. **Scope-Based Tool & System Message Filtering**:
   - `@ChatService(scope = [...])` scopes an entire service to specific scopes
   - `@Tool(scope = [...])` restricts individual tools to scopes (with parent validation)
   - `@SystemMessage(scope = [...])` restricts system messages to scopes
   - Empty scope arrays mean "available to all scopes" (backwards compatible)

2. **Named System Messages** (replacing index-based approach):
   - `@SystemMessage(name = "custom_name")` provides stable name
   - Auto-generated names from method/field names if not explicit
   - Registry built at startup with fail-fast duplicate detection
   - YAML references use meaningful names, not fragile indices

3. **Inline System Messages** (YAML-defined per scope):
   - `aimo.scope.{scopeId}.system-messages: {id: "text"}` for scope-specific prompts
   - Merged with pre-defined `@SystemMessage` beans
   - Full flexibility for custom prompts without code changes

4. **Scope Inheritance & Validation**:
   - Parent `@ChatService.scope` defines scope bounds
   - Child `@Tool` and `@SystemMessage` scopes must be subsets
   - Intersection validation with fail-fast errors
   - Comprehensive test coverage (15 new unit tests)

5. **Runtime Scope Selection**:
   - Builder method: `withChatScope(scopeId)` for explicit selection
   - Conversation metadata storage via `setSelectedChatScope(scopeId)`
   - Fallback chain: explicit → conversation metadata → global scope
   - Scope filtering happens at ChatClient build time

6. **Configuration**:
   - Scopes pre-defined in `application.yml` under `aimo.scope.*`
   - Each scope lists `tool-refs` and `system-message-refs`
   - Global scope always available with all tools
   - Full YAML documentation with examples

**Breaking Changes**:
- ⚠️ `tool-filter` → `tool-refs` (in YAML scope definitions)
- ⚠️ `system-message-filter` → `system-message-refs` (in YAML scope definitions)
- ⚠️ System message refs now use names instead of indices
- API remains backwards compatible (new features are additive)

**Test Coverage**: 15 new comprehensive unit tests in ChatScopeTest and InlineSystemMessageCallbackTest

For detailed ChatScope documentation and examples, see:
- **README.md**: "Chat Scopes (Phase 2)" section with examples
- **AGENTS.md**: "Chat Scopes (Phase 2)" technical section
- **COMPLETION_REPORT.md**: Full implementation details


### Phase 3: Spring Security
**Goal**: Provide optional Spring Security integration via interceptors

**Status**: Ready to implement. Phase 2 provides the scope foundation.

**Overview**:
- Spring Security module provides pre-built interceptors
- Interceptors hook into ChatClient and ChatScopeProvider to enforce security
- Users register the interceptors via the builder — no special-purpose wiring needed
- Uses standard Spring Security annotations (`@Secured`, `@PreAuthorize`) on tools

**Key Features**:
1. **Tool Security**: Declare access control via annotations
   ```kotlin
   @Tool(description = "Admin operation")
   @PreAuthorize("hasRole('ADMIN')")
   fun adminOperation(): String { ... }
   ```

2. **Scope Access Control**: Filter available scopes by user permissions
   - ChatScopeProvider interceptor filters scopes based on authentication
   - Only scopes accessible to current user are available

3. **Integration with Builder**:
   ```kotlin
   chatClientBuilderFactory
       .builder(conversation)
       .withSecurityContext(securityContext)  // Optional
       .build()
   ```

**⚠️ User Concept Decision**:
The codebase has existing user/security infrastructure:
- `AimoUserProvider` - provides current user from context
- `AimoUser` - holds userId and metadata  
- `GlobalUserProvider` - default (single-user mode)
- `AimoSecurityConfig` - registers default

**Decision Pending**: Before Phase 3 implementation, clarify strategy:
1. Remove custom user concept and rely entirely on Spring Security
2. Bridge custom user concept to Spring Security (Spring principal → AimoUserProvider)

This decision affects DAO access control and user scoping approach.

### Phase 4: Reusable Kotlin/Java Aimo Client
**Goal**: Extract and publish a standalone, reusable Kotlin/Java client for Aimo

**⚠️ Not the Same as In-JVM BuilderFactory**:
This client is an **HTTP client** for communicating with a remote Aimo server. It is the external-facing API wrapper. It is NOT the same as the internal `BuilderFactory` or `AimoChatClient` used inside the JVM. Do not conflate the two.

**Features**:
- Type-safe client for communicating with Aimo backend
- Handle ChatClient requests and streaming responses
- Support for scope/model selection
- Message history management
- Reusable across different JVM applications and tools
- **Critical for Phase 5**: Enables remote server communication for scope forwarding
- Published on Maven Central for easy consumption

**Usage Contexts**:
- Remote Scope Forwarding: Tools use client to call other Aimo instances
- Standalone JVM Applications: Integrate Aimo into non-UI JVM services
- Sample applications and tools

**Deliverables**:
- Published package to custom Maven repository
- Clear API documentation
- Kotlin/Java types for all Aimo concepts
- Example usage in sample applications

### Phase 5: Chat Client Forwarding
**Dependencies**: Requires Phase 4 (Kotlin/Java Aimo Client) for remote server communication
**Goal**: Support streaming tool output to other chat clients or scopes (in-JVM and remote)

**Definition**:
- Tools can internally call other chat clients or scopes
- Response streams are forwarded through the tool output
- Enables scope-to-scope communication and nested chat flows

**Forwarding Modes**:
1. **In-JVM Forwarding**: 
   - Tools call other chat clients/scopes within the same JVM
   - Direct API invocation via builders
   - No network overhead
   
2. **Remote Aimo Requests**: 
   - Tools use Kotlin/Java Aimo Client to call remote Aimo instances
   - HTTP-based communication to other Aimo deployments
   - Streaming responses from remote instances
   - Client library provides type-safe remote access

**Use Cases**:
- Tool calls another scope to handle sub-tasks
- Tool streams external chat responses back to the main conversation
- Nested scope chains where one scope's output feeds another
- Distributed scope networks across multiple Aimo instances

**Implementation**:
- Tools can instantiate and invoke chat clients or scopes at runtime
- Support for both local and remote scope invocation
- Response streaming is passed through tool output
- Tool execution includes async/stream support for long-running operations

### Phase 6: Server API Enhancements
**Goal**: Extend server API for flexible model specification and message manipulation (testing/admin)

**Model Specification in Requests**:
- Chat requests can specify either:
  1. **Predefined Model**: Name of a configured model (e.g., `"gpt-4"`)
  2. **Custom Model Configuration**: Inline model config with provider, model, and settings
- Builders accept both forms and resolve to appropriate chat service
- Enables ad-hoc model testing without pre-configuration

**Message Management** (for testing):
- API endpoint to delete messages from a conversation
- API endpoint to edit/update messages (regardless of source)
- Useful for testing agent behavior, cleaning up conversations, correcting messages

### Phase 7: Guard-Rails
**Goal**: Implement lightweight interceptors for ChatClient request/response validation and enrichment

**Definition**:
- Guard-rails are interceptors registered on the ChatClient
- They use the same generic interceptor interface as security and other cross-cutting concerns
- Leverage fast, lightweight LLMs for quick checks and operations
- Enable content moderation, safety checks, and response enhancement

**Key Features**:
- **ChatClient Request Interception**: Check incoming ChatClient requests before processing
  - Input validation using lightweight LLMs
  - Content safety checks
  - Message enrichment or transformation

- **ChatClient Response Interception**: Check outgoing ChatClient responses before returning
  - Output validation
  - Safety verification
  - Response formatting or enhancement

- **Configuration & Interceptor Integration**:
  - Guard-rails defined in application.yaml under `aimo.guard-rails`
  - Use lightweight model selection (configured in `aimo.model`)
  - Integrated as interceptors in the builder pipeline
  - Applied automatically when builder chains ChatClient

### Phase 8: Additional Model Providers
**Goal**: Add first-party support for OpenAI (ChatGPT) and Anthropic as model providers

**New Modules**:
- `aimo-model-openai` — OpenAI provider (ChatGPT, GPT-4, etc.)
- `aimo-model-anthropic` — Anthropic provider (Claude models)

**Existing Providers** (already implemented):
- `aimo-model-ollama` — Ollama (local models)
- `aimo-model-bedrock` — AWS Bedrock

**Design**:
- Follow the existing `AimoChatModelProviderFactory` pattern used by Ollama and Bedrock
- Configured under `aimo.model` in application.yaml alongside existing providers
- Drop-in addition — no core changes required


---

## Frontend

**Philosophy**: 
- Default tooling will be retired; users implement custom UI mechanisms
- UI serves dual purpose: simple chatbot AND advanced testing/debugging tool
- Quick deployment: Stand up a functional chatbot in minutes with minimal configuration
- Advanced features: Built-in debugging and testing capabilities for developers and power users
- Primary focus: Flexible, extensible UI that works for both simple and advanced use cases

### Phase 0: Reusable TypeScript Aimo Client
**Goal**: Extract and publish a standalone, reusable TypeScript client for Aimo

**⚠️ Already Partially Exists**:
There are already hand-maintained TypeScript API wrappers in `aimo-ui/src/api/aimo-client` and `aimo-ui/src/api/aimo-ui-client`. This phase extracts and formalizes them into a proper standalone package. Do not rewrite from scratch — build on what exists.

**Features**:
- Type-safe client for communicating with Aimo backend
- Handle ChatClient requests and streaming responses
- Support for scope/model selection
- Message history management
- Reusable across different UI implementations
- Published on npm for easy consumption

**Usage Contexts**:
- Frontend debugging/chatbot UI
- Custom UI implementations
- Third-party integrations

**Deliverables**:
- Published npm package
- Clear API documentation
- TypeScript types for all Aimo concepts
- Example usage in debugging tool and custom UIs

### Phase 1: ChatScope & Model Selection
**Goal**: UI components for users to select chat scopes and models

**ChatScope Selector**:
- Dropdown/modal showing available chat scopes
- Display scope name and description
- Select a scope when creating a conversation
- Scope selection influences which tools and system messages are used

**Model Selector**:
- Dropdown to choose a provider/LLM + configuration combination
- Can be per-conversation or global default
- Display available model configurations based on deployment setup
- Each model configuration includes provider, LLM, and settings (temperature, max tokens, etc.)

### Phase 2: Context Visualization
**Goal**: Show which conversation history is included in the current context window

### Phase 3: Model Comparison
**Goal**: Compare responses from multiple models side-by-side

### Phase 4: ChatScope Debugging Tool
**Goal**: Comprehensive debugging interface for chat scopes and ChatClient behavior

**Foundation**:
- Built using TypeScript Aimo Client (Phase 0)
- Runs alongside or as part of the main UI

**Key Features**:
- **Scope Execution Trace**: Track scope execution flow and decisions
- **Tool Call Inspector**: View tool calls, parameters, and results
- **Message History Debugging**: Inspect which messages are included in context
- **System Message Display**: Show active system messages for the current scope
- **Model Configuration Display**: Show which model configuration is active
- **Request/Response Inspector**: View raw ChatClient requests and responses
- **Guard-Rail Monitoring**: Display guard-rail validations and transformations

**Primary Use Cases**:
- Debug scope behavior during development
- Understand how tool calls and responses flow through the system
- Test different scope configurations and models
- Verify context inclusion and message filtering
- Monitor guard-rail behavior in real-time
- Troubleshoot scope forwarding (in-JVM and remote)


