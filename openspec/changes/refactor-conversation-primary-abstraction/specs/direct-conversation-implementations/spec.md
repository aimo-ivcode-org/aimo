## ADDED Requirements

### Requirement: MemoryConversation Implementation
The framework SHALL provide a MemoryConversation class that implements Conversation directly using in-memory maps, without requiring AimoChatClientDao.

#### Scenario: Create and use MemoryConversation
- **WHEN** MemoryConversation(chatId) is instantiated
- **THEN** it can store and retrieve messages via getMessages() and addMessages()

#### Scenario: Memory storage persists across operations
- **WHEN** messages are added via addMessages() and later retrieved via getMessages()
- **THEN** the same messages are returned in order

#### Scenario: Metadata operations work on MemoryConversation
- **WHEN** writeChatProperty("title", "My Chat") is called
- **THEN** getChatProperty("title") returns "My Chat"

#### Scenario: Scope metadata is enforced in MemoryConversation
- **WHEN** MemoryConversation is created with scopeMetadata={tenant: "acme"}
- **THEN** operations accessing it without matching metadata fail

#### Scenario: MemoryConversation supports request-grouped history
- **WHEN** getHistory() is called after multiple addMessages() calls
- **THEN** it returns entries preserving requestId and createdAt per addMessages() call, without depending on any DAO entity type

### Requirement: FileConversation Implementation
The framework SHALL provide a FileConversation class that implements Conversation by storing messages and metadata in files, without requiring AimoChatClientDao.

#### Scenario: Create FileConversation backed by filesystem
- **WHEN** FileConversation(chatId, dataDirectory) is instantiated
- **THEN** it creates files under dataDirectory to persist messages

#### Scenario: FileConversation reads and writes messages
- **WHEN** addMessages() is called
- **THEN** messages are written to disk

#### Scenario: FileConversation retrieves messages across restarts
- **WHEN** a new FileConversation instance for the same chatId is created after app restart
- **THEN** getMessages() returns previously persisted messages

#### Scenario: FileConversation respects character budget
- **WHEN** getMessages(maxCacheCharacters=1000) is called
- **THEN** only recent messages totaling up to 1000 characters are returned

#### Scenario: FileConversation supports request-grouped history
- **WHEN** getHistory() is called after an app restart
- **THEN** it returns entries preserving requestId and createdAt per persisted request, without depending on any DAO entity type

### Requirement: Direct Implementations Don't Require DAO
Direct Conversation implementations (MemoryConversation, FileConversation, etc.) MUST work without dependency on AimoChatClientDao.

#### Scenario: No DAO injection needed
- **WHEN** creating MemoryConversation or FileConversation
- **THEN** no AimoChatClientDao instance is required or injected

#### Scenario: Implementations are simpler and more testable
- **WHEN** writing unit tests for a direct implementation
- **THEN** mocking/stubbing is simpler because no DAO layer exists

