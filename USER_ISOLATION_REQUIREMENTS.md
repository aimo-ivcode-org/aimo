# User Isolation & Security System Requirements

## Overview

This document outlines the requirements for implementing user isolation and security in the `aimo` application.

**How it works**:
- Each conversation is owned by a `userId` (which is any string)
- The configured `AimoUserProvider` determines what userId the current request/context "is"
- When querying conversations with a userId, you get only those matching that userId
- When querying conversations with `userId = null`, you get ALL conversations (admin visibility, no scoping)
- Different providers return different userIds: `GlobalUserProvider` returns "global", `OAuth2Provider` returns the OAuth subject, etc.
- The system treats all non-null userIds the same way—no special cases

**Future**: Ephemeral/temporary conversations for programmatic agent-to-agent communication (see Out of Scope section).

**Examples**:
- `userId == null`: Returns ALL conversations (admin/system visibility, no filtering)
- `userId == "global"`: Returns conversations owned by userId "global" (what GlobalUserProvider returns)
- `userId == alice`: Returns conversations owned by userId "alice" (what multi-user providers return)
- All userIds are treated identically; "global" is not special

**Important**: Passing `userId = null` to DAO methods bypasses all scoping and returns/operates on all conversations. This should only be used for admin operations.

---

## Core Requirements

### R1: UserProvider Abstraction

**Objective**: Make user resolution pluggable and provider-agnostic.

- [ ] Create `AimoUserProvider` interface in `aimo-core`
  - Method: `getCurrentUser(): AimoUser`
  - Method: `getUserById(userId: String): AimoUser?` (optional)
- [ ] Create `AimoUser` data class in `aimo-core`
  - Field: `userId: String`
  - Field: `metadata: Map<String, Any>`
- [ ] Implement `GlobalUserProvider` in `aimo-core`
  - Always returns `AimoUser(userId = "global")`
  - "global" is just a userId string; treated like any other userId
- [ ] Provider is injectable via Spring, determined by configuration
- [ ] Default provider is `GlobalUserProvider` if no other bean is registered

### R2: DAO Layer User Scoping

**Objective**: Enforce user isolation at the persistence layer.

#### R2.1: ChatConversationEntity
- [ ] Add field: `userId: String? = null`
  - Null = no owner, represents conversations visible to admin/system operations (not assigned to any user)
  - String = owned by that userId (a regular string, just like any other userId)
- [ ] Preserve on all operations (create, retrieve, delete)

#### R2.2: AimoChatClientDao Interface
- [ ] Add `userId: String? = null` parameter to methods:
  - `createChatConversation(userId: String? = null): ChatConversationEntity`
  - `createChatConversation(userId: String? = null, metadata: Map<String, String>): ChatConversationEntity`
  - `getChatConversations(userId: String? = null): List<ChatConversationEntity>`
  - `getChatConversation(chatId: UUID, userId: String? = null): ChatConversationEntity?`
  - `getConversationClient(chatId: UUID, userId: String? = null): AimoConversationClient?` (if applicable)
  - `deleteChatConversation(chatId: UUID, userId: String? = null): Boolean`
  - `addChatRequest(userId: String? = null, request: ChatRequestEntity)`
  - `getChatRequests(userId: String? = null, chatId: UUID): List<ChatRequestEntity>`
  - `getChatRequests(userId: String? = null, chatId: UUID, maxRequestCharacters: Int): List<ChatRequestEntity>`
  - `getMessages(userId: String? = null, chatId: UUID): List<ChatMessageEntity>`
  - `upsertConversationMetadata(chatId: UUID, userId: String? = null, metadata: Map<String, Any>): Boolean`
  - `deleteConversationMetadata(chatId: UUID, userId: String? = null, keys: List<String>): Boolean`

#### R2.3: AimoChatClientDaoMemory Implementation
- [ ] `getChatConversations(userId)` scoping logic:
  - If `userId == null`: return ALL conversations, no scoping (admin visibility)
  - If `userId != null`: return only conversations where `conversation.userId == userId`
- [ ] `getChatConversation(chatId, userId)` ownership check:
  - If `userId == null`: return conversation regardless of owner (admin override)
  - If `userId != null`: verify `conversation.userId == userId`, return null if mismatch
- [ ] `deleteChatConversation(chatId, userId)` ownership check:
  - If `userId == null`: allow delete regardless of owner (admin override)
  - If `userId != null`: verify `conversation.userId == userId`, return false if denied
- [ ] `addChatRequest(userId, request)`: store request; userId already tracked in conversation
- [ ] `getChatRequests(userId, chatId)`: verify `userId` owns `chatId` before returning requests (null userId overrides)
- [ ] `getMessages(userId, chatId)`: verify `userId` owns `chatId` before returning messages (null userId overrides)
- [ ] All metadata operations respect ownership checks
- [ ] Note: null userId = admin visibility (returns all); non-null userid = user-scoped

### R3: Aimo Interface Updates

**Objective**: Propagate userId through the core orchestration layer.

- [ ] Update `Aimo` interface in `aimo-core`:
  - `getConversationClient(chatId: UUID, userId: String? = null): AimoConversationClient?`
  - `createConversation(userId: String? = null): AimoConversationInfo`
  - `getConversations(userId: String? = null): List<AimoConversationInfo>`
  - `deleteConversation(chatId: UUID, userId: String? = null): Boolean`
  - `getChatHistory(chatId: UUID, userId: String? = null): List<AimoHistoryRequest>`
  - `upsertConversation(chatId: UUID, metadata: Map<String, String>, userId: String? = null): Boolean`

- [ ] Update `AimoImpl` to pass `userId` to DAO layer for all operations
  - Pass as-is; DAO handles all userId values the same way
  - userId is just a string identifier, no special cases
  - GlobalUserProvider gives "global", other providers give other strings

- [ ] Preserve backward compatibility: omitting `userId` defaults to `null` (global namespace)

### R4: AimoConversationClient Integration

**Objective**: Ensure user scoping flows through conversation operations.

- [ ] Add `userId: String?` parameter to `AimoConversationClient` constructor
- [ ] Pass `userId` to all DAO operations:
  - `getMessages()` → `dao.getMessages(userId, chatId)`
  - `addMessages()` → `dao.addChatRequest(userId, request)`
  - Metadata operations → `dao.upsertConversationMetadata(chatId, userId, ...)`

### R5: Server Layer Integration

**Objective**: Wire UserProvider into controllers and services.

- [ ] Inject `AimoUserProvider` into:
  - `ConversationController`
  - `ChatController`
  - `HistoryController`
  - Relevant services
- [ ] Resolve user via `userProvider.getCurrentUser()`
- [ ] Extract `userId` as `user.userId` (never null, but may be "global")
- [ ] Pass `userId` to `Aimo` methods

**Example**:
```kotlin
@RestController
class ConversationController(
    private val conversationService: ConversationService,
    private val userProvider: AimoUserProvider,
) {
    @PostMapping("/")
    fun createConversation(): ChatConversationInfo {
        val user = userProvider.getCurrentUser()
        return conversationService.createConversation(user.userId)
    }
}

@Service
class ConversationService(private val aimo: Aimo) {
    fun createConversation(userId: String?): ChatConversationInfo {
        return ChatConversationInfo(aimo.createConversation(userId).chatId)
    }
}
```

### R6: Configuration

**Objective**: Allow deployer to select which UserProvider is active.

- [ ] Add `aimo.auth.provider` configuration property:
  - `global` (default): Uses `GlobalUserProvider` (returns "global" for all requests)
  - `basic`: Uses `BasicAuthUserProvider` (returns username from HTTP Basic Auth)
  - `oauth2`: Uses `OAuth2UserProvider` (returns subject from OAuth2 token)
  - `jwt`: Uses `JWTUserProvider` (parses userId from JWT)
  - `header`: Uses `CustomHeaderUserProvider` (reads X-User-Id header)

- [ ] Implement provider bean registration in Spring config:
  - Use `@ConditionalOnProperty` to select active provider
  - Default to `GlobalUserProvider` if none specified

- [ ] Support Spring profiles: `global`, `basic-auth`, `oauth2`, `jwt`, `header`

- [ ] Example `application.yml`:
```yaml
aimo:
  auth:
    provider: global  # or: basic, oauth2, jwt, header
```

### R7: Security Enforcement

**Objective**: Enforce user isolation at the DAO layer.

- [ ] All ownership checks happen at DAO layer (primary defense)
- [ ] **Core scoping rule**:
  - If `userId == null`: return ALL conversations, no filtering (admin/system visibility)
  - If `userId != null`: return only conversations where `conversation.userId == userId` (user-scoped access)
- [ ] Unauthorized access (user trying to access another user's conversation) returns `false` or NotFoundException consistently
- [ ] No data leaks on denied access

---

## Data Model Changes Summary

| Component | Change |
|-----------|--------|
| `ChatConversationEntity` | Add `userId: String? = null` field |
| `AimoChatClientDao` | Add `userId: String? = null` parameter to all public methods |
| `Aimo` | Add `userId: String? = null` parameter to all public methods |
| `AimoConversationClient` | Accept `userId` in constructor, pass to DAO |
| (NEW) `AimoUser` | Data class with `userId`, `metadata` |
| (NEW) `AimoUserProvider` | Interface for user resolution |
| (NEW) `GlobalUserProvider` | Default implementation |

---

## API Contract Examples

### GlobalUserProvider (Default)

```kotlin
// Provider returns userId = "global"
val user = userProvider.getCurrentUser()  // AimoUser(userId = "global")
aimo.createConversation(user.userId)       // Pass "global"

// DAO stores conversation with userId = "global"
// All requests from GlobalUserProvider see userId = "global", so all conversations appear to each request
// Calls to getChatConversations(null) would also return these (admin view)
```

### BasicAuthUserProvider Example

```kotlin
// Provider extracts username from HTTP Basic Auth
val user = userProvider.getCurrentUser()  // AimoUser(userId = "alice")
aimo.createConversation(user.userId)       // Pass "alice"

// DAO stores conversation with userId = "alice"
// Alice's requests see userId = "alice", conversations with userId = "alice"
// Bob's requests see userId = "bob", conversations with userId = "bob"
// Calls to getChatConversations(null) would return all conversations (admin view)
```


---

## Acceptance Criteria

### AC1: GlobalUserProvider Works
- [ ] Defaulting to `GlobalUserProvider` with no config
- [ ] All HTTP requests see all conversations (because all requests get userId = "global")
- [ ] Admin queries with `userId = null` also see all conversations

### AC2: Multi-User Providers Work
- [ ] Different userIds returned by provider see only their own conversations
- [ ] User cannot access another userId's conversations (denied/NotFoundException)
- [ ] Different providers can be plugged in and work correctly

### AC3: Backward Compatibility
- [ ] Existing code calling `aimo.createConversation()` works (defaults to `userId = null`)
- [ ] All existing tests pass without modification
- [ ] Opt-in: existing code doesn't need to know about UserProvider

### AC4: Testing
- [ ] DAO scoping tests for null vs. non-null userId
- [ ] Provider resolution tests
- [ ] Mock providers work in tests

---

## Out of Scope (For Later)

- [ ] Ephemeral/temporary conversations for programmatic agent-to-agent communication
  - `EphemeralConversation` data class (isolated session)
  - `AimoChatClientDaoEphemeral` (session-backed DAO)
  - `AimoEphemeralFactory` (factory for creating ephemeral Aimo instances)
  - Cleanup semantics (`session.clear()`)
  - Use case: Agents orchestrating temporary isolated conversations without polluting main namespace
- [ ] Database implementation (use memory DAO for MVP)
- [ ] JWT token parsing details (Spring Security handles)
- [ ] OAuth2 client configuration (Spring Security handles)
- [ ] User registration/management (external system)
- [ ] Audit logging (separate concern)
- [ ] Rate limiting (separate concern)
- [ ] User metadata persistence (store in conversation metadata for now)

---

## Implementation Order

1. **Phase 1: Core Types**
   - Create `AimoUser`, `AimoUserProvider`, `GlobalUserProvider`
   - Update `ChatConversationEntity` to include `userId`

2. **Phase 2: DAO Layer**
   - Update `AimoChatClientDao` interface with userId parameters
   - Update `AimoChatClientDaoMemory` with scoping logic
   - Add comprehensive tests

3. **Phase 3: Core Orchestration**
   - Update `Aimo` interface and `AimoImpl`
   - Update `AimoConversationClient` and `AimoConversationClientImpl`

4. **Phase 4: Server Integration**
   - Update controllers and services
   - Inject `AimoUserProvider`
   - Add Spring config for provider selection

5. **Phase 5: Configuration & Deployment**
   - Add `aimo.auth.provider` config
   - Implement provider bean registration
   - Document setup for each provider type

---

## Testing Strategy

- **Unit Tests**: DAO scoping logic (null vs. non-null userId), provider resolution
- **Integration Tests**: Multiple providers (GlobalUserProvider, mock multi-user provider), multiple userIds
- **Backward Compat Tests**: Existing code paths, default arguments

