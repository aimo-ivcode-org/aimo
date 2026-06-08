# AIMO Roadmap: Goals

## Backend

### Phase 1: Configuration
**Goal**: Make the system configurable through properties and runtime builders

**Application Properties** (application.yaml):
- Define predefined models and their configurations (under `aimo.models`)
- Define agents and their tool scoping (under `aimo.agents`)
- Base configuration for the system
- Minimal changes to the existing property structure

**Conversation Model** (Terminology Change):
- Conversations represent a chat's message history
- Storage is abstracted: memory, file, RDS, MongoDB, etc.
- Conversations are passed to builders as input
- No longer create chat clients; instead, builders consume conversations

**Runtime Builder Architecture**:
- **BuilderFactory**: Entry point for runtime configuration
  - Initializes from application properties
  - Creates builder instances for runtime customization
  
- **Builders**: Composable instances for specific runtime scenarios
  - Accept a conversation as input
  - Users configure runtime behavior (model, agent, tools, etc.)
  - Each builder specifies its own configuration
  - Builders return configured chat clients or agents
  
**Example Flow**:
1. Application starts with properties-based configuration
2. Conversation (history) is loaded from storage
3. BuilderFactory creates builder instances
4. Users invoke builders, passing in the conversation
5. Builders return configured chat clients ready to execute

**Example Usage** (Illustrative - API not fixed):
```
Conversation conversation = loadConversation(conversationId)

ChatClient chatClient = builderFactory
  .builder()
  .withConversation(conversation)
  .withAgent("admin")
  .withModel("gpt-4")
  .build()

chatClient.chat("user message")
```

*Note: This is a conceptual example to show the builder pattern. The actual API may differ.*

### Phase 1.5: Refactor ChatController to ChatService
**Goal**: Rename ChatController to ChatService for clearer semantics

**Changes**:
- Rename `ChatController` to `ChatService` throughout the codebase
- Updates core architecture terminology to reflect business logic focus
- Foundational change before implementing new phases

### Phase 2: Agents
**Goal**: Define agents as scoped collections of tools with customizable system messages

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
  - Manages predefined agents from registry
  - Supports runtime agent creation without registration
  - Integrates with interceptors for access control

- **Interceptors**: Filter agents based on context
  - Apply user permissions if security is enabled
  - Can restrict access to certain agents based on roles
  - Optional: no interceptors if security is disabled

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
- `@ChatService(agents = {"admin", "public"})`: Scope service to specific agents
- `@Tool(agents = {"admin", "retrieval"})`: Scope tool to specific agents
- `@SystemMessage(agents = {"admin"})`: Scope system message to specific agents
- If no agents are specified, the component is available to all agents

### Phase 3: Spring Security
**Goal**: Provide optional Spring Security integration
- Configuration and setup
- Interceptors for hooking into the system

### Phase 4: Reusable Kotlin/Java Aimo Client
**Goal**: Extract and publish a standalone, reusable Kotlin/Java client for Aimo

**Features**:
- Type-safe client for communicating with Aimo backend
- Handle ChatClient requests and streaming responses
- Support for agent/model selection
- Message history management
- Reusable across different JVM applications and tools
- **Critical for Phase 5**: Enables remote server communication

**Deliverables**:
- Published package on Maven Central for easy consumption
- Clear API documentation
- Kotlin/Java types for all Aimo concepts
- Example usage in sample applications and tools

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
   - Direct API invocation
   
2. **Remote Aimo Requests**: 
   - Tools can forward requests to remote Aimo instances
   - HTTP-based communication to other Aimo deployments
   - Streaming responses from remote instances

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
- Guard-rails use interceptors to intercept ChatClient requests and responses
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

- **Interceptor Integration**:
  - Hooks into existing interceptor framework (same as agents, tooling)
  - Configurable via application.yaml under `aimo.guardRails`
  - Lightweight model selection for fast execution


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

**Features**:
- Type-safe client for communicating with Aimo backend
- Handle ChatClient requests and streaming responses
- Support for agent/model selection
- Message history management
- Reusable across different UI implementations (debugging tool, custom UIs, etc.)

**Deliverables**:
- Published npm package for easy consumption
- Clear API documentation
- TypeScript types for all Aimo concepts
- Example usage in debugging tool

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


