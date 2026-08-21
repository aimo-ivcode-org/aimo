## ADDED Requirements

### Requirement: Conversation Interface as Primary Contract
The aimo-core framework SHALL treat Conversation as the primary abstraction for conversation storage. All implementations MUST satisfy the Conversation interface directly.

#### Scenario: AimoChatClient uses Conversation without knowing implementation
- **WHEN** AimoChatClient requests message history
- **THEN** it calls conversation.getMessages() and receives messages regardless of storage backend

#### Scenario: Implementation flexibility without breaking changes
- **WHEN** a new storage backend (SQL, NoSQL, custom) is added
- **THEN** it can implement Conversation directly without mandatory DAO wrapper

### Requirement: Conversation Stores Messages and Metadata
The Conversation interface SHALL provide operations for storing and retrieving both message history and durable chat metadata.

#### Scenario: Add messages to conversation
- **WHEN** AimoChatClient calls conversation.addMessages(requestId, messages, maxCacheCharacters)
- **THEN** messages are persisted and available via getMessages()

#### Scenario: Manage chat metadata
- **WHEN** code calls conversation.writeChatProperty(key, value)
- **THEN** metadata is persisted and retrieved via getChatProperty(key)

#### Scenario: Batch metadata write is atomic
- **WHEN** code calls conversation.writeChatProperties(mapOf("title" to "My Chat", "pinned" to true))
- **THEN** all properties are persisted in a single storage operation, and each is retrievable via getChatProperty()

#### Scenario: Batch metadata delete is atomic
- **WHEN** code calls conversation.deleteChatProperties(listOf("title", "pinned"))
- **THEN** all listed properties are removed in a single storage operation

#### Scenario: Retrieve message history with character budget
- **WHEN** AimoChatClient calls conversation.getMessages(maxCacheCharacters=5000)
- **THEN** system returns most recent history up to 5000 characters, or all history if no limit provided

### Requirement: Conversation Identity and Scoping
Each Conversation instance MUST be scoped to a single chatId and operate within provided scope metadata for access control.

#### Scenario: Conversation instances are chatId-specific
- **WHEN** factory creates a Conversation for chatId X
- **THEN** all operations on that instance target only chatId X

#### Scenario: Scope metadata enforces access control
- **WHEN** factory.getConversation(chatId, scopeMetadata={userId: "alice"}) is called
- **THEN** returned Conversation enforces that constraint; operations outside scope return null or false

### Requirement: Conversation Exposes Request-Grouped History
The Conversation interface SHALL provide a `getHistory()` operation that returns history grouped by request (chatId, requestId, messages, createdAt), distinct from the flat message list returned by `getMessages()`.

#### Scenario: Retrieve request-grouped history
- **WHEN** code calls conversation.getHistory()
- **THEN** it returns a list of entries, each containing the requestId, createdAt timestamp, and the messages persisted together via a single addMessages() call

#### Scenario: Request-grouped history respects character budget
- **WHEN** code calls conversation.getHistory(maxCacheCharacters=1000)
- **THEN** only the most recent request-groups totaling up to 1000 characters are returned, consistent with getMessages(maxCacheCharacters) budgeting behavior

#### Scenario: Request-grouped history preserves chronological order
- **WHEN** multiple addMessages() calls have been made for a conversation
- **THEN** conversation.getHistory() returns entries ordered by createdAt ascending


