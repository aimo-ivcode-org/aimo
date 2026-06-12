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
   - `ChatClientBuilder<T>` provides fluent API for runtime composition
   - Supports model selection, agent binding (Phase 2 ready), and custom interceptors
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
   - Migration guide (MIGRATION-PHASE1.md)
   - Example application documentation

**Example Usage**:
```kotlin
@Service
class ChatService(
    private val conversationFactory: ConversationFactory,
    private val chatClientBuilderFactory: ChatClientBuilderFactory
) {
    fun chat(chatId: UUID, message: String): AimoChatResponse {
        val conversation = conversationFactory.getConversation(chatId)
        val chatClient = chatClientBuilderFactory
            .builder(conversation)
            .withModel("gpt-oss")  // Optional model override
            .build()
        
        return chatClient.chat(AimoChatRequest(userMessage = message))
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

### Phase 2: Agents
**Goal**: Define agents as scoped collections of tools with customizable system messages

**Status**: Ready to implement. Phase 1 and 1.5 provide the foundation.

**⚠️ Current Tool Discovery**:
Tools are currently discovered **globally at startup** via reflection in `AimoConfig.createControllerEntities()`. All `@ChatService` beans are scanned, and ALL their tools are registered. This means there is no filtering — every tool is available to every request today.

Agent scoping must **filter at runtime**, not at startup. The full tool registry is still built at startup, but only the agent's allowed tools are passed to `AimoChatClientImpl` when building a prompt.

**⚠️ `SystemMessageContext` Enhancement Needed**:
`SystemMessageContext` currently only contains `context: Map<String, Any>` with no `agentId`. Adding `agentId` as a field is required for system message selection by agent.

**⚠️ Annotation Enhancement Needed**:
The `agents` property must be added to `@ChatService`, `@Tool`, and `@SystemMessage` annotations. If no `agents` property is set on an annotation, the component is available to all agents (backwards compatible).

**Definition**:
- Agents are named configurations that bind a subset of tools to a specific system message
- Each agent can override default behavior (model selection, parameters, etc.)

**Agent Model**:
```
Agent
  ├── id (unique identifier)
  ├── displayName (user-facing name)
  ├── description (what this agent does)
  ├── systemMessage (custom system prompt)
  ├── tools (list of available tools for this agent)
  └── config (agent-specific settings)
```

**Agent Provider Architecture**:
- **Agent Provider**: Central service for retrieving and creating agents
  - Loads predefined agents from application.yaml (under `aimo.agents`)
  - Supports runtime agent creation without registration
  - Accepts generic interceptors for filtering/access control
  - Initialized by BuilderFactory

- **Interceptors on Agent Provider**: Filter agents based on context
  - Security module provides interceptors that filter by user permissions
  - Applied only if security interceptors are registered (optional)
  - Use the same interceptor interface as ChatClient interceptors
  - Part of broader interceptor framework

**Agent Sources**:
1. **Predefined Agents**: Programmatically or via configuration file
   - Stored in agent registry
   - Managed through configuration
   
2. **Runtime Agents**: Created on-the-fly at runtime
   - No registration required
   - Defined by specifying tools and system message
   - Useful for dynamic agent creation

**Definition Methods**:
1. **Programmatically**: Code-based agent registration
   - Beans/configuration classes define agents
   - Full control over agent setup

2. **Application Configuration**: YAML-based definitions
   - Configured in `aimo.agents` section of application.yaml
   - Easy updates without redeployment

**Agent Registry**:
- Central registry that stores predefined agent definitions
- Query available agents
- Look up agent by ID

**User Interaction**:
- Users select an agent when creating a conversation
- Selected agent determines which tools are available
- Selected agent's system message applies to the conversation
- Agent selection can be changed at conversation level
- Interceptors filter available agents based on permissions (if security enabled)

**Annotation-Based Scoping**:
- `@ChatService(agents = ["admin", "public"])`: Scope service to specific agents
- `@Tool(agents = ["admin", "retrieval"])`: Scope tool to specific agents
- `@SystemMessage(agents = ["admin"])`: Scope system message to specific agents
- If no agents specified, the component is available to all agents (default)

**⚠️ DAO Storage for Agent Binding**:
The conversation's `agentId` will be stored in conversation metadata (the `AimoConversationInfo.metadata` / `Map<String, Any>` that already exists in the DAO). No schema changes are needed for this — it uses the existing `writeChatProperty`/`readChatProperty` mechanism.

### Phase 3: Spring Security
**Goal**: Provide optional Spring Security integration via interceptors
- Spring Security module provides pre-built interceptors
- Interceptors hook into ChatClient and Agent Provider to enforce security
- Users register the interceptors via the builder — no special-purpose wiring needed
- Uses standard Spring Security annotations (`@Secured`, `@PreAuthorize`) on tools

**⚠️ Existing User Concept in Codebase**:
The current codebase already has a user/security concept:
- `AimoUserProvider` interface (`aimo-core/.../security/`) — provides the current user from execution context
- `AimoUser` data class — holds `userId` and `metadata`
- `GlobalUserProvider` — default implementation, always returns "global" user (single-user mode)
- `AimoSecurityConfig` — registers `GlobalUserProvider` as default via `@ConditionalOnMissingBean`

This existing mechanism handles user scoping (all DAO operations are scoped by `userId`). The decision is **pending** on whether to:
1. Remove this custom user concept and rely entirely on Spring Security
2. Bridge it to Spring Security (Spring Security principal populates `AimoUserProvider`)

This decision must be made before implementing Phase 3.

**User Concept**:
- Conversation API defines user context
- Decision pending: Remove existing user concept or integrate with Spring Security
- To be determined in Phase 3 implementation

### Phase 4: Reusable Kotlin/Java Aimo Client
**Goal**: Extract and publish a standalone, reusable Kotlin/Java client for Aimo

**⚠️ Not the Same as In-JVM BuilderFactory**:
This client is an **HTTP client** for communicating with a remote Aimo server. It is the external-facing API wrapper. It is NOT the same as the internal `BuilderFactory` or `AimoChatClient` used inside the JVM. Do not conflate the two.

**Features**:
- Type-safe client for communicating with Aimo backend
- Handle ChatClient requests and streaming responses
- Support for agent/model selection
- Message history management
- Reusable across different JVM applications and tools
- **Critical for Phase 5**: Enables remote server communication for agent forwarding
- Published on Maven Central for easy consumption

**Usage Contexts**:
- Remote Agent Forwarding: Tools use client to call other Aimo instances
- Standalone JVM Applications: Integrate Aimo into non-UI JVM services
- Sample applications and tools

**Deliverables**:
- Published package to custom Maven repository
- Clear API documentation
- Kotlin/Java types for all Aimo concepts
- Example usage in sample applications

### Phase 5: Agent Forwarding
**Dependencies**: Requires Phase 4 (Kotlin/Java Aimo Client) for remote server communication
**Goal**: Support streaming tool output to other agents or chat clients (in-JVM and remote)

**Definition**:
- Tools can internally call other agents or chat clients
- Response streams are forwarded through the tool output
- Enables agent-to-agent communication and nested chat flows

**Forwarding Modes**:
1. **In-JVM Forwarding**: 
   - Tools call other agents/chat clients within the same JVM
   - Direct API invocation via builders
   - No network overhead
   
2. **Remote Aimo Requests**: 
   - Tools use Kotlin/Java Aimo Client to call remote Aimo instances
   - HTTP-based communication to other Aimo deployments
   - Streaming responses from remote instances
   - Client library provides type-safe remote access

**Use Cases**:
- Tool calls another agent to handle sub-tasks
- Tool streams external chat responses back to the main conversation
- Nested agent chains where one agent's output feeds another
- Distributed agent networks across multiple Aimo instances

**Implementation**:
- Tools can instantiate and invoke chat clients or agents at runtime
- Support for both local and remote agent invocation
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
  - Guard-rails defined in application.yaml under `aimo.guardRails`
  - Use lightweight model selection (configured in `aimo.models`)
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
- Configured under `aimo.models` in application.yaml alongside existing providers
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
- Support for agent/model selection
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

### Phase 1: Agent & Model Selection
**Goal**: UI components for users to select agents and models

**Agent Selector**:
- Dropdown/modal showing available agents
- Display agent name and description
- Select an agent when creating a conversation
- Agent selection influences which tools and system message are used

**Model Selector**:
- Dropdown to choose a provider/LLM + configuration combination
- Can be per-conversation or global default
- Display available model configurations based on deployment setup
- Each model configuration includes provider, LLM, and settings (temperature, max tokens, etc.)

### Phase 2: Context Visualization
**Goal**: Show which conversation history is included in the current context window

### Phase 3: Model Comparison
**Goal**: Compare responses from multiple models side-by-side

### Phase 4: Agent Debugging Tool
**Goal**: Comprehensive debugging interface for agents and ChatClient behavior

**Foundation**:
- Built using TypeScript Aimo Client (Phase 0)
- Runs alongside or as part of the main UI

**Key Features**:
- **Agent Execution Trace**: Track agent execution flow and decisions
- **Tool Call Inspector**: View tool calls, parameters, and results
- **Message History Debugging**: Inspect which messages are included in context
- **System Message Display**: Show active system message for the current agent
- **Model Configuration Display**: Show which model configuration is active
- **Request/Response Inspector**: View raw ChatClient requests and responses
- **Guard-Rail Monitoring**: Display guard-rail validations and transformations

**Primary Use Cases**:
- Debug agent behavior during development
- Understand how tool calls and responses flow through the system
- Test different agent configurations and models
- Verify context inclusion and message filtering
- Monitor guard-rail behavior in real-time
- Troubleshoot agent forwarding (in-JVM and remote)


