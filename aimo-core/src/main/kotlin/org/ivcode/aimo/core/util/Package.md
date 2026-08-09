# Package org.ivcode.aimo.core.util

Utility functions and helpers for context management and cross-package concerns.

This package provides extension functions for accessing well-known context keys, 
thread-safe locking primitives, and other utilities shared across aimo-core packages.

Responsibilities
----------------
- Define well-known context key constants used throughout AIMO
- Provide extension functions for safe context access with type coercion
- Provide thread-safe locking utilities (`KeyedReentrantLock`)
- Support request context threading through async operations

Well-Known Context Keys
------------------------
All AIMO components use these standard context keys:

- **`"chatId"`** (`CONTEXT_KEY__CHAT_ID`): UUID of the current conversation
- **`"requestId"`** (`CONTEXT_KEY__REQUEST_ID`): UUID of the current request
- **`"conversation-client"`** (`CONTEXT_KEY__CONVERSATION`): Reference to the `Conversation` instance
- **`"requestMetadata"`** (defined in `aimo-server`): Server-specific metadata (tenant, user, etc.)

Context Access Extension Functions
-----------------------------------
This package provides type-safe getters for both `Map<String, Any>` and `SystemMessageContext`:

```kotlin
// Extensions on Map<String, Any>
fun Map<String, Any>.getChatId(): UUID?
fun Map<String, Any>.getRequestId(): UUID?
fun Map<String, Any>.getConversationClient(): Conversation?

// Extensions on SystemMessageContext
fun SystemMessageContext.getChatId(): UUID?
fun SystemMessageContext.getRequestId(): UUID?
fun SystemMessageContext.getConversationClient(): Conversation?
```

These functions perform safe type casting and return null if the key is missing or type mismatch.

Utilities
---------
- **`KeyedReentrantLock`**: Thread-safe per-key locking for scenarios where multiple threads 
  need to coordinate access to different conversations independently (e.g., two threads 
  updating conversation A and B concurrently is fine; two threads updating A concurrently 
  should serialize).

Integration Points
-------------------
- Context keys are set by `AimoChatClientImpl` and `aimo-server` controllers
- Used by tool callbacks, system message generators, and chat client interceptors
- Extension functions make context access idiomatic Kotlin across packages

Developer Notes
----------------
- Always use the extension functions provided here rather than casting context directly; 
  this ensures consistent null-handling and error resilience
- When passing context between async operations, ensure all well-known keys are preserved
- Do not add application-specific context keys here; keep this package generic
- `KeyedReentrantLock` should be used for conversation-level synchronization 
  (keyed by chatId) to allow concurrent updates to different conversations

Example Usage
-------------
```kotlin
fun mySystemMessage(context: SystemMessageContext): String? {
    val chatId = context.getChatId() ?: return null
    val requestId = context.getRequestId() ?: return null
    val conversation = context.getConversationClient() ?: return null
    // Use chatId, requestId, conversation...
    return "My system message"
}

// In a tool callback
fun MyTool(context: Map<String, Any>): String {
    val conversation = context.getConversationClient()
    conversation?.let { /* use it */ }
    return "done"
}
```


