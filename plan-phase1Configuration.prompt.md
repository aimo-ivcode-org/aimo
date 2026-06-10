# Plan: Phase 1 Configuration — BuilderFactory & Runtime Builders

**TL;DR**: Replace the monolithic `Aimo` facade with a `BuilderFactory` + `Builder` pattern for flexible runtime composition. Bootstrap from `application.yaml` properties under `aimo.*` (models, agents, guard-rails). Interceptors provide a unified mechanism for cross-cutting concerns. Phase 1 removes `Aimo` entirely; Phase 1.5 renames `@ChatController` to `@ChatService` for semantic clarity.

## Architectural Layers

Phase 1 clarifies three distinct layers:

- **`AimoChatModel`** (renamed from `AimoChatEngine`) — Raw LLM execution
  - Provider-specific interface for executing individual chat calls
  - No knowledge of history, tool orchestration, or multi-turn logic
  - Examples: Ollama, Bedrock, OpenAI APIs
  - Responsibility: Single-turn model invocation only

- **`AimoChatClient`** — Orchestration layer for history and tool calls
  - Manages multi-turn conversations (loop on tool calls, retries, etc.)
  - Composes system messages with history for each model invocation
  - Handles tool call detection, invocation, and result integration
  - Depends on `AimoChatModel` for raw LLM execution
  - Depends on `Conversation` for history storage
  - Responsibility: Chat orchestration, tool handling, history composition

- **`Conversation`** — History storage (scoped to a chat by `chatId`)
  - Read/write chat message history
  - Manage conversation metadata and properties
  - Interface for persistent storage (replaces `AimoChatClientImpl`)
  - Can be wrapped with `ConversationInterceptor` for security, auditing, caching
  - Responsibility: Durable conversation history

**Data Flow**: HTTP Request → Builders → `AimoChatClient` → `AimoChatModel` (+ `Conversation` for history)

## Naming Clarifications

Two distinct `AimoChatModel` concepts exist, which must be distinguished:

- **`AimoChatModel` interface** (raw LLM engine)
  - Already exists as renamed `AimoChatEngine`
  - Provider-specific interface for single-turn chat execution
  - Will be renamed to `AimoChatModel` as part of Phase 1 (in step 6)

- **`AimoChatModel` data class** (configuration wrapper) → **RENAME TO `AimoChatModelConfig`**
  - Currently exists as `data class AimoChatModel` in `aimo-core/src/main/kotlin/org/ivcode/aimo/core/model/AimoChatModel.kt`
  - Wraps a chat engine with metadata: name, options, context, isPrimary flag
  - Used in factory return types: `AimoChatModelProviderFactory.createAimoChatModel()` returns `AimoChatModelConfig?`
  - Used in factory methods: `ChatClientBuilderFactory.getPrimaryModel()` returns `AimoChatModelConfig`
  - **Action**: Rename to `AimoChatModelConfig` to avoid naming collision after interface rename
  - **All references updated** throughout the plan and codebase after rename

## Steps

### 1. Define core builder interfaces (`aimo-core`)

Create foundational abstractions for the builder and interceptor pattern:

- **`ConversationInterceptor`** interface: Separate interceptor for conversation (DAO) operations
  - Chain pattern: `intercept(chain: Chain, context: MutableMap<String, Any>): Any?`
  - Chain interface: `fun proceed(context: MutableMap<String, Any>): Any?`
  - Operation-specific parameters stored in context: `messages`, `property`, `value`, etc.
  - Used for: security/access control, auditing, caching, data transformation at DAO level
  - **NOT interchangeable with ChatClientInterceptor** — different operations, different signatures
  - Example: `SecurityConversationInterceptor(userId)` checks user owns conversation before DAO access

- **`ChatClientInterceptor`** interface: Separate interceptor for chat client operations
  - Chain pattern: `intercept(chain: Chain, context: MutableMap<String, Any>): AimoChatResponse`
  - Chain interface: `fun proceed(context: MutableMap<String, Any>): AimoChatResponse`
  - Operation-specific parameters stored in context: `message`, etc.
  - Used for: guard-rails, security filtering, logging, tracing, error handling, caching at chat request level
  - **NOT interchangeable with ConversationInterceptor** — different operations, different signatures
  - Example: `GuardRailsInterceptor` validates user input and filters response

- **`ConversationBuilder<T>`** interface: Fluent API for building wrapped conversations
  - Methods: `withInterceptor(interceptor: ConversationInterceptor): ConversationBuilder<T>`, `build(): Conversation`
  - Registers conversation-level interceptors (only ConversationInterceptor, not ChatClientInterceptor)
  - All methods return `ConversationBuilder<Conversation>` for chaining
  - Note: T is always Conversation; generic for potential future extensions

- **`ChatClientBuilder<T>`** interface: Fluent API for building chat clients
  - Methods: `withConversation(conversation: Conversation)`, `withModel(name: String)`, `withAgent(name: String)`, `withInterceptor(interceptor: ChatClientInterceptor)`, `build(): T`
  - Generic parameter `T` = the type being built (e.g., `ChatClient`)
  - Registers ChatClient-level interceptors (only ChatClientInterceptor, not ConversationInterceptor)
  - All methods return `ChatClientBuilder<T>` for chaining

- **`ChatClientBuilderFactory`** interface: Entry point for creating both builders
  - Methods: 
    - `conversationBuilder(conversation: Conversation): ConversationBuilder<Conversation>` — Builder for wrapping a conversation with ConversationInterceptors
    - `builder(): ChatClientBuilder<ChatClient>` — Default chat client builder
    - `builder(conversation: Conversation): ChatClientBuilder<ChatClient>` — Chat client builder with pre-bound conversation
  - Manages application-level state (predefined models, agents, guard-rails)
  - Initialized from properties on startup
  - **Provider-implemented**: Each model provider (Ollama, Bedrock, OpenAI, etc.) and other subsystems can provide alternative or specialized factory implementations
  - Default implementation provided by `aimo-core`; providers can override for custom behavior

- **Interceptor chains are NOT interchangeable**:
  - `ConversationInterceptor` handles DAO operations: `getMessages()`, `addMessages(messages)`, `writeChatProperty(property, value)`, etc.
  - `ChatClientInterceptor` handles chat operations: `chat(message)`, `chatStream(message)`, etc.
  - Different operation signatures = different parameter contexts = separate interfaces
  - This separation ensures type safety and clarity

### 2. Create properties configuration model (`aimo-core`)

**First, define the `Conversation` interface** (scoped DAO access):

- **`Conversation`** interface: DAO operations scoped to a specific `chatId`
  - Represents chat history/metadata storage abstraction for a single conversation
  - Methods (similar to `AimoConversationClient` but chat-ID scoped):
    - `getMessages(maxCacheCharacters: Long? = null): List<AimoChatMessage>?` — Fetch conversation history
    - `addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long? = null)` — Append messages
    - `getChatMetadata(): Map<String, Any>` — Read durable metadata
    - `readChatMetadata(): Map<String, Any>` — Read durable metadata (explicit naming)
    - `getChatProperty(property: String): Any?` — Read single property
    - `readChatProperty(property: String): Any?` — Read single property (explicit naming)
    - `writeChatProperty(property: String, value: Any)` — Write/update property
    - `deleteChatProperty(property: String): Boolean` — Delete property
  - **Supports interceptors**: Can be wrapped with `ConversationInterceptor` for concerns like:
    - **Security/Access Control**: Enforce that user can only access their own conversation
    - **Data Filtering**: Filter or redact conversation data based on user role/permissions
    - Caching/memoization of getMessages
    - Auditing all conversation writes
    - Data transformation on read/write (encryption, schema migration)
  - **Implementation: `ConversationImpl`**
    - Constructor: `ConversationImpl(chatId: UUID, conversationStore: ConversationStore, userId: String)`
    - Stores `chatId` and `conversationStore` as fields
    - All methods delegate to store with `chatId` for filtering (e.g., `conversationStore.getMessages(chatId)`)
    - `userId` field available for security/access control checks
  - Can be implemented by memory, file, RDS, MongoDB, etc. (same backing stores as existing DAO)
  - Caller (HTTP endpoint) creates `Conversation` instance: `ConversationImpl(chatId, conversationStore, userId)`
  - Passes to `ConversationBuilder` which wraps with interceptors
  - Builder can register conversation interceptors via `withInterceptor(conversationInterceptor)`

Then define properties configuration:

- **`AimoModelProperties`**: `aimo.models` section
  - Define providers (ollama, bedrock, etc.) and their configured models
  - Structure: `aimo.models.{provider}.{modelName}.{settings}`
  - Fields: `baseUrl`, `region`, `primary`, `context`, `options`, etc.

- **`AimoAgentProperties`**: `aimo.agents` section (Phase 2 preparation)
  - Define predefined agents with names, descriptions, system messages, tool scoping
  - Structure: `aimo.agents.{agentId}.{setting}`

- **`AimoGuardRailProperties`**: `aimo.guardRails` section (Phase 7 preparation)
  - Define guard-rail interceptors, lightweight model selections
  - Structure: `aimo.guardRails.{ruleId}.{setting}`

- **`AimoProperties`** (root): Collect all sub-properties
  - `aimo.data-dir`: Conversation storage directory (existing)
  - `aimo.global-user-id`: Single-user mode (existing)
  - `aimo.models`: Model definitions
  - `aimo.agents`: Agent definitions
  - `aimo.guard-rails`: Guard-rail configurations

- **Spring validators**: Validate logical constraints on startup
  - At most one model marked `primary=true` globally
  - Provider-local `primary` constraints (already exist in factories)

### 3. Implement built-in interceptors

Create base interceptor types for common cross-cutting concerns. Implementations are **separate** for ConversationInterceptor and ChatClientInterceptor:

#### ChatClientInterceptor implementations (for chat request-level concerns)

- **`LoggingInterceptor`**: Generic logging for chat operations
  - Implements `ChatClientInterceptor` with chain pattern
  - On `intercept()`: 
    - Extract context (chatId, requestId, userId, message, operation parameters)
    - Log chat entry with parameters from context
  - Call `chain.proceed(context)` to pass context downstream
  - After return: Log results/exceptions, include context in log output
  - Configurable log level per operation type

- **`TracingInterceptor`**: Distributed tracing support for chat operations
  - Implements `ChatClientInterceptor` with chain pattern
  - On `intercept()`: 
    - Extract or create trace ID from context
    - Start span with operation parameters from context
  - Call `chain.proceed(context)` to pass context downstream
  - After return: 
    - Tag span with result, error status, model name, etc. from context
    - Mark span complete
  - Propagate trace context through context map downstream

- **`ErrorHandlingInterceptor`**: Standardized error mapping and recovery for chat
  - Implements `ChatClientInterceptor` with chain pattern
  - On `intercept()`: 
    - Store operation parameters from context for retry (e.g., message, model)
  - Call `chain.proceed(context)` inside try-catch with exception handling
  - On exception: 
    - Convert provider-specific exceptions to `ChatException` hierarchy
    - Include context (chatId, requestId, message, model) in error details
    - Apply retry logic using stored parameters if applicable
    - Return fallback value if configured
  - Re-throw or return result

#### ConversationInterceptor implementations (for DAO-level concerns)

- **`SecurityConversationInterceptor`**: Access control for conversation DAO access
  - Implements `ConversationInterceptor` with chain pattern
  - On `intercept()`: 
    - Extract context (chatId, userId, owner information)
    - Verify user has permission for the operation (DAO method name from operation parameter)
  - Call `chain.proceed(context)` if authorized
  - On unauthorized: Throw `AccessDeniedException` before proceeding
  - Returns DAO result unchanged

- **`AuditingConversationInterceptor`**: Log all conversation DAO operations
  - Implements `ConversationInterceptor` with chain pattern
  - On `intercept()`: 
    - Extract context (operation, parameters)
    - Log "Before" entry
  - Call `chain.proceed(context)` to execute DAO operation
  - After return: Log result, if exception occurred log error
  - Returns DAO result unchanged or re-throws exception

### 4. Build ChatClientBuilderFactory implementation (`aimo-core`)

Create the Spring beans that orchestrate both conversation and chat client creation:

- **`ChatClientBuilderFactoryImpl`**: Core implementation and central registry
  - Constructor: accepts `AimoProperties`, `AimoChatModelProviderFactory` map, `ConversationStore`, tools, system messages
  - **On construction** (when Spring bean is instantiated):
    - Scan `application.yaml` for model properties
    - Call each `AimoChatModelProviderFactory` to **register available models** (initialization happens once)
    - Validate primary model constraint (exactly one, or one per provider if multiple providers)
    - **Register agents** from `aimo.agents` properties (Phase 2)
    - **Register guard-rails** from `aimo.guard-rails` properties (Phase 7)
    - Initialize default interceptors (Logging, Tracing, Error Handling)
  - Factory then becomes a **singleton registry** for the application lifetime
   - Methods (query the registry, never re-register):
     - `conversationBuilder(conversation: Conversation): ConversationBuilder<Conversation>` — Create wrapped conversation
     - `builder(): ChatClientBuilder<ChatClient>` — Default chat client builder
     - `builder(conversation: Conversation): ChatClientBuilder<ChatClient>` — Chat client builder with pre-bound conversation
     - `getAvailableModels(): List<ModelInfo>` — List configured models
     - `getPrimaryModel(): AimoChatModelConfig` — Get the default model
     - `getAgent(agentId: String): Agent?` — Look up agent by ID (Phase 2)

- **`ConversationBuilderImpl`**: Builder for wrapping conversations with ConversationInterceptors
  - Holds: conversation, registered conversation interceptors (ConversationInterceptor only), parent factory
  - Fluent API:
    - `withInterceptor(interceptor: ConversationInterceptor): ConversationBuilder` — Register conversation-level interceptor
    - `build(): Conversation` — Wrap conversation and apply interceptors
  - Build logic:
    - Wrap base conversation with all registered ConversationInterceptors (in order)
    - Return wrapped conversation ready for use
    - **Note**: Only accepts ConversationInterceptor, not ChatClientInterceptor

- **`ChatClientBuilderImpl`**: Concrete builder for `ChatClient`
   - Holds: conversation (optional), selected model, selected agent, registered chat client interceptors (ChatClientInterceptor only), parent factory
   - Fluent API:
     - `withConversation(conversation: Conversation): ChatClientBuilder` — Set conversation
     - `withModel(name: String): ChatClientBuilder` — Select model by name
     - `withModel(config: AimoChatModelConfig): ChatClientBuilder` — Use inline model config (Phase 6)
     - `withAgent(agentId: String): ChatClientBuilder` — Select agent (Phase 2)
     - `withInterceptor(interceptor: ChatClientInterceptor): ChatClientBuilder` — Register chat-level interceptor
     - `build(): ChatClient` — Construct and apply interceptors
     - **Note**: Only accepts ChatClientInterceptor, not ConversationInterceptor
   - Build logic:
     - Resolve model (use selected, or default from `ChatClientBuilderFactory`)
     - Resolve agent (use selected, or determine from conversation metadata, or use default)
     - Create base context map with execution metadata (`chatId`, `requestId`, `conversation-client`, `userId`, `requestMetadata`, etc.)
     - Create `ChatClient` instance (core implementation) with resolved model, agent, conversation, tools filtered by agent scope (Phase 2)
     - **Create wrapper/proxy** that captures all method calls with parameters:
       - When someone calls `chatClient.chat(message)`, wrapper intercepts it
       - Wrapper adds operation parameters to context (e.g., `context["message"] = message`)
       - Wrapper builds interceptor chain with this context
       - Each interceptor can read/modify context and parameters
       - Final link calls core ChatClient method with current context/parameters
       - Result flows back up through interceptor chain
     - Apply all registered ChatClient interceptors to the wrapper (in order)
     - Return wrapped client ready for use

### 5. Implement builders and defer construction

The **`ConversationBuilder`** and **`ChatClientBuilder`** patterns defer configuration and assembly until `build()` is called:

#### Conversation Builder

- **Deferred composition**: Conversation interceptors collected until `build()` called
  - Allows per-request security, auditing, and caching decisions
  - Enables request-scoped conversation wrapping (isolation)
  - No global state mutation during building

- **Interceptor chain**: Chain of responsibility pattern applied in registration order
  - First registered interceptor is the outermost link (executes first)
  - Wrapper captures conversation method calls (e.g., `getMessages()`, `addMessages(messages)`) with parameters
  - Operation parameters stored in context (e.g., `context["messages"] = messages`)
  - Each interceptor receives context with `chatId`, `userId`, operation parameters, etc.
  - Each interceptor can read/modify context before calling `chain.proceed(context)`
  - Each interceptor calls `chain.proceed(context)` to pass context to next link
  - Last link in chain accesses base `Conversation` (DAO) using parameters from context
  - When `proceed(context)` returns, all downstream interceptors have completed

#### ChatClient Builder

- **Deferred composition**: All configuration collected in fields until `build()` called
  - Allows runtime customization per request
  - Enables per-request interceptor chains (isolation)
  - No global state mutation during building

- **Conversation binding**: `ChatClientBuilder` accepts pre-built `Conversation` instance
  - Conversation is already wrapped with its security/audit interceptors
  - ChatClient just passes through to the conversation for storage access
  - Separation of concerns: conversation security separate from chat validation

- **Model resolution**: If builder doesn't specify a model
  - Use primary model from `ChatClientBuilderFactory`
  - Fallback to default in `AimoProperties`

- **Agent resolution**: If builder doesn't specify an agent (Phase 2 preparation)
  - Try reading `agentId` from conversation metadata
  - Fallback to default agent (if defined in properties)
  - Fallback to "global" or "default" agent

- **Interceptor chain**: Chain of responsibility pattern for ChatClient
  - First registered interceptor is the outermost link (executes first)
  - Wrapper captures ChatClient method calls (e.g., `chat(message)`) with parameters
  - Operation parameters stored in context (e.g., `context["message"] = message`)
  - Each interceptor receives context map with `chatId`, `requestId`, `userId`, operation parameters, `requestMetadata`, etc.
  - Each interceptor can read/modify context and parameters before calling `chain.proceed(context)`
  - Each interceptor calls `chain.proceed(context)` to pass context to next link
  - Last link in chain calls the actual `ChatClient` method using parameters from context
  - When `proceed(context)` returns, all downstream interceptors have completed, result flows back up chain

### 6. Remove the `Aimo` facade entirely

Phase 1 **replaces** the monolithic `Aimo` interface with the builder pattern. The old facade is completely removed:

- **Delete `Aimo` interface and `AimoImpl` class**:
  - `aimo-core/src/main/kotlin/org/ivcode/aimo/core/Aimo.kt` — DELETED
  - `aimo-core/src/main/kotlin/org/ivcode/aimo/core/AimoImpl.kt` — DELETED
  - This is a breaking change — applications must migrate to builder pattern

- **Delete `AimoConversationClient` interface**:
  - `aimo-core/src/main/kotlin/org/ivcode/aimo/core/AimoConversationClient.kt` — DELETED
  - Replaced by `Conversation` interface with interceptor support
  - `Conversation` is simpler and more focused (single conversation scoped to chatId + DAO, not user-scoped at interface level)

- **Delete or refactor `AimoChatClient` interface** (if no other uses):
  - Review `aimo-core/src/main/kotlin/org/ivcode/aimo/core/AimoChatClient.kt` for other dependencies
  - If only used by Aimo facade → DELETE
  - If used elsewhere → KEEP and update as needed

- **Update all imports** across the codebase:
  - Remove any imports of `Aimo`, `AimoImpl`, `AimoConversationClient`
  - Update to use `Conversation`, `ConversationBuilder`, `ChatClientBuilder` instead

- **Migration path**: Applications must update to use builders directly
  - HTTP endpoints: Load conversation from store → create `ConversationImpl(chatId, conversationStore, userId)` → build via `ConversationBuilder` + `ChatClientBuilder`
  - Tests: Use builders with mock `Conversation` instances
  - Admin/management: Provide new APIs that work with builders (not through `Aimo`)

- **All examples must be updated**:
  - `examples/simple-ollama`: Remove Aimo usage, use builders
  - `examples/simple-bedrock`: Remove Aimo usage, use builders
  - Show builder pattern in documentation as the standard approach

- **Tools and system messages**: Still discovered via `@ChatService` reflection
  - Injected into builders at construction time (same as before)
  - No API changes to tool/message registration

### 7. Add Phase 1.5 preparation: Rename `@ChatController` to `@ChatService` and package reorganization

Execute the annotation rename and package reorganization:

- **Rename entire package**:
  - `org.ivcode.aimo.core.controller` → `org.ivcode.aimo.core.chatservice`
  - Move ALL files from controller package to chatservice package:
    - `Annotations.kt` (contains `@ChatService`, `@ChatController`)
    - `ChatControllerEntity.kt` → `ChatServiceEntity.kt`
    - `ControllerHelpers.kt`
    - `FieldSystemMessageCallback.kt`
    - `MethodAimoToolCallback.kt`
    - `MethodSystemMessageCallback.kt`
    - `PropertySystemMessageCallback.kt`
    - `ReflectionHelpers.kt`
    - `SystemMessageCallback.kt`
    - `SystemMessageContext.kt`

- **Rename annotation and entity**:
  - `@ChatController` → `@ChatService` (keep `@ChatController` as `@Deprecated` alias)
  - `ChatControllerEntity` → `ChatServiceEntity`

- **Update `AimoConfig.kt`**:
  - Update imports: `org.ivcode.aimo.core.controller.*` → `org.ivcode.aimo.core.chatservice.*`
  - Change `ctx.getBeansWithAnnotation<ChatController>()` → `ctx.getBeansWithAnnotation<ChatService>()`
  - Update all `ChatControllerEntity` references → `ChatServiceEntity`
  - Update all `ReflectionHelpers` calls (now from chatservice package)

- **Update all usages across entire codebase**:
  - `aimo-plugin-ui`: Update imports, rename `@ChatController` → `@ChatService`
  - `examples/simple-ollama`: Update imports, rename `@ChatController` → `@ChatService`
  - `examples/simple-bedrock`: Update imports, rename `@ChatController` → `@ChatService`
  - Any other modules referring to controller package
  - All imports from `org.ivcode.aimo.core.controller` → `org.ivcode.aimo.core.chatservice`

- **Deprecation strategy**:
  - Add `@Deprecated` to `@ChatController` with migration message
  - Keep `@ChatController` as alias in Annotations.kt for backwards compatibility during transition
  - This allows gradual migration of external code

### 8. Update documentation & examples

Provide clear migration and usage guidance:

- **Builder pattern example** in README or new doc:
  ```kotlin
  val userId = getCurrentUser()  // From auth context
  val chatId = getChatId()       // From request
  
  // Step 1: Create Conversation from store (scoped to chatId + userId)
  val conversation = ConversationImpl(chatId, conversationStore, userId)
  
  // Step 2: Build wrapped conversation (applies security, auditing, caching)
  val secureConversation = chatClientBuilderFactory
    .conversationBuilder(conversation)
    .withInterceptor(SecurityConversationInterceptor(userId))  // Enforce user ownership
    .withInterceptor(AuditingInterceptor())                    // Audit conversation access
    .withInterceptor(CachingInterceptor())                     // Cache getMessages
    .build()
  
  // Step 3: Build chat client with the secure conversation
  val chatClient = chatClientBuilderFactory
    .builder(secureConversation)  // Use the wrapped conversation
    .withAgent("default")
    .withModel("gpt-4")
    .withInterceptor(GuardRailsInterceptor())                 // Guard-rails on chat
    .build()
  
  val response = chatClient.chat("user message")
  ```

- **`application.yaml` template**:
  - Add `aimo.models.*` section with example Ollama and Bedrock configs
  - Add `aimo.agents.*` section (empty/example for Phase 2)
  - Add `aimo.guardRails.*` section (empty/example for Phase 7)
  - Mark primary model clearly

- **Migration guide** (for applications updating from Phase 0):
  - Old pattern removed: `Aimo` facade no longer exists
  - New pattern: Three-step process with explicit separation of concerns
    - Step 0: Create `Conversation` from store, scoped to `chatId` + `userId`: `ConversationImpl(chatId, conversationStore, userId)`
    - Step 1: Build wrapped conversation with security/auditing interceptors
    - Step 2: Build chat client with guard-rails/logging interceptors
  - Why three steps? Access control happens at DAO level (Step 1), before any chat logic (Step 2)
  - Show how to update `ChatService` beans to `@ChatService`
  - Link to Phase 2 for agent scoping details

- **Integration points doc**:
  - Clarify `ChatService` (@annotation) vs `ChatService` (@server class) naming
  - Show how HTTP endpoints use builders
  - Show how tools/system messages are discovered and scoped

## Further Considerations

### Dual Interceptor Chains: Conversation and ChatClient (Separate Builders)

**Architecture**: Interceptors work at two levels, with **separate builders** to enforce separation of concerns:

#### Conversation Builder & Chain
- **Responsibility**: Wrap base `Conversation` with ConversationInterceptors (DAO-level concerns)
- **Interceptor type**: `ConversationInterceptor` (separate interface, not interchangeable with ChatClientInterceptor)
- **Built separately**: `chatClientBuilderFactory.conversationBuilder(baseConversation)`
- **Primary use case: Security & Access Control**
  - Verify user owns the conversation before granting DAO access
  - Filter conversation data based on user permissions/roles
  - Prevent unauthorized modifications to shared conversations
- Other use cases: auditing, caching, data transformation at DAO level
- Registered via `builder.withInterceptor(conversationInterceptor)`
- Once built, returns a wrapped `Conversation` instance with ConversationInterceptor chain applied
- Same wrapped instance is then passed to `ChatClientBuilder`

#### ChatClient Builder & Chain
- **Responsibility**: Build chat client with one or more models, agents, tools (chat request-level concerns)
- **Interceptor type**: `ChatClientInterceptor` (separate interface, not interchangeable with ConversationInterceptor)
- **Built separately**: `chatClientBuilderFactory.builder(secureConversation)`
- **Primary use cases**: Guard-rails, security filtering, logging, tracing at chat request level
- Registered via `builder.withInterceptor(chatClientInterceptor)`
- Applied once during `build()` with factory defaults + builder-level interceptors
- Uses the pre-built, secure (wrapped) conversation for all storage access

**Two-step flow**:
```
HTTP Request (userId, chatId)
  ↓
Load raw conversation from store
  ↓
Create Conversation instance (scoped to chatId + store)
  val conversation = ConversationImpl(chatId, conversationStore, userId)
  ↓
Build wrapped conversation (adds security, auditing, caching interceptors)
  ConversationBuilder.conversationBuilder(conversation)
    .withInterceptor(SecurityConversationInterceptor(userId))
    .withInterceptor(AuditingInterceptor())
    .build()
      ↓ (wraps with ConversationInterceptor chain)
      Returns: Conversation (with security enforced at store level)
  ↓
Build chat client with wrapped conversation (adds guard-rails, logging interceptors)
  ChatClientBuilder.builder(secureConversation)
    .withInterceptor(GuardRailsInterceptor())
    .build()
      ↓ (builds ChatClient with wrapped conversation + factory defaults)
      Returns: ChatClient (ready to use)
  ↓
chatClient.chat(request)
  ↓
Return streamed response
```

**Benefits of separation**:
- **Clear separation of concerns**: Store access ≠ Conversation security ≠ Chat validation
- **Explicit scoping**: `ConversationImpl(chatId, conversationStore, userId)` makes store scope explicit
- **Security-first**: Access control happens in Step 1 (Conversation interceptors) before any chat logic
- **Reusability**: Same wrapped conversation can be passed to multiple chat clients if needed
- **Testability**: Can test conversation interceptors independently from chat client interceptors
- **Composition**: Each step is independently composable with its own interceptors

### Two-Tier Interceptor Architecture

**Recommendation**: Implement both factory-level (global defaults) and builder-level (request-specific) interceptors.

#### Factory-Level Interceptors (Global Defaults)

These are automatically applied to **every** `ChatClient` created:

- **`LoggingInterceptor`** — Always present
  - Configured via `application.yaml` (enable/disable, log level)
  - Applied by default when `ChatClientBuilderFactory` creates builders
  
- **`TracingInterceptor`** — Always present
  - Configured via `application.yaml` (enable/disable)
  - Applied by default for observability

- **`ErrorHandlingInterceptor`** — Always present 
  - Configured via `application.yaml` (enable/disable, retry strategy)
  - Applied by default for resilience

**Benefit**: Core concerns (observability, error handling) work "for free" across all requests. Users don't need to think about these.

#### Builder-Level Interceptors (Request-Specific)

Added via `Builder.withInterceptor()` for request-specific needs:

- **Guard-Rails** — Request-specific validation/transformation
- **Security Filtering** — Request-specific access control
- **Custom Domain Logic** — One-off interceptors

**Benefit**: Flexibility for advanced use cases without boilerplate in the common path.

#### Priority & Ordering

**Builder-level interceptors have HIGHER priority** (execute first in the chain):

```
Builder Interceptor (outermost, executes FIRST) ← HIGHEST PRIORITY
  ↓
Factory Interceptor (Logging)
  ↓
Factory Interceptor (Tracing)
  ↓
Factory Interceptor (Error Handling) (innermost, executes LAST)
  ↓
Core ChatClient
```

**Execution flow**:
1. Builder interceptors invoke `chain.proceed()`
2. Factory interceptors invoke `chain.proceed()`
3. Core `ChatClient` method executes
4. Control returns through all interceptors (innermost to outermost)

**Benefits of this ordering**:
- Request-level concerns can reject/transform before factory handlers
- Factory defaults provide safety net (logging, error recovery)
- "Most specific wins" principle: builder choices override factory defaults

#### Implementation Details

**`ChatClientBuilderFactoryImpl` initialization**:
```kotlin
class ChatClientBuilderFactoryImpl(...) {
    private val defaultInterceptors = listOf(
        LoggingInterceptor(),      // Reads config from AimoProperties
        TracingInterceptor(),      // Reads config from AimoProperties
        ErrorHandlingInterceptor() // Reads config from AimoProperties
    )
    
    fun conversationBuilder(conversation: Conversation): ConversationBuilder {
        return ConversationBuilderImpl(conversation)
    }
    
    fun builder(): ChatClientBuilder {
        return ChatClientBuilderImpl(null, defaultInterceptors.toMutableList())
    }
    
    fun builder(conversation: Conversation): ChatClientBuilder {
        return ChatClientBuilderImpl(conversation, defaultInterceptors.toMutableList())
    }
}
```

**Separate builder usage**:
```kotlin
val userId = getCurrentUser()
val baseConversation = conversationStore.load(chatId)

// Step 1: Build secure conversation (separate concern)
val secureConversation = chatClientBuilderFactory
    .conversationBuilder(baseConversation)
    .withInterceptor(SecurityConversationInterceptor(userId))
    .withInterceptor(AuditingInterceptor())
    .build()

// Step 2: Build chat client (separate concern)
val chatClient = chatClientBuilderFactory
    .builder(secureConversation)
    .withInterceptor(GuardRailsInterceptor())
    .build()
```

**Chain construction**:
- `ConversationBuilder.build()`: Wraps conversation with registered interceptors (in order)
- `ChatClientBuilder.build()`: 
  - Resolves model/agent
  - Creates context map
  - Creates ChatClient with conversation reference
  - Wraps with factory defaults + builder-level interceptors (in order)

#### Configuration & Properties

**Factory interceptors configured in `AimoProperties`**:

```yaml
aimo:
  interceptors:
    logging:
      enabled: true
      level: INFO
    tracing:
      enabled: true
    errorHandling:
      enabled: true
      maxRetries: 3
      retryBackoffMs: 100
```

**Builder interceptors** are programmatically registered; no YAML config needed (they're part of application logic).

### Tool & System Message Agent Scoping (Phase 2 Dependency)

Phase 1 discovers all tools/messages globally. Phase 2 adds `agents` property to `@ChatService`, `@Tool`, and `@SystemMessage` for runtime filtering.

**Phase 1 preparation**:
- Store agent binding information in `ChatServiceEntity` (even if empty)
- Design `Builder` to accept filtered tool/message lists
- Do NOT enforce agent filtering in Phase 1; default to "all agents" for all components


### Conversation Abstraction

**`Conversation` interface relationship to existing code**:
- Essentially replaces `AimoConversationClient` in the new pattern (simpler, single-responsibility name)
- Scopes all store operations to a specific `chatId`
- Methods mirror `AimoConversationClient` (getMessages, addMessages, metadata access)

**Implementation: `ConversationImpl`**:
- Constructor: `ConversationImpl(chatId: UUID, conversationStore: ConversationStore, userId: String)`
  - `chatId`: The conversation to scope all operations to
  - `conversationStore`: The underlying store (memory, file, RDS, etc.)
  - `userId`: Stored for use by security interceptors (optional, for access control)
- All methods delegate to store with `chatId` for filtering:
  - `getMessages()` → `conversationStore.getMessages(chatId)`
  - `addMessages(messages)` → `conversationStore.addMessages(chatId, messages)`
  - `writeChatProperty(key, value)` → `conversationStore.writeChatProperty(chatId, key, value)`
  - etc.
- When wrapped with `ConversationInterceptor`s, interceptors can see `userId` in context for access control decisions

**Usage**:
- HTTP endpoint loads conversation from store: `val conv = conversationStore.loadConversation(chatId, userId)` (or similar)
- Creates `Conversation` instance: `Conversation impl = ConversationImpl(chatId, conversationStore, userId)`
- Passes to builder: `conversationBuilder(impl).withInterceptor(...).build()`
- Enables testability: Tests pass mock `Conversation` instances without needing full store setup

### Security Filtering with Conversation Interceptors

**Use case: Enforce user-scoped conversation access**

Current architecture scopes conversations by `chatId`. But who can access a conversation? Security interceptors on `Conversation` enable fine-grained access control:

**Example: User-scoped conversation listing**:
```kotlin
// HTTP endpoint receives userId from auth context
val userId = getCurrentUser()

// Load conversation from store
val conversation = conversationStore.loadConversation(chatId)

// Wrap conversation with security interceptor
val secureConversation = chatClientBuilderFactory
    .builder()
    .withConversation(conversation)
    .withConversationInterceptor(SecurityConversationInterceptor(userId)) // Check user ownership
    .build()
```

**`SecurityConversationInterceptor` logic**:
```kotlin
class SecurityConversationInterceptor(
    private val userId: String
) : ChatClientInterceptor<Conversation> {
    override fun intercept(chain: Chain<Conversation>, context: Map<String, Any>): Conversation {
        // Verify user owns this conversation
        val conversationOwnerId = chain.proceed().readChatProperty("userId") as? String
        if (conversationOwnerId != userId) {
            throw AccessDeniedException("User $userId cannot access conversation owned by $conversationOwnerId")
        }
        
        // Return wrapped conversation that filters data by user
        return SecureConversationWrapper(chain.proceed(), userId)
    }
}
```

**Benefits**:
- Security logic is decoupled from `ChatClient` implementation
- Same `Conversation` interface works for both secure and insecure contexts
- Easy to compose multiple security rules (role-based, ownership, etc.)
- Works seamlessly with HTTP endpoints: load conversation → wrap with security → build chat client

**Conversation listing scenario**:
```kotlin
// HTTP endpoint: GET /aimo-api/conversation?userId=alice
val allConversations = conversationStore.listAllConversations()

// Filter by user (security responsibility)
val userConversations = allConversations.filter { conv ->
    val owner = conv.readChatProperty("userId") as? String
    owner == userId
}

return userConversations.map { it.id }
```

This filtering happens at the HTTP service layer. Conversation interceptors enforce access control at the DAO layer, preventing unauthorized access even if bugs exist at higher levels.

### Interceptor Context Lifecycle

**Context availability and mutation**:
- Context map is created per request/chat call with standard keys: `chatId`, `requestId`, `conversation-client`, `requestMetadata`
- Interceptors can read and modify context; modifications propagate to downstream interceptors
- Modifications in interceptors do NOT persist across separate chat calls (each call gets a fresh context)
- Interceptors should not assume modifications are visible to upstream interceptors (only downstream)
- Use context for request-scoped data (tracing IDs, request metadata, temporary state)

### Version & Release Timing

- Phase 1 should be a minor version bump (e.g., `0.2.0`)
- Phase 1.5 can be included in same release or follow-up minor (e.g., `0.2.1`)
- Establish cadence for subsequent phases

## Downstream Module Changes (Required across all modules)

Phase 1 core API changes require updates to all modules that depend on `aimo-core`. The following changes are mandatory:

### aimo-server (HTTP API layer)

**Changes needed**:
- **`ChatController.kt` (HTTP endpoints)**:
  - Remove dependency on `Aimo` facade
  - Replace with direct use of:
    - `ConversationStore` (injected) for loading conversations
    - `ChatClientBuilderFactory` (injected) for building clients
  - `POST /aimo-api/chat/{chatId}` endpoint flow:
    - Load `ConversationImpl(chatId, conversationStore, userId)` from auth context
    - Build via `builderFactory.conversationBuilder().withInterceptor(...).build()`
    - Build via `builderFactory.builder(secureConversation).build()`
    - Call `chatClient.chat()` and stream response
  - Update all endpoints that previously called `Aimo` methods
  - Add builder composition for request-scoped interceptors

- **`ChatService.kt` (service layer)**:
  - Merge request metadata into context before passing to builder
  - No longer wraps `AimoConversationClient`
  - Works directly with `ChatClient` instances

- **Dependency injection**:
  - Inject `ChatClientBuilderFactory` instead of `Aimo`
  - Inject `ConversationStore` for loading conversations
  - Update Spring configuration if needed

- **Import updates**:
  - Remove `org.ivcode.aimo.core.Aimo`, `org.ivcode.aimo.core.AimoImpl`
  - Add `org.ivcode.aimo.core.builder.*`
  - Add `org.ivcode.aimo.core.ConversationImpl`

### aimo-plugin-ui (Tool & system message discovery)

**Changes needed**:
- **`@ChatService` discovery** (renamed from `@ChatController`):
  - Update package imports: `org.ivcode.aimo.core.controller` → `org.ivcode.aimo.core.chatservice`
  - Change `@ChatController` → `@ChatService` on any tool/message beans
  - Reflection discovery still works (names changed, logic same)

- **Tool/message registration in builders**:
  - Tools and system messages are injected into `ChatClientBuilderFactory` at startup
  - No changes to tool/message callback interfaces
  - Annotation usage remains the same (just renamed)

### aimo-model-ollama, aimo-model-bedrock (Model providers)

**Changes needed**:
- **`AimoChatModelProviderFactory` implementations**:
  - Constructor signature unchanged (still accepts properties, etc.)
  - Registration flow with factory unchanged
  - Models are still registered with `ChatClientBuilderFactory`

- **Dependency updates**:
  - Update imports if referencing removed types
  - No implementation changes to model logic itself
  
- **If providers create clients directly**:
  - Update any internal uses of `Aimo` facade → use builders instead
  - Unlikely in provider modules, but verify

### examples/simple-ollama, examples/simple-bedrock (Example applications)

**Changes needed** (BREAKING - must be updated):
- **Remove `Aimo` usage completely**:
  - Delete any `Aimo` injection
  - Delete any `createConversation()` calls

- **Add three-step builder pattern**:
  ```kotlin
  // Step 1: Create Conversation from store
  val conversation = ConversationImpl(chatId, conversationStore, userId)
  
  // Step 2: Build wrapped conversation
  val secureConversation = builderFactory
    .conversationBuilder(conversation)
    .withInterceptor(SecurityInterceptor(userId))
    .build()
    
  // Step 3: Build chat client
  val chatClient = builderFactory
    .builder(secureConversation)
    .build()
  ```

- **Update HTTP endpoints** (if any):
  - Same pattern as aimo-server

- **Update integration tests**:
  - Tests previously using `Aimo` must use builders
  - Mock `Conversation` instead of mocking `Aimo`

- **Verify composition**:
  - Check that `aimo-server` + model provider works with new builder pattern
  - Check that examples run end-to-end

### aimo-ui (React frontend)

**Changes needed** (if any):
- **No direct API changes**:
  - Frontend calls HTTP endpoints (aimo-server), not aimo-core directly
  - As long as HTTP API contract remains same, frontend mostly unaffected
  - May need minor updates if endpoint behavior changes

- **If frontend has integration tests**:
  - Update mock responses if builder pattern changes response format
  - Unlikely to be significant

### Summary of Scope

| Module | Change Type | Severity | Impact |
|---|---|---|---|
| aimo-core | Core API refactor | HIGH | Interfaces added, facades removed |
| aimo-server | Integration update | HIGH | Must use builders instead of Aimo |
| aimo-plugin-ui | Package rename | MEDIUM | Annotation rename only |
| aimo-model-ollama | Dependency update | LOW | Import updates, logic unchanged |
| aimo-model-bedrock | Dependency update | LOW | Import updates, logic unchanged |
| examples/* | Integration update | HIGH | Must use builders, APIs removed |
| aimo-ui | Possible minor update | LOW | Depends on endpoint changes |

## Success Criteria

1. ✅ `Conversation` interface defined with chat-scoped DAO operations + ConversationInterceptor support
2. ✅ `ConversationInterceptor` interface provides separate interceptor chain for DAO-level concerns (security, auditing, caching)
3. ✅ `ConversationBuilder` interface/implementation enables wrapping conversations with ConversationInterceptors
4. ✅ `ChatClientInterceptor` interface provides separate interceptor chain for chat request-level concerns (guard-rails, logging, tracing)
5. ✅ `ChatClientBuilderFactory` provides separate methods: `conversationBuilder()` and `builder()` for two-step composition
6. ✅ `ChatClientBuilder` API accepts pre-built `Conversation` instances and fluent model/agent/interceptor selection
7. ✅ Dual interceptor chains work independently:
   - `ConversationBuilder` builds conversation security chains (ConversationInterceptor)
   - `ChatClientBuilder` builds chat request chains (ChatClientInterceptor + factory defaults)
8. ✅ Properties configuration model reads models, agents, guard-rails, interceptors from YAML
9. ✅ `Aimo` facade **completely removed** — files deleted:
   - `aimo-core/src/main/kotlin/org/ivcode/aimo/core/Aimo.kt` — DELETED
   - `aimo-core/src/main/kotlin/org/ivcode/aimo/core/AimoImpl.kt` — DELETED
   - `aimo-core/src/main/kotlin/org/ivcode/aimo/core/AimoConversationClient.kt` — DELETED
   - All imports and references to these removed
10. ✅ `ConversationImpl` implementation created:
   - Constructor: `ConversationImpl(chatId: UUID, conversationStore: ConversationStore, userId: String)`
   - Wraps store operations scoped to chatId
11. ✅ HTTP endpoints and service layer updated to use builders instead of `Aimo`
12. ✅ `examples/simple-ollama` and `examples/simple-bedrock` updated to use builders (no Aimo usage)
12. ✅ `@ChatController` renamed to `@ChatService` throughout codebase
13. ✅ Migration guide and examples show two-step builder pattern (build conversation, then chat client)
14. ✅ All tests pass; no references to removed `Aimo` interface
