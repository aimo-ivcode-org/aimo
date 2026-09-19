# Migration Guide: Removing DAO from aimo-core

This guide helps developers migrate code away from the deleted `AimoChatClientDao` layer.

## Overview

As of this refactoring, aimo-core no longer includes:
- `AimoChatClientDao` interface
- `AimoChatClientDaoMemory` implementation
- `AimoChatClientDaoFile` implementation
- `AimoChatClientDaoEntities` (ChatConversationEntity, ChatRequestEntity, etc.)
- `ConversationImpl` wrapper class

**Why?** These were an intermediate abstraction layer. The new architecture has implementations implement `Conversation` directly, eliminating unnecessary boilerplate.

## Migration Paths

### Path 1: From ConversationImpl + DAO to Direct Implementations

**Before:**
```kotlin
// Old code using ConversationImpl wrapper
val dao = AimoChatClientDaoMemory()
val factory = ConversationFactoryImpl(dao)
val conversation: Conversation = factory.getConversation(chatId, metadata)
    ?: error("Not found")

// conversation is wrapped ConversationImpl backed by DAO
val messages = conversation.getMessages()
```

**After:**
```kotlin
// New code using direct implementations
val factory = MemoryConversationFactory()  // or FileConversationFactory
val conversation = factory.getConversation(chatId, metadata)
    ?: error("Not found")

// conversation is direct MemoryConversation or FileConversation
val messages = conversation.getMessages()
```

**Changes:**
- Replace `AimoChatClientDaoMemory()` with `MemoryConversationFactory()`
- Replace `AimoChatClientDaoFile(dataDir)` with `FileConversationFactory(dataDir)`
- Remove `ConversationFactoryImpl` wrapper - factories are used directly
- API remains identical - no caller changes needed

### Path 2: From Accessing DAO Entities to Using Conversation Methods

**Before:**
```kotlin
// Old code accessing DAO entities
val requests: List<ChatRequestEntity> = dao.getChatRequests(chatId, metadata)
for (request in requests) {
    println("Request ${request.requestId}: ${request.messages.size} messages")
    println("Created: ${request.createdAt}")
}
```

**After:**
```kotlin
// New code using Conversation interface
val conversation = factory.getConversation(chatId, metadata)
val history = conversation?.getHistory()
for (request in history ?: emptyList()) {
    println("Request ${request.requestId}: ${request.messages.size} messages")
    println("Created: ${request.createdAt}")
}
```

**Key differences:**
- `getHistory()` returns `List<AimoHistoryRequest>` (type-stable, public model)
- No need to map from internal `ChatRequestEntity`
- `AimoHistoryRequest` is the public contract type

### Path 3: From DAO Metadata to Conversation Metadata Methods

**Before:**
```kotlin
// Old code using DAO for metadata
val entity: ChatConversationEntity? = dao.getChatConversation(chatId, metadata)
val storedMetadata = entity?.metadata ?: emptyMap()
val title = storedMetadata["title"] as? String
```

**After:**
```kotlin
// New code using Conversation interface
val conversation = factory.getConversation(chatId, metadata)
val title = conversation?.getChatProperty("title") as? String
// or batch access:
val allMetadata = conversation?.getChatMetadata() ?: emptyMap()
```

**Advantages:**
- Simpler API (single method per property vs. entity extraction)
- Type-consistent return values
- Atomic batch operations with `writeChatProperties()` / `deleteChatProperties()`

## Test Migration

### Migrating Tests from DAO to ConversationContractTest

**Before:** Tests using `AimoChatClientDaoMemory` directly

```kotlin
@Test
fun testMessagePersistence() {
    val dao = AimoChatClientDaoMemory()
    val chatId = UUID.randomUUID()
    val entity = dao.createChatConversation(mapOf())
    
    val request = ChatRequestEntity(
        chatId = entity.chatId,
        requestId = UUID.randomUUID(),
        messages = listOf(...)
    )
    dao.addChatRequest(request, emptyMap())
    
    val retrieved = dao.getChatRequests(entity.chatId, emptyMap())
    assertEquals(1, retrieved.size)
}
```

**After:** Tests using ConversationContractTest

```kotlin
class MyConversationTest : ConversationContractTest() {
    
    override fun createConversation(): Conversation {
        return MemoryConversation(
            chatId = UUID.randomUUID(),
            scopeMetadata = null
        )
    }
    
    // Inherits all contract tests:
    // - testAddMessages()
    // - testGetMessages()
    // - testChatMetadata()
    // - testScopeMetadataEnforcement()
    // - testCharacterBudget()
    // - etc.
}
```

**Benefits:**
- Comprehensive coverage via inherited tests
- Focus on implementation-specific behavior
- No need to implement every test case

## Breaking Changes

These types have been deleted and will cause compilation errors:

| Deleted Type | Replacement | Usage |
|---|---|---|
| `AimoChatClientDao` | `ConversationFactory` | Factory for creating/accessing conversations |
| `AimoChatClientDaoMemory` | `MemoryConversationFactory` | In-memory storage factory |
| `AimoChatClientDaoFile` | `FileConversationFactory` | File-backed storage factory |
| `ChatConversationEntity` | `Conversation` (interface) | Single conversation contract |
| `ChatRequestEntity` | `AimoHistoryRequest` | Public history request type |
| `ChatMessageEntity` | `AimoChatMessage` | Public message type |
| `ConversationImpl` | Direct `Conversation` implementations | No wrapper needed |

## Compile-Time Checks

The compiler will help you find all affected code:

1. Search for imports: `grep -r "import.*AimoChatClientDao" .`
2. Search for references: `grep -r "AimoChatClientDao" .`
3. Fix each location using the migration paths above

## Runtime Behavior

The public runtime behavior is **unchanged**:

- Conversation history is still persisted
- Metadata is still stored durably
- Scope metadata validation still enforced
- Interceptors still compose transparently

Only the internal implementation has changed.

## Spring Configuration

If you're configuring the bean manually:

**Before:**
```kotlin
@Configuration
class ConversationConfig {
    @Bean
    fun conversationFactory(): ConversationFactory {
        val dao = AimoChatClientDaoFile(File("./data"))
        return ConversationFactoryImpl(dao)
    }
}
```

**After:**
```kotlin
@Configuration
class ConversationConfig {
    @Bean
    @Primary
    fun conversationFactory(
        @Value("\${aimo.data-dir:./data}") dataDirPath: String,
        objectMapper: ObjectMapper
    ): ConversationFactory {
        return FileConversationFactory(
            File(dataDirPath),
            objectMapper
        )
    }
}
```

## Questions?

- For new custom implementations, see `CONVERSATION_IMPLEMENTATIONS.md`
- For API details, check `Conversation.kt` and `ConversationFactory.kt` javadoc
- For examples, see `examples/` directory
