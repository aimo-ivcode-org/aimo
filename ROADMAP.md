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
   - Empty scope arrays inherit the parent `@ChatService` scope when the parent is scoped; available to all scopes only when parent has no scope restriction

2. **Named System Messages** (stable references, not index-based):
   - `@SystemMessage(name = "custom_name")` provides explicit stable name
   - Auto-generated names from method/field names if not explicit (e.g., `methodName()` → `"methodName"`)
   - Registry built at startup with fail-fast duplicate detection
   - YAML references use meaningful names: `system-message-refs: ["power_user_capabilities"]`
   - Example system messages in test suite:
     - `global_context`, `power_user_capabilities` (GlobalTools)
     - `public_scope_intro` (PublicTools)
     - `admin_scope_warning` (AdminTools)
     - `research_scope_intro` (ResearchTools)
     - `multi_scope_intro` (MixedTools)

3. **Inline System Messages** (YAML-defined per scope):
   - `aimo.scope.{scopeId}.system-messages: {id: "text"}` for scope-specific prompts
   - Full message text defined directly in YAML without separate bean
   - Merged at runtime with pre-defined `@SystemMessage` beans
   - Example: `power_user_inline: "You are a power user with elevated privileges..."`
   - Full flexibility for custom prompts without code changes or recompilation

4. **Scope Inheritance & Validation**:
   - Parent `@ChatService.scope` defines scope bounds
   - Child `@Tool` and `@SystemMessage` scopes must be subsets of parent
   - Intersection validation with fail-fast errors at startup
   - `inherit-global: true/false` controls whether global (unrestricted) tools/messages are included
   - Comprehensive test coverage (15 unit tests in `aimo-core`)

5. **Runtime Scope Selection**:
   - Builder method: `withChatScope(scope)` for explicit scope selection (takes a ChatScope object, not a string)
   - Scope resolution: explicit selection or default to global scope
   - Scope filtering happens at ChatClient build time (not runtime)
   - Different conversations can have different scopes independently via per-request scope selection

6. **Scope Configuration in YAML**:
   - Scopes pre-defined in `application.yml` under `aimo.scope.*`
   - Each scope lists tools via `tool-refs: ["toolName1", "toolName2"]`
   - Each scope lists system messages via `system-message-refs: ["messageName1"]`
   - Inline system messages via `system-messages: {msgId: "text"}`
   - Global scope always available with all unrestricted tools and messages
   - Full YAML documentation with working examples

7. **Use Cases Demonstrated**:
   - **Case 1 - Isolated Scope**: `restricted` scope with `inherit-global: false` excludes all global tools
   - **Case 2 - Cherry-Picked Scope**: `power_user` scope with `inherit-global: true` combines:
     - Global tools (help, status)
     - Explicitly referenced tools from multiple scopes (add, multiply from public; deleteConversation from admin; searchPapers from research)
     - Named system message references
     - Inline system messages

**Breaking Changes**:
- ⚠️ `tool-filter` → `tool-refs` (in YAML scope definitions)
- ⚠️ `system-message-filter` → `system-message-refs` (in YAML scope definitions)
- ⚠️ System message indexing removed; all references now use stable names
- API remains backwards compatible (new features are additive)

**Test Coverage**: 
- 15 comprehensive unit tests located in `aimo-core/src/test/kotlin/org/ivcode/aimo/core/chatscope/ChatScopeDemoTest.kt`
- Tests verify tool filtering, system message scoping, named references, inline messages, and inheritance patterns
- Isolated test configuration (no model provider dependency) allows fast, focused testing of scope logic
- All tests passing

**Files Changed**:
- Core implementation in `aimo-core/src/main/kotlin/org/ivcode/aimo/core/conf/AimoConfig.kt`
- Test agents in `aimo-core/src/test/kotlin/org/ivcode/aimo/core/chatscope/ChatScopeDemoAgents.kt`
- Test configuration in `aimo-core/src/test/kotlin/org/ivcode/aimo/core/chatscope/TestChatScopeConfig.kt`
- Test YAML in `aimo-core/src/test/resources/application-scope-demo.yml`

For detailed ChatScope documentation and examples, see:
- **README.md**: "Chat Scopes (Phase 2)" section with examples
- **AGENTS.md**: "Chat Scopes (Phase 2)" technical section
- **aimo-core tests**: 15 integration tests demonstrating all features

#### Missed Requirement: Programmatic Scope Builder
**Status**: Future work. Can be added post-Phase 2 to enhance scope management capabilities.

**Problem**: Current implementation only supports scope configuration via YAML or annotation discovery. There's no programmatic way to:
- Build custom scopes at runtime from scratch
- Add `ToolCallback` instances to scopes dynamically
- Parse annotated classes and extract tools/system messages programmatically
- Create scopes from MCP server definitions at runtime

**Solution**: Implement a **ChatScopeBuilder** fluent API:

```kotlin
// Builder for creating scopes programmatically
val customScope = ChatScopeBuilder(id = "custom_research")
    .displayName("Custom Research")
    .description("Dynamically built research scope")
    // Parse @ChatService annotated class
    .withAnnotatedService(MyResearchService::class)
    // Add individual tools manually
    .withToolCallback(myToolCallback1)
    .withToolCallback(myToolCallback2)
    // Add system messages
    .withSystemMessage(mySystemMessageCallback)
    .withSystemMessageByName("research_guide")
    .build()  // Returns ChatScope
```

**Implementation Phases**:

1. **Phase 2.5a: Annotation-Based Builder**
   - `ChatScopeBuilder.withAnnotatedService(clazz)` — parse `@ChatService` class, extract `@Tool` and `@SystemMessage` members
   - Reuse existing reflection/annotation discovery logic from `AimoConfig`
   - Validate scope constraints (subset validation)

2. **Phase 2.5b: Manual Tool Registration**
   - `ChatScopeBuilder.withToolCallback(callback)` — manually add individual tools
   - `ChatScopeBuilder.withSystemMessage(callback)` — manually add individual messages
   - Support for creating one-off tools without needing `@ChatService` beans

3. **Phase 2.5c: MCP Integration** (deferred until Phase 3)
   - `ChatScopeBuilder.withMcpServerTools(serverId, toolNames)` — cherry-pick MCP tools by name (use `"{serverId}:{toolName}"` naming)
   - Requires MCP tool registry to be accessible (inject `List<ScopedToolCallback>` from `aimo-mcp`)
   - Support glob patterns: `"claude-desktop:*"` to include all tools from a server

**Files to Create** (Missed Requirement):
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatscope/ChatScopeBuilder.kt`
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/chatscope/ChatScopeBuilderImpl.kt`
- `aimo-core/src/test/kotlin/org/ivcode/aimo/core/chatscope/ChatScopeBuilderTest.kt`

**Why This Matters**:
- Enables dynamic scope creation at runtime (not just YAML/annotations)
- Allows frameworks/applications to programmatically compose scopes from multiple sources
- Supports runtime scope modifications (add/remove tools from a scope)
- Necessary for advanced use cases: multi-tenant scoping, user-specific scopes, A/B testing different tool sets

**Rationale for Deferred Implementation**:
- Should have been part of Phase 2 but wasn't prioritized initially
- Can be added as a post-Phase 2 enhancement without blocking Phase 3
- YAML-based configuration sufficient for initial use cases
- Will inform future programmatic APIs for other components


### Phase 3: MCP Tool Consuming
**Goal**: Enable AIMO agents to discover and consume MCP tools from external MCP servers

**Status**: ✅ IMPLEMENTED - MCP client with tools and prompts integration (July 2026)

**Overview**:
- ✅ AIMO agents can discover MCP tools from external servers (Claude Desktop, Cline, etc.)
- ✅ External MCP tools are wrapped and exposed as AIMO `@Tool` resources
- ✅ Tools integrate seamlessly with scope and system message architecture
- ✅ Tool results flow naturally through conversation context
- ✅ MCP prompts (system messages) are discovered and included in chat context
- ✅ Prompts respect scope restrictions and are named `{serverId}:{promptName}`

**Key Features**:
- ✅ MCP Client: Connect to external MCP servers and discover available tools and prompts
- ✅ Tool Wrapping: Auto-wrap MCP tool definitions as AIMO `@Tool` resources
- ✅ Prompt Wrapping: Auto-wrap MCP prompts as AIMO `@SystemMessage` resources
- ✅ Schema Conversion: Convert MCP tool/prompt schemas to AIMO parameter definitions
- ✅ Scope Integration: Wrapped tools and prompts respect scope restrictions
- ✅ Multi-Server Support: Connect to multiple MCP servers simultaneously
- ✅ Dynamic Updates: Handle `tools/listChanged` and `prompts/listChanged` notifications
- ✅ Refresh Support: Manual and periodic re-discovery of tools and prompts

**Use Cases**:
- External Tool Consumption: Integrate tools from Claude Desktop, Cline, other MCP servers
- External Prompt Consumption: Get domain-specific system messages and context from MCP servers
- Multi-Agent Coordination: One agent calls another agent's MCP-exposed tools
- Third-Party Tool Integration: Quickly add specialized tools without code changes
- Contextual Instructions: Integrate server-provided prompts for specific workflows

**Deliverables**:
- ✅ MCP client implementation with server and prompt discovery
- ✅ Tool wrapping/conversion framework
- ✅ Prompt wrapping/conversion framework
- ✅ Integration with existing `@Tool`, `@SystemMessage` and scope infrastructure
- ✅ Example workflows showing MCP tool and prompt consumption
- ✅ Documentation on connecting to external MCP servers and using prompts

### Phase 3.5: MCP Tool Providing
**Goal**: Enable AIMO to expose its tools as an MCP server for external agents to consume

**Status**: Design phase - requires further thinking on architecture and protocol details.

**Overview**:
- AIMO exposes its `@ChatService` tools as discoverable MCP resources
- External MCP clients can discover and invoke AIMO tools
- Tool schemas are auto-generated from `@Tool` annotations
- Remote agents can integrate AIMO tools into their workflows

**Pending Design Decisions**:
- How to expose scoped tools via MCP (scope filtering on server side)
- Authentication/authorization for MCP server access
- Tool invocation context propagation (request metadata, conversation context)
- Resource lifecycle management for long-running tool operations
- Rate limiting and resource quotas for external consumers

**Status**: Blocked on design clarity - will revisit after Phase 3 (MCP Consuming) learnings

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
**Goal**: Extend conversation-server API for flexible model specification and message manipulation (testing/admin). Rename `aimo-server` module to `aimo-conversation-server` to clarify this module provides conversation chat APIs, not necessarily agent-specific functionality.

**Module Rename**:
- `aimo-server` → `aimo-conversation-server`
- Clarifies the module's purpose: conversation chat management, not generic server infrastructure
- Updates all references in documentation, Gradle configuration, and examples

**API Enhancements**:
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

### Phase 9: Agentic Flows & Multi-Agent Coordination
**Goal**: Enable complex, self-directed agent flows with inter-agent communication

**Status**: Depends on Phase 3 (MCP Tooling) and Phase 5 (Chat Client Forwarding).

**Key Features**:
- Agent Orchestration: Tools can spawn sub-agents, delegate work to remote agents via MCP or HTTP
- Work Distribution: Distribute tasks across team scopes with result aggregation and parallel execution
- State & Context Management: Parent/child relationships, shared coordination context, token budget accounting
- Tool Result Streaming: Long-running operations stream results with execution metadata

**Use Cases**:
- Research Agent: Coordinates multiple research sub-agents on different topics
- Code Generation: Manages planning, implementation, and testing agents
- Distributed Processing: Specialized analysis agents for parallel data processing
- Customer Support: Routes to specialized support agents based on need


---

## Nice to Have / Lower Priority

### Spring Security Integration
**Goal**: Provide optional Spring Security integration via interceptors

**⚠️ REQUIREMENT**: All phases must include security hooks/extension points to support Spring Security implementation anytime, even if not built-in.

**Overview**:
- Spring Security module provides pre-built interceptors
- Interceptors hook into ChatClient via existing `ChatClientInterceptor` mechanism
- Standard Spring Security annotations (`@Secured`, `@PreAuthorize`) on tools
- No special-purpose wiring needed — uses builder's interceptor registration

**Implementation Strategy** (when prioritized):
- Use existing interceptor architecture for policy enforcement
- Keep custom user concept as primary security layer
- Bridge to Spring Security if needed via Spring principal → AimoUserProvider

**Why It's Lower Priority**:
- Current focus is agentic flows and multi-agent coordination
- Can be added anytime without disrupting core or existing functionality
- Most agent-to-agent use cases use token/API key security instead


---

## Frontend

**Philosophy**: 
- Primary UI focus: **Conversation comparison tool** for testing and analysis
- Secondary purpose: Simple chatbot interface for basic interactions
- Advanced features: Comprehensive debugging and comparison tools for agentic workflows

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
- Published npm package
- Clear API documentation
- TypeScript types for all Aimo concepts
### Phase 1: Conversation Comparison Tool
**Goal**: Build a powerful conversation comparison UI for testing and analysis

**Core Features**:
- Side-by-Side View: Display two or more conversations for direct comparison
- Model/Scope Testing: Run same prompts against different models or scopes, compare results
- Parameter Variation: Test how temperature, max_tokens, system messages affect outputs
- Agent Flow Comparison: Visualize different agent coordination strategies handling same tasks
- Response Metrics: Display token counts, latency, cost per model
- Diff Highlighting: Show meaningful differences between responses

**Use Cases**:
- Compare model outputs (Ollama vs OpenAI vs Bedrock)
- Test scope filtering (which tools were available, which got called)
- Verify agent coordination patterns (sequential vs parallel)
- Tune agent prompts and system messages
- Debug complex agentic workflows

### Phase 2: ChatScope & Model Selection UI

### Phase 1: ChatScope & Model Selection
**Goal**: UI components for users to select chat scopes and models

**ChatScope Selector**:
- Dropdown/modal showing available chat scopes
- Display scope name and description
- Select a scope when creating a conversation
- Scope selection influences which tools and system messages are used

**Model Selector**:
- Dropdown to choose a provider/LLM + configuration combination

### Phase 3: Context & Execution Visualization
**Goal**: Show conversation context and agent execution flow
- Display available model configurations based on deployment setup
**Context Visualization**:
- Display which conversation history is included in the current context window
- Show token counting and context budget utilization
- Visualize message inclusion/exclusion reasoning
### Phase 2: Context Visualization
**Agent Execution Trace**:
- Track multi-agent workflows and task delegation
- Show which scopes were used for each step
- Display tool calls and results
- Timeline view of agent coordination
### Phase 3: Model Comparison
**MCP Tool Visibility**:
- Show which MCP tools are available in current scope
- Display MCP tool schemas and parameter definitions
- Trace MCP calls between agents

### Phase 4: Advanced Debugging Tool
**Goal**: Comprehensive debugging interface for agent flows and ChatClient behavior
### Phase 4: ChatScope Debugging Tool
**Goal**: Comprehensive debugging interface for chat scopes and ChatClient behavior

**Foundation**:
- Built using TypeScript Aimo Client (Phase 0)
- Runs alongside or as part of the main UI
- Scope Execution Trace: Track scope execution flow and decisions
- Tool Call Inspector: View tool calls, parameters, and results across all agents
- Message History Debugging: Inspect messages included in context per agent
- System Message Display: Show active system messages for each scope
- Model Configuration Display: Show which model configuration is active per agent
- Request/Response Inspector: View raw ChatClient requests and responses
- Guard-Rail Monitoring: Display guard-rail validations and transformations
- MCP Protocol Inspector: View MCP calls, tool schemas, and responses
- Agent Lineage: Show parent/child relationships in multi-agent workflows
- **Request/Response Inspector**: View raw ChatClient requests and responses
- **Guard-Rail Monitoring**: Display guard-rail validations and transformations
- Debug agent behavior during development
- Understand how tool calls flow through multi-agent systems
- Test different scope configurations, models, and agent orchestration patterns
- Verify context inclusion and message filtering across agents
- Test different scope configurations and models
- Troubleshoot scope forwarding and inter-agent communication
- Analyze MCP tool calls and performance
- Monitor guard-rail behavior in real-time
- Troubleshoot scope forwarding (in-JVM and remote)



