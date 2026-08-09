# Package org.ivcode.aimo.core.conversation

Conversation storage abstraction and factory for managing durable chat history.

This package provides the `Conversation` interface and implementation for a single chat's 
persistent message history and metadata. It includes support for interceptors to add cross-cutting 
concerns like auditing, encryption, or caching.

Responsibilities
----------------
- Define the `Conversation` interface as the primary API for a single chat's storage
- Implement `ConversationImpl` backed by a `ConversationStore` and DAO
- Provide `ConversationFactory` to load or create conversations by chatId
- Support `ConversationInterceptor` for cross-cutting concerns (security, auditing, encryption, caching)
- Manage message history with optional character-limit windowing
- Handle durable conversation metadata (chat titles, scope settings, user annotations)
- Enforce scope metadata filtering: interceptors enrich metadata, DAO filters by scope
- Coordinate with DAO for read/write operations

Key Concepts
------------
- **Single conversation per chatId**: Each `Conversation` instance is scoped to one chat ID.
- **Durable metadata**: Separate from message history; stores properties like chat title, 
  scope, or custom annotations via `writeChatProperty()` / `getChatProperty()`.
- **Interceptor chain**: `ConversationInterceptor` wraps DAO operations for audit, access 
  control, data transformation, or caching. Typical use cases:
  - **Auditing**: Log all reads/writes
  - **Access control**: Validate user ownership before allowing DAO access
  - **Caching**: Memoize message fetches
  - **Encryption**: Encrypt messages on write, decrypt on read
  - **Metadata enrichment**: Add user/tenant information
- **Message windowing**: `getMessages(maxCacheCharacters)` loads the most recent history 
  up to a character limit for prompt budget management.
- **Scope metadata**: Metadata like `{"tenant": "acme", "userId": "user123"}` is passed 
  to all DAO calls for filtering and access control.

Integration Points
-------------------
- Built on top of `AimoChatClientDao` for storage
- Used by `AimoChatClientImpl` for all conversation reads/writes
- Interceptors compose with the DAO to provide enterprise-grade features
- Scope metadata enforced at DAO layer (see `AimoChatClientDao` for scoping semantics)

Operational Guidance
--------------------
- To add auditing, wrap the factory with an interceptor that logs all calls
- To add encryption, implement an interceptor that transforms messages on write/read
- To add caching, implement an interceptor that memoizes `getMessages()` results
- Always ensure interceptors are thread-safe if the conversation is accessed concurrently
- Scope metadata should be consistent across all operations for a conversation; 
  the DAO enforces that all metadata entries in requests must match stored metadata

Example Use Case
-----------------
```kotlin
val factory = ConversationFactoryImpl(daoMemory)
    .withInterceptor(AuditingConversationInterceptor())
    .withInterceptor(CachingConversationInterceptor())

val conversation = factory.getConversation(chatId, mapOf("tenant" to "acme"))
val messages = conversation.getMessages(maxCacheCharacters = 10000)
conversation.addMessages(requestId, newMessages)
```


