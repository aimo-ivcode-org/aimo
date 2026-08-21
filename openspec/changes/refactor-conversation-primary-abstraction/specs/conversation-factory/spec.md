## ADDED Requirements

### Requirement: ConversationFactory Creates Direct Conversation Implementations
ConversationFactory SHALL create and manage direct Conversation implementations (MemoryConversation, FileConversation), without requiring or using AimoChatClientDao.

#### Scenario: Factory creates MemoryConversation
- **WHEN** ConversationFactory is wired with MemoryConversationFactory
- **THEN** factory.getConversation(chatId) returns a MemoryConversation instance

#### Scenario: Factory creates FileConversation
- **WHEN** ConversationFactory is wired with FileConversationFactory
- **THEN** factory.getConversation(chatId) returns a FileConversation instance

#### Scenario: No DAO dependency in factory
- **WHEN** ConversationFactory creates a Conversation
- **THEN** no AimoChatClientDao is instantiated or used internally

### Requirement: ConversationFactory Creates New Conversations
ConversationFactory SHALL create and persist new conversations with optional initial metadata.

#### Scenario: Create conversation with metadata
- **WHEN** ConversationFactory.createConversation(metadata={title: "My Chat"}) is called
- **THEN** a new Conversation is created, stored, and returned with the provided metadata

#### Scenario: Create conversation with empty metadata
- **WHEN** ConversationFactory.createConversation() is called with no metadata
- **THEN** a new Conversation is created with empty metadata

### Requirement: ConversationFactory Lists Conversations
ConversationFactory SHALL retrieve multiple conversations matching optional scope metadata for browsing and management operations.

#### Scenario: List all conversations
- **WHEN** ConversationFactory.getConversations() is called
- **THEN** all stored conversations are returned as a list

#### Scenario: List conversations filtered by scope metadata
- **WHEN** ConversationFactory.getConversations(scopeMetadata={userId: "alice"}) is called
- **THEN** only conversations matching the scope metadata are returned

#### Scenario: Empty result when no conversations match
- **WHEN** getConversations(scopeMetadata={userId: "nonexistent"}) is called
- **THEN** an empty list is returned

### Requirement: ConversationFactory Supports Interceptor Composition
ConversationFactory SHALL support adding ConversationInterceptors that apply cross-cutting concerns (auditing, encryption, access control) transparently.

#### Scenario: Interceptor can log conversation access
- **WHEN** ConversationFactory.withInterceptor(loggingInterceptor) is used
- **THEN** loggingInterceptor.interceptGet(chain, chatId, metadata) is called on every getConversation()

#### Scenario: Interceptors can modify metadata
- **WHEN** interceptor modifies metadata before calling chain.proceed()
- **THEN** the modified metadata is passed to underlying Conversation

#### Scenario: Interceptor chain is composable
- **WHEN** multiple interceptors are added via withInterceptor()
- **THEN** they execute in order forming a chain

#### Scenario: Interceptors can deny access
- **WHEN** accessControl.interceptGet() determines user lacks permission
- **THEN** chain.proceed() is not called and null is returned

### Requirement: ConversationFactory Handles Delete Operations
ConversationFactory SHALL support deletion of conversations with the same scoping and interceptor capabilities as read operations.

#### Scenario: Delete with scope metadata
- **WHEN** deleteConversation(chatId, scopeMetadata={userId: "alice"}) is called
- **THEN** conversation is deleted only if scope metadata matches

#### Scenario: Interceptor can audit deletions
- **WHEN** auditInterceptor.interceptDelete() is called
- **THEN** it can log the deletion before delegating to underlying implementation

#### Scenario: Delete returns success/failure
- **WHEN** deleteConversation() is called for existing conversation
- **THEN** it returns true
- **WHEN** deleteConversation() is called for non-existent conversation
- **THEN** it returns false

### Requirement: ConversationFactory Works With Any Direct Conversation Implementation
ConversationFactory SHALL be agnostic to the underlying implementation; consumers do not need to know whether a Conversation is MemoryConversation, FileConversation, or another direct implementation.

#### Scenario: Implementation choice is transparent to consumers
- **WHEN** AimoChatClient calls conversation.getMessages()
- **THEN** it works regardless of whether Conversation is MemoryConversation or FileConversation

#### Scenario: Factory can be swapped with different implementation
- **WHEN** application configuration is changed from MemoryConversationFactory to FileConversationFactory
- **THEN** the rest of the application continues to work without code changes



