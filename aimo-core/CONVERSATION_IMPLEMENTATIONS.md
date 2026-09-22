# Conversation Implementations

This guide explains the built-in Conversation storage implementations and how to add custom implementations.

## Built-in Implementations

### MemoryConversation

In-memory storage for chat history and metadata.

**Use cases:**
- Development and testing
- Stateless deployments where persistence is not required
- Ephemeral/session-scoped conversations
- Rapid prototyping

**Characteristics:**
- All data lost when application restarts
- No file system dependencies
- Fastest access (pure in-memory)
- Thread-safe with internal synchronization
- Suitable for single-server deployments

**Example:**
```kotlin
val factory = MemoryConversationFactory()
val conversation = factory.createConversation(
    metadata = mapOf("userId" to "user123")
)
```

### FileConversation

File-system backed JSON storage for persistent chat history.

**Use cases:**
- Local development with persistence
- Small to medium deployments
- Single-server scenarios
- Simple, auditable storage

**Characteristics:**
- Conversations persisted to JSON files under configurable data directory
- Each chat gets its own file (e.g., `data/{chatId}.json`)
- Metadata stored inline with messages
- Character budget support for efficient incremental history
- Suitable for development, demos, and small production deployments

**Example:**
```kotlin
val factory = FileConversationFactory(
    dataDir = File("./data/conversations"),
    objectMapper = ObjectMapper()
)
val conversation = factory.createConversation(
    metadata = mapOf("userId" to "user123")
)
```

**File Structure:**
```
data/conversations/
├── 550e8400-e29b-41d4-a716-446655440000/
│   ├── metadata.json
│   └── messages.json
├── 6ba7b810-9dad-11d1-80b4-00c04fd430c8/
│   ├── metadata.json
│   └── messages.json
└── ...
```

**metadata.json** contains chat properties:
```json
{
  "userId": "user123",
  "tenant": "acme"
}
```

**messages.json** contains request groups:
```json
[
  {
    "requestId": "...",
    "createdAt": "2026-09-14T22:30:00Z",
    "messages": [...]
  }
]
```

## Adding Custom Implementations

To implement a custom Conversation backend (e.g., PostgreSQL, MongoDB, DynamoDB):

### 1. Implement the Conversation Interface

```kotlin
class PostgresConversation(
    override val chatId: UUID,
    private val db: DataSource,
    private val scopeMetadata: Map<String, Any>?
) : Conversation {
    
    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
        // Query database for messages
        // Enforce scope metadata
        // Respect character budget
    }
    
    override fun addMessages(
        requestId: UUID, 
        messages: List<AimoChatMessage>, 
        maxCacheCharacters: Long?
    ) {
        // Insert messages into database
        // Group by requestId
        // Enforce scope metadata
    }
    
    override fun getChatMetadata(): Map<String, Any> {
        // Query metadata table
        // Enforce scope metadata
    }
    
    override fun getChatProperty(property: String): Any? {
        // Query metadata table for single property
    }
    
    override fun writeChatProperty(property: String, value: Any) {
        // Upsert metadata
        // Enforce scope metadata
    }
    
    override fun writeChatProperties(properties: Map<String, Any>) {
        // Batch upsert metadata (atomic operation)
        // Enforce scope metadata
    }
    
    override fun deleteChatProperty(property: String) {
        // Delete metadata property
        // Enforce scope metadata
    }
    
    override fun deleteChatProperties(keys: List<String>) {
        // Batch delete metadata properties (atomic operation)
        // Enforce scope metadata
    }
}
```

### 2. Implement ConversationFactory

```kotlin
class PostgresConversationFactory(
    private val db: DataSource,
    private val scopeMetadata: Map<String, Any>? = null
) : ConversationFactory {
    
    private val interceptors = mutableListOf<ConversationInterceptor>()
    
    override fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory {
        interceptors.add(interceptor)
        return this
    }
    
    override fun createConversation(metadata: Map<String, Any>): Conversation {
        val chatId = UUID.randomUUID()
        // Insert into database with metadata
        return buildConversation(chatId, metadata)
    }
    
    override fun getConversation(chatId: UUID, metadata: Map<String, Any>): Conversation? {
        // Query database for conversation
        // Validate scope metadata if required
        if (!validateScope(metadata)) return null
        return buildConversation(chatId, metadata)
    }
    
    override fun getConversations(metadata: Map<String, Any>): List<Conversation> {
        // Query all conversations matching metadata
        // Return list of Conversation instances
    }
    
    override fun deleteConversation(chatId: UUID, metadata: Map<String, Any>): Boolean {
        // Delete from database
        // Validate scope metadata
        return true
    }
    
    private fun buildConversation(chatId: UUID, metadata: Map<String, Any>): Conversation {
        val conv = PostgresConversation(chatId, db, metadata)
        // Wrap with interceptors
        return interceptors.fold(conv as Conversation) { wrapped, interceptor ->
            InterceptorChain.wrapConversation(wrapped, interceptor)
        }
    }
    
    private fun validateScope(metadata: Map<String, Any>): Boolean {
        // Validate metadata matches required scope
        return true
    }
}
```

### 3. Key Implementation Requirements

**Scope Metadata Enforcement:**
- All operations must validate `scopeMetadata` against stored metadata
- If metadata mismatch, return `null` or `false` (deny access)
- Implement `AND` logic: all entries in scopeMetadata must match

**Atomic Operations:**
- `writeChatProperties()` must write all properties atomically
- `deleteChatProperties()` must delete all properties atomically
- Prevents partial failures affecting chat state

**Request Grouping:**
- Messages must be grouped by `requestId` and `createdAt`
- `getHistory()` must return `AimoHistoryRequest` objects with proper grouping
- Enables stateful replay and UI ordering

**Character Budget:**
- `getMessages(maxCacheCharacters)` must return recent history up to character limit
- Efficient for large conversations (avoid loading entire history)
- Calculate from most recent backward

**Error Handling:**
- Throw `IllegalStateException` if operation cannot be performed
- Return `null` if conversation not found (not an error)
- Return `false` if scope metadata denies access

### 4. Spring Configuration

```kotlin
@Configuration
class PostgresConversationConfig {
    
    @Bean
    @Primary
    fun conversationFactory(dataSource: DataSource): ConversationFactory {
        return PostgresConversationFactory(dataSource)
            .withInterceptor(AuditingInterceptor())
    }
}
```

### 5. Testing

Use the provided `ConversationContractTest` as a base for your implementation:

```kotlin
class PostgresConversationTest : ConversationContractTest() {
    
    override fun createConversation(): Conversation {
        return PostgresConversation(
            chatId = UUID.randomUUID(),
            db = testDataSource,
            scopeMetadata = null
        )
    }
    
    @BeforeEach
    fun setup() {
        // Initialize test database
        // Create tables, etc.
    }
}
```

## Migration from DAO

Prior to this refactoring, aimo-core used an abstract `AimoChatClientDao` layer. The new architecture eliminates this:

**Old (DAO-based):**
```kotlin
val dao = AimoChatClientDaoFile(dataDir)
val factory = ConversationFactoryImpl(dao)
val conversation = factory.getConversation(chatId, metadata)
```

**New (direct implementations):**
```kotlin
val factory = FileConversationFactory(dataDir)
val conversation = factory.getConversation(chatId, metadata)
```

Benefits of the new approach:
- **Simpler**: Fewer abstraction layers
- **Type-safe**: No entity wrapper types (use `AimoChatMessage`, `AimoHistoryRequest` directly)
- **Flexible**: Implement `Conversation` directly without forced DAO pattern
- **Testable**: ConversationContractTest provides comprehensive behavior contracts
