# Aimo Core

The core framework for multi-turn AI conversations with tool use, system messages, and conversation history management.

## Architecture Overview

Aimo Core provides:
- **Chat Client**: Multi-turn LLM chat with tool calling
- **Conversation Storage**: Durable history with DAO-based persistence and interceptor support
- **Chat Services**: Annotation-driven tool and system message discovery
- **Chat Scopes**: Role-based filtering of available tools and system messages
- **Model Adapters**: Pluggable LLM model providers (Ollama, Bedrock, etc.)

## Key Components

### Chat Client (`AimoChatClientImpl`)

The core chat loop that orchestrates conversations:

```kotlin
val client = builder
    .withModel("gpt-4")
    .build()

val response = client.chat(
    AimoChatRequest(
        prompt = "What is the weather?",
        context = emptyMap()
    )
)
```

**Flow:**
1. Merge request metadata with conversation context
2. Fetch conversation history from DAO (respecting scope metadata)
3. Prompt budget calculation
4. LLM call → receive completion
5. Handle tool calls (if any)
6. Persist prompt + generated messages
7. Return response

### Model & Model Adapters

LLM configuration via `AimoChatModelConfig`:

```kotlin
AimoChatModelConfig(
    name = "ollama-mistral",
    modelId = "mistral:latest",
    provider = OllamaChatModelFactory(),
    primary = true
)
```

**Exactly one primary model must exist** at runtime. Provider-specific factories enforce at most one `primary=true` per provider.

### Chat Service & Discovery

Annotation-based tool and system message registration:

```kotlin
@ChatService(scope = ["admin", "research"])
class MyService {
    
    @Tool(scope = ["admin"])
    fun deleteData(id: String, @ToolParam("reason") reason: String): String {
        // Tool implementation
    }
    
    @SystemMessage(scope = ["research"])
    fun researchContext(): String = "You are a research assistant..."
}
```

**Discovery:**
- Reflection-based at startup
- Scoped visibility via `@ChatService` and `@Tool`/`@SystemMessage` annotations
- Parameter `context: Map<String, Any>` is auto-injected and excluded from JSON schema
- Method signature for system messages: `() -> String?` or `(SystemMessageContext) -> String?`

### Conversation & History Storage

`Conversation` represents a single chat's persistent history:

```kotlin
val conversation = conversationFactory.getConversation(
    chatId = UUID.fromString("..."),
    metadata = mapOf("tenant" to "acme", "userId" to "user123")
)

conversation.getMessages(maxCacheCharacters = 10000)
conversation.addMessages(requestId, messages)
conversation.getChatProperty("title")
conversation.writeChatProperty("title", "My Chat")
```

**Storage:**
- Backed by `AimoChatClientDao` (file or in-memory implementations)
- Scope metadata filters access (e.g., tenant-based isolation)
- Durable metadata stored with conversation
- All reads/writes pass scope metadata for DAO filtering

### Conversation Interceptors

Cross-cutting concerns for conversation operations via `ConversationInterceptor`:

```kotlin
class MyInterceptor : ConversationInterceptor {
    override fun interceptGet(
        chain: ConversationInterceptor.GetChain,
        chatId: UUID,
        metadata: MutableMap<String, Any>
    ): Conversation? {
        // Enrich metadata before DAO operation
        metadata["enriched"] = true

        // Proceed to next interceptor or final DAO call
        val result = chain.proceed(chatId, metadata)

        // Post-process result
        return result
    }

    override fun interceptDelete(
        chain: ConversationInterceptor.DeleteChain,
        chatId: UUID,
        metadata: MutableMap<String, Any>
    ): Boolean {
        // Optionally enrich/validate before delete
        return chain.proceed(chatId, metadata)
    }
}

val conversation = factory
    .withInterceptor(MyInterceptor())
    .getConversation(chatId, metadata)
```

**Typical use cases:**
- **Auditing**: Log all conversation operations
- **Caching**: Memoize message fetches
- **Data transformation**: Encryption, compression
- **Metadata enrichment**: Add scope-specific metadata

### Chat Scopes

Role-based access control for tools and system messages:

```yaml
aimo:
  scope:
    admin:
      tool-refs: ["deleteUser", "suspendAccount"]
      system-message-refs: ["adminContext"]
    research:
      tool-refs: ["queryData", "exportData"]
      system-message-refs: ["researchContext"]
```

**Scope semantics:**
- Every instance has a built-in `"global"` scope (unrestricted)
- `@ChatService(scope=[...])` restricts tool/message visibility
- Empty `scope = []` on tool/message inherits parent `@ChatService` scope
- At startup, scopes are validated as subsets of parent service scope

**Runtime resolution:**
```kotlin
builder
    .withChatScope("admin")  // Explicit scope selection
    .build()
// or defaults to "global" scope
```

## Data Access Object (DAO)

The `AimoChatClientDao` interface abstracts storage:

```kotlin
interface AimoChatClientDao {
    fun getChatConversation(chatId: UUID, scopeMetadata: Map<String, Any>): ChatConversationEntity?
    fun addChatRequest(request: ChatRequestEntity, scopeMetadata: Map<String, Any>): Boolean
    fun getChatRequests(chatId: UUID, scopeMetadata: Map<String, Any>): List<ChatRequestEntity>
    fun upsertConversationMetadata(chatId: UUID, metadata: Map<String, Any>, scopeMetadata: Map<String, Any>): Boolean
    // ... more methods
}
```

**Implementations:**
- `AimoChatClientDaoMemory`: In-memory storage
- `AimoChatClientDaoFile`: File-based JSON storage
- Custom implementations for databases (SQL, NoSQL, etc.)

**Scope Metadata Matching:**
- Empty `scopeMetadata` matches all conversations
- Otherwise, **all entries** in `scopeMetadata` must match stored metadata (AND logic)
- File and Memory DAOs use `ConversationMetadataMatcher` for post-query filtering
- Database implementations should push filtering into SQL WHERE clauses

## Context Management

Well-known context keys throughout the system:

- `"chatId"`: UUID of the current conversation
- `"requestId"`: UUID of the current request
- `"conversation-client"`: Reference to the current `Conversation` instance
- Server adds `"requestMetadata"`: Request-specific context

## Building a Chat Client

The typical flow requires two factories:

```kotlin
// 1. Create a conversation (for history storage)
val dao = AimoChatClientDaoMemory()
val conversationFactory = ConversationFactoryImpl(dao)
val metadata = mapOf("tenant" to "acme", "userId" to "user123")
val entity = dao.createChatConversation(metadata)
val conversation = conversationFactory.getConversation(entity.chatId, metadata)
    ?: error("Conversation not found")

// 2. Build and use the chat client
// (ChatClientBuilderFactory is typically injected or configured in Spring)
val chatClient = chatClientBuilderFactory
    .builder(conversation)  // Pass the conversation
    .withChatScope("admin")  // Optional: select scope
    .withModel("gpt-4")      // Optional: select model
    .build()

// 3. Execute chat
val response = chatClient.chat(
    AimoChatRequest(
        prompt = "Help me with X",
        context = emptyMap()
    )
)
```

**Key points:**
- `ConversationFactory` creates/loads conversations from storage (DAO)
- `ChatClientBuilderFactory` is configured at application startup with models, tools, and scopes
- `builder(conversation)` creates a builder with the conversation already bound
- Optional customization: model selection, scope selection, interceptors
- `build()` composes the final chat client with interceptors and chat scope

## Error Handling

- **Configuration errors**: Fail-fast at startup (e.g., multiple primary models, invalid scope definitions)
- **DAO errors**: Return `null` or `false` for access violations (scope mismatch)
- **LLM errors**: Propagate via exceptions (model unavailable, rate limit, etc.)
