# Phase 1 Migration Guide

## Overview

Phase 1 replaced the monolithic `Aimo` facade with a factory-based architecture. This guide helps you understand what changed and how to migrate any existing code.

## What Was Removed

### 1. `Aimo` Facade (Deleted ✅)
- **File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/Aimo.kt`
- **File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/AimoImpl.kt`
- **What it did**: Monolithic singleton that managed all conversations, chat clients, and admin operations
- **Why removed**: Global state, hard to test, no per-request configuration

### 2. `AimoConversationClient` Interface (Deleted ✅)
- **File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/AimoConversationClient.kt`
- **File**: `aimo-core/src/main/kotlin/org/ivcode/aimo/core/client/conversation/AimoConversationClientImpl.kt`
- **Replaced by**: `Conversation` interface (`aimo-core/.../Conversation.kt`)

## New Architecture

### Factory Pattern

```
ConversationFactory
    └─> Conversation (per-chat instance)
            ├─ History (messages)
            ├─ Metadata (properties)
            └─ createChatClientBuilder() → ChatClientBuilder
                    └─ build() → AimoChatClient
```

### Key Interfaces

#### 1. `Conversation` (replaces `AimoConversationClient`)
```kotlin
interface Conversation {
    val chatId: UUID
    
    // History
    fun getMessages(maxCacheCharacters: Long? = null): List<AimoChatMessage>?
    fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long? = null)
    
    // Metadata
    fun getChatMetadata(): Map<String, Any>
    fun getChatProperty(property: String): Any?
    fun writeChatProperty(property: String, value: Any)
    fun deleteChatProperty(property: String): Boolean

    // Builder creation
    fun createChatClientBuilder(): ChatClientBuilder
}
```

#### 2. `ChatClientBuilder` (new)
```kotlin
interface ChatClientBuilder {
    fun withModel(modelId: String): ChatClientBuilder
    fun withModel(config: AimoChatModelConfig): ChatClientBuilder
    fun withInterceptor(interceptor: AimoChatClientInterceptor): ChatClientBuilder
    fun build(): AimoChatClient
}
```

#### 3. `ConversationFactory` (new)
```kotlin
interface ConversationFactory {
    fun getConversation(chatId: UUID, userId: String?): Conversation
}
```

#### 4. `ChatClientBuilderFactory` (new)
```kotlin
interface ChatClientBuilderFactory {
    fun createBuilder(): ChatClientBuilder
}
```

## Migration Examples

### Before: Using `Aimo` Facade
```kotlin
@Autowired
private lateinit var aimo: Aimo

fun doSomething() {
    val conversation = aimo.getConversation(chatId, userId)
    val chatClient = conversation.createChatClient()
    val response = chatClient.chat("Hello")
}
```

### After: Using Factories
```kotlin
@Autowired
private lateinit var conversationFactory: ConversationFactory

@Autowired
private lateinit var chatClientBuilderFactory: ChatClientBuilderFactory

fun doSomething() {
    val conversation = conversationFactory.getConversation(chatId, userId)
    val chatClient = conversation.createChatClientBuilder()
        .build()
    val response = chatClient.chat("Hello")
}
```

### With Per-Request Model Override
```kotlin
fun doSomethingWithCustomModel() {
    val conversation = conversationFactory.getConversation(chatId, userId)
    val chatClient = conversation.createChatClientBuilder()
        .withModel("gpt-4")  // Override default model
        .build()
    val response = chatClient.chat("Hello")
}
```

### With Interceptors
```kotlin
fun doSomethingWithInterceptor() {
    val conversation = conversationFactory.getConversation(chatId, userId)
    val chatClient = conversation.createChatClientBuilder()
        .withInterceptor(myCustomInterceptor)
        .build()
    val response = chatClient.chat("Hello")
}
```

## Method Renames

### Conversation Metadata Methods

The duplicate `read*` methods have been removed:

| Old Method (Deleted)           | New Method (Use This)      |
|-------------------------------|----------------------------|
| `readChatMetadata()`          | `getChatMetadata()`        |
| `readChatProperty(property)`  | `getChatProperty(property)`|

## Configuration Changes

### Properties File Structure (New)

You can now configure models in `application.yaml`:

```yaml
aimo:
  models:
    gpt-4:
      provider: openai
      model: gpt-4
      temperature: 0.7
      maxTokens: 2000
      primary: true  # Default model
    
    llama3:
      provider: ollama
      model: llama3
      temperature: 0.5
```

### Spring Bean Configuration

The following beans are now available for autowiring:

- `ConversationFactory` — Create/retrieve conversations
- `ChatClientBuilderFactory` — Create chat client builders
- `AimoChatClientDao` — Direct DAO access (if needed)
- `AimoChatModelProviderFactory` — Model provider factory

The following beans are **removed**:
- ❌ `Aimo` — No longer exists

## Server-Side Changes

### `ChatService` (aimo-server)

**Before:**
```kotlin
val conversation = aimo.getConversation(chatId, userId)
val chatClient = conversation.createChatClient()
```

**After:**
```kotlin
val conversation = conversationFactory.getConversation(chatId, userId)
val chatClient = conversation.createChatClientBuilder().build()
```

### Context Extensions

**Before:**
```kotlin
context.getConversation()  // Returns AimoConversationClient
```

**After:**
```kotlin
context.getConversation()  // Returns Conversation
```

## Testing Changes

### Test Setup

**Before:**
```kotlin
@MockBean
private lateinit var aimo: Aimo

@Test
fun test() {
    val mockConversation = mock<AimoConversationClient>()
    whenever(aimo.getConversation(any(), any())).thenReturn(mockConversation)
    // ...
}
```

**After:**
```kotlin
@MockBean
private lateinit var conversationFactory: ConversationFactory

@Test
fun test() {
    val mockConversation = mock<Conversation>()
    whenever(conversationFactory.getConversation(any(), any())).thenReturn(mockConversation)
    // ...
}
```

## Benefits of New Architecture

### 1. **No Global State**
- Each conversation is independently managed
- No singleton bottlenecks
- Better testability

### 2. **Per-Request Configuration**
- Override model per request
- Add interceptors per request
- Configure timeouts/retries per request

### 3. **Builder Pattern**
- Fluent, composable API
- Clear configuration intent
- Easy to extend

### 4. **Cleaner Dependencies**
- No circular dependencies through facade
- Factory pattern is testable
- Clear separation of concerns

### 5. **Interceptor Support**
- Add cross-cutting concerns (logging, security, guard-rails)
- Composable middleware
- No special-purpose hooks needed

## Breaking Changes Summary

| Component                    | Status      | Replacement                          |
|------------------------------|-------------|--------------------------------------|
| `Aimo` interface             | ✅ Deleted  | `ConversationFactory` + `ChatClientBuilderFactory` |
| `AimoImpl` class             | ✅ Deleted  | Factory beans                        |
| `AimoConversationClient`     | ✅ Deleted  | `Conversation` interface             |
| `AimoConversationClientImpl` | ✅ Deleted  | `ConversationImpl` class             |
| `readChatMetadata()`         | ✅ Deleted  | `getChatMetadata()`                  |
| `readChatProperty()`         | ✅ Deleted  | `getChatProperty()`                  |

## Files Modified

### Core (aimo-core)
- `AimoConfig.kt` — Removed `createAimo` bean, simplified factory wiring
- `Extensions.kt` — Updated context extensions to use `Conversation`
- Added `Conversation.kt`, `ConversationImpl.kt`
- Added `ChatClientBuilder.kt`, `ChatClientBuilderImpl.kt`
- Added `ConversationFactory.kt`, `ConversationFactoryImpl.kt`
- Added `ChatClientBuilderFactory.kt`, `ChatClientBuilderFactoryImpl.kt`

### Server (aimo-server)
- `ChatService.kt` — Uses `ConversationFactory` instead of `Aimo`
- `TitleController.kt` — Uses `ConversationFactory` + `AimoChatClientDao`
- `Extensions.kt` — Context extensions return `Conversation`

### Plugin UI (aimo-plugin-ui)
- `TitleChatController.kt` — Supports `Conversation` interface
- `Extensions.kt` — Extension methods for `Conversation`

### Tests
- All tests updated to use new factories
- `ConversationImplTest.kt` replaces `AimoConversationClientImplTest.kt`

## Migration Checklist

If you have custom code that uses the old architecture:

- [ ] Replace `@Autowired Aimo` with `@Autowired ConversationFactory` and `@Autowired ChatClientBuilderFactory`
- [ ] Replace `aimo.getConversation()` with `conversationFactory.getConversation()`
- [ ] Replace `conversation.createChatClient()` with `conversation.createChatClientBuilder().build()`
- [ ] Replace `readChatMetadata()` with `getChatMetadata()`
- [ ] Replace `readChatProperty()` with `getChatProperty()`
- [ ] Update test mocks to use new interfaces
- [ ] Update context extension usage if needed

## Questions?

See:
- `AGENTS.md` — Architecture overview and conventions
- `ROADMAP.md` — Phase 1 completion status and future phases
- Example apps: `examples/simple-ollama`, `examples/simple-bedrock`

## Commit References

- **[b24a893]** Replace Aimo facade with builder factory pattern
- **[45477ca]** Remove legacy AimoConversationClient interface

