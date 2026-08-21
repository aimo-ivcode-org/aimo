## Context

**Current State**: 
- `AimoChatClientDao` is a required abstraction; `ConversationImpl` wraps it
- Every conversation storage backend must implement DAO
- Conversation factory mandates DAO-backed storage with ConversationImpl wrapper
- Metadata scoping and interceptors work through the DAO layer

**Constraints**:
- AimoChatClient already works with Conversation interface (no changes needed at upper layers)
- Must maintain scope-based access control via metadata
- Must preserve interceptor support (auditing, encryption, etc.)
- No backward-compatible DAO shim: `AimoChatClientDao` is removed entirely, so all consumers (including outside aimo-core, see Impact) are migrated within this change rather than left on a deprecated path

**Stakeholders**: Core team, app developers wiring beans, anyone adding custom storage backends

## Goals / Non-Goals

**Goals:**
- Make Conversation the primary abstraction that implementations must satisfy
- Provide direct implementations (MemoryConversation, FileConversation) without DAO
- Maintain ConversationFactory's interceptor and metadata scoping capabilities
- Enable simpler, more flexible storage backends
- Completely remove AimoChatClientDao and ConversationImpl

**Non-Goals:**
- Change AimoChatClient or upper layer APIs
- Remove scope/metadata filtering capabilities
- Eliminate interceptor support

## Decisions

### 1. Conversation as Primary Contract
**Decision**: Conversation interface is the fundamental contract; all implementations satisfy it directly.

**Rationale**: Conversation is what consumers actually need—a chat's message history and metadata. DAO is an implementation detail. Making the contract explicit at the level consumers care about is clearer.

**Alternatives Considered**:
- Keep DAO primary with Conversation as wrapper → Less flexible; everyone forced through DAO layer
- Multiple parallel hierarchies → Confusing, harder to reason about

### 2. ConversationFactory Returns Conversation Implementations
**Decision**: `ConversationFactory.getConversation()` returns a Conversation instance directly. Multiple `ConversationFactory` implementations exist: `MemoryConversationFactory` and `FileConversationFactory`.

**Rationale**: Factories should be transparent about what they create. Each backend has its own factory that creates the appropriate implementation. This makes configuration explicit and simplifies testing.

**Alternatives Considered**:
- Single factory with a strategy pattern → More complex; factories are simpler to understand
- Direct instantiation without factory → Loses centralized scoping and interceptor logic

### 3. Direct Implementations Without DAO
**Decision**: Create `MemoryConversation` and `FileConversation` that implement Conversation directly, without using AimoChatClientDao.

**Rationale**: 
- Reduces indirection for simple backends
- Simpler to reason about and test
- No DAO dependency at all, internally or externally — DAO is deleted entirely (Decision 5)

**Alternatives Considered**:
- Keep all implementations using DAO internally → Unnecessary layer for direct storage

### 4. Scoping & Metadata at ConversationFactory Level
**Decision**: Scope validation and metadata enrichment (via interceptors) happen at the factory level, not pushed down to DAO.

**Rationale**: 
- Implementations focus on storing messages; factories focus on access control
- Cleaner separation: each layer does one thing
- Scoping logic is implementation-agnostic

**Implementation**:
```kotlin
// ConversationFactory creates scoped instances
factory.getConversation(chatId, scopeMetadata)
  ↓
factory applies interceptors (which may modify metadata)
  ↓
returns Conversation with access control enforced
```

### 5. Eliminate DAO Module Entirely
**Decision**: Remove `AimoChatClientDao` interface and all DAO implementations. All storage backends implement Conversation directly.

**Rationale**: 
- DAO pattern added unnecessary indirection without providing value for our use cases
- Direct Conversation implementations are simpler to reason about and test
- Eliminates a layer of abstraction that didn't earn its complexity
- Cleaner boundary: Conversation is the sole storage contract

**Alternatives Considered**:
- Keep DAO as optional → Still adds complexity and confusion; better to make clean break
- Keep DAO but deprecate → Leaves legacy code in place; cleaner to remove entirely

### 6. Conversation Exposes Request-Grouped History via `getHistory()`
**Decision**: Add `Conversation.getHistory(maxCacheCharacters: Long? = null): List<AimoHistoryRequest>` alongside the existing flat `getMessages()`. `AimoHistoryRequest` (already defined in `aimo-core/model/AimoModel.kt` as `chatId`, `requestId`, `messages`, `createdAt`) becomes the canonical grouped-history shape returned by all `Conversation` implementations.

**Rationale**:
- `getMessages()` returns a flat `List<AimoChatMessage>` with no request grouping. This is sufficient for `AimoChatClientImpl`'s prompt-budget history loading, but is **not** sufficient for `HistoryService`/`ChatHistoryRequest`, which the aimo-ui frontend relies on: `ChatController.flattenHistory()` sorts history by `createdAt` and maps `requestId` → `responseId` to reconstruct per-response message grouping and stream-accumulation keys when a conversation is loaded. This is a real, user-facing requirement, not a DAO-internal artifact — verified by tracing `aimo-server/HistoryController` → `aimo-ui AimoClient.getHistory` → `ChatController.flattenHistory`.
- `AimoHistoryRequest` already exists in `aimo-core.model` with exactly this shape, and `aimo-server/ServiceTransformers.kt` already has a (currently dead/unused) `AimoHistoryRequest.toChatHistoryRequest()` extension — this decision activates that existing, previously-unused scaffolding instead of introducing a new type.
- `MemoryConversation`/`FileConversation` must retain per-request grouping (`requestId`, `createdAt`) internally (see Decision 3) to implement `getHistory()`; this supersedes the internal use of DAO's `ChatRequestEntity`/`ChatMessageEntity`/`ChatConversationEntity`, which are deleted along with the DAO module.

**Alternatives Considered**:
- Drop `requestId`/`createdAt` from the history API and only expose flat messages → Rejected; would break existing chat history ordering/reconstruction in aimo-ui without a replacement mechanism.
- Overload `getMessages()` to optionally return grouped data → Rejected; conflates two distinct concerns (bounded prompt-budget history vs. full request-grouped history for display) in one method signature.

### 7. Batch Metadata Operations
**Decision**: Add batch-oriented metadata methods to `Conversation` — `writeChatProperties(properties: Map<String, Any>)` and `deleteChatProperties(keys: List<String>)` — alongside the existing single-key `writeChatProperty`/`deleteChatProperty`.

**Rationale**:
- `aimo-server/ConversationService.upsertMetadata`/`deleteMetadata` operate on a full `Map`/`List<String>` per call (backing the `/aimo-api/conversation/{chatId}/metadata` PUT/DELETE endpoints). Looping single-key calls would silently change atomicity/failure semantics (partial writes on error) compared to the current DAO-backed `upsertConversationMetadata`/`deleteConversationMetadata`, which apply all changes in one storage operation.
- Explicit batch methods preserve today's atomicity guarantees for `MemoryConversation` (single map mutation) and `FileConversation` (single file rewrite) without requiring callers to loop.

**Alternatives Considered**:
- Loop single-key calls in `ConversationService` → Rejected; changes atomicity semantics silently and adds N storage writes instead of 1.

## Risks / Trade-offs

**[Risk] More implementation diversity → harder to standardize**
- Mitigation: Provide well-documented reference implementations (MemoryConversation, FileConversation); establish clear contract tests for Conversation interface

**[Risk] Removing DAO removes a reusable pattern for future SQL/NoSQL backends**
- Mitigation: Conversation interface is the contract; new backends can be added as needed (e.g., SqlConversation). No need to resurrect DAO—implement Conversation directly.

**[Risk] Code currently using ConversationImpl and DAO directly needs major updates**
- Mitigation: ConversationImpl becomes obsolete; update all example apps and tests to use direct implementations (MemoryConversation or FileConversation). Clear, one-time migration.
- This also includes non-`aimo-core` consumers discovered during review: `aimo-server/ConversationService.kt`, `aimo-server/HistoryService.kt`, and `aimo-plugin-ui/TitleController.kt` all inject `AimoChatClientDao` directly today and must be migrated to `ConversationFactory`/`Conversation` as part of this change (see tasks 5a.*).

**[Risk] Losing request-grouped history breaks aimo-ui chat history reconstruction**
- Mitigation: `Conversation.getHistory()` (Decision 6) preserves `requestId`/`createdAt` grouping so `HistoryService` and the `/aimo-api/history/{chatId}` endpoint continue to work unchanged from the frontend's perspective.

**[Trade-off] Losing a "standard" DAO pattern vs. simplicity of direct implementations**
- Accepted because direct implementations are clearer; Conversation interface provides the contract; future backends add what they need without mandatory DAO wrapper





