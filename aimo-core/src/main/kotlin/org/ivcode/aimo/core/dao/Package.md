# Package org.ivcode.aimo.core.dao

Data Access Object (DAO) abstraction and implementations for conversation storage.

This package defines the `AimoChatClientDao` interface and provides multiple backend implementations 
(in-memory, file-based) for persisting chat conversations, messages, and metadata. All operations 
support optional scope-based metadata filtering for multi-tenant and role-based access control.

Responsibilities
----------------
- Define `AimoChatClientDao` interface for all storage operations
- Implement in-memory storage (`AimoChatClientDaoMemory`)
- Implement file-based JSON storage (`AimoChatClientDaoFile`)
- Define DAO entity classes (`ChatConversationEntity`, `ChatRequestEntity`, `ChatMessageEntity`)
- Support scope-based filtering: all read/write operations accept optional `scopeMetadata`
- Provide `ConversationMetadataMatcher` for post-query filtering on metadata
- Ensure consistent behavior across implementations regarding scope semantics

Key Concepts
------------
- **Scope metadata semantics**: When `scopeMetadata` is non-empty, operations only affect 
  conversations whose stored metadata contains ALL entries in `scopeMetadata` (AND logic).
  Empty `scopeMetadata` performs no scoping.
- **Create vs. Read scoping**: Creation always stores the provided metadata; reads filter 
  by scope match.
- **Backend independence**: The interface is agnostic to storage backend; implementations 
  can use in-memory maps, files, SQL databases, NoSQL stores, etc.
- **Entity models**: Entities (`ChatConversationEntity`, `ChatRequestEntity`, etc.) represent 
  the durable state and are independent of domain models.

Available Implementations
------------------------
- **AimoChatClientDaoMemory**: In-memory storage using Kotlin maps. Suitable for testing 
  and development. Uses `ConversationMetadataMatcher` for scope-based filtering.
- **AimoChatClientDaoFile**: File-based JSON storage in a configurable directory. Each 
  conversation is stored as a separate JSON file. Uses `ConversationMetadataMatcher` 
  for scope-based filtering.
- **Custom implementations**: Database-backed implementations should push scope filtering 
  into SQL WHERE clauses (or equivalent NoSQL queries) for efficiency.

Scope Metadata Matching Rules
------------------------------
- If `scopeMetadata` is empty → no filtering, all conversations are visible
- If `scopeMetadata` is non-empty → only return conversations where **every** 
  key-value pair in `scopeMetadata` matches the stored metadata
- Example: `scopeMetadata = {"tenant": "acme"}` matches conversations with 
  `metadata = {"tenant": "acme", "userId": "user1"}` but NOT 
  `metadata = {"tenant": "other", "userId": "user1"}`
- File and Memory DAOs use `ConversationMetadataMatcher` helper to implement this logic
- SQL implementations should write WHERE clauses like: 
  `WHERE metadata->>'tenant' = 'acme' AND metadata->>'userId' = 'user1'`

Integration Points
-------------------
- Used by `Conversation` (and `ConversationInterceptor`) for all storage
- Wrapped by `ConversationInterceptor` for cross-cutting concerns
- Configured via `AimoProperties.dataDir` for file-based storage
- Scope metadata enriched by interceptors before DAO calls

Developer Notes
----------------
- Implement `AimoChatClientDao` to add new storage backends (SQL, NoSQL, cloud storage)
- Use `ConversationMetadataMatcher` utility in your implementation for consistent 
  scope filtering unless you can push filtering into your backend's native query language
- For performance in multi-tenant scenarios, ensure scope filtering is efficient 
  (ideally in database indexes)
- Test your implementation with both empty and non-empty `scopeMetadata` to ensure 
  filtering works correctly


