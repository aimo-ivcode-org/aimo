## 1. Refactor Conversation Interface

- [ ] 1.1 Review current Conversation interface and update javadoc to emphasize primary abstraction role
- [ ] 1.2 Ensure Conversation interface clearly documents expected behavior (message storage, metadata, scoping)
- [ ] 1.3 Add contract tests (ConversationContractTest) defining behavior all implementations must satisfy
- [ ] 1.4 Verify no breaking changes to existing Conversation methods (getMessages, addMessages, getChatMetadata, getChatProperty, writeChatProperty, deleteChatProperty)
- [ ] 1.5 Add `getHistory(maxCacheCharacters: Long? = null): List<AimoHistoryRequest>` to Conversation
  - Returns request-grouped history (chatId, requestId, messages, createdAt) using the existing `AimoHistoryRequest` model type (`aimo-core/model/AimoModel.kt`)
  - Required because `getMessages()` returns a flat message list and cannot support `HistoryService`'s `ChatHistoryRequest` (requestId/createdAt grouping used by aimo-ui's `ChatController.flattenHistory()` for sorting and stream-accumulation keys)
  - This is a new, additive method - not a breaking change to the interface
- [ ] 1.6 Add `writeChatProperties(properties: Map<String, Any>)` and `deleteChatProperties(keys: List<String>)` to Conversation
  - Batch equivalents of `writeChatProperty`/`deleteChatProperty`, applied as a single atomic storage operation
  - Required to preserve the atomicity of `ConversationService.upsertMetadata`/`deleteMetadata` (currently a single DAO call over a `Map`/`List<String>`), avoiding a semantics change to N sequential single-key writes
  - This is a new, additive method - not a breaking change to the interface

## 2. Create Direct Conversation Implementations

- [ ] 2.1 Create MemoryConversation class implementing Conversation without DAO
  - Stores messages grouped per request (chatId, requestId, messages, createdAt) in a mutable in-memory structure - do NOT depend on `org.ivcode.aimo.core.dao.ChatRequestEntity`/`ChatMessageEntity` (deleted in task 4); use `AimoHistoryRequest`/`AimoChatMessage` (or an internal equivalent within the `conversation` package) instead
  - Stores metadata in mutable Map<String, Any>
  - Implements getMessages(), getHistory(), addMessages(), getChatMetadata(), getChatProperty(), writeChatProperty(), deleteChatProperty(), writeChatProperties(), deleteChatProperties()
  - No AimoChatClientDao dependency

- [ ] 2.2 Create FileConversation class implementing Conversation without DAO
  - Persists to filesystem under configurable data directory
  - Maintains message history as serialized JSON per chatId, grouped per request (requestId, createdAt) so getHistory() can be implemented without DAO entity types
  - Handles character budget for getMessages(maxCacheCharacters) and getHistory(maxCacheCharacters)
  - No AimoChatClientDao dependency

- [ ] 2.3 Implement scope metadata enforcement in both direct implementations
  - Support optional scopeMetadata parameter on construction
  - Validate metadata matches before returning conversation data

- [ ] 2.4 Add unit tests for MemoryConversation using ConversationContractTest
- [ ] 2.5 Add unit tests for FileConversation using ConversationContractTest
- [ ] 2.6 Add unit tests for getHistory() request-grouping (requestId/createdAt preserved and correctly ordered) on both implementations

## 3. Refactor ConversationFactory

- [ ] 3.1 Update ConversationFactory interface
  - Keep withInterceptor(), getConversation(), deleteConversation() methods
  - **Add new methods**:
    - `createConversation(metadata: Map<String, Any> = emptyMap()): Conversation` - create and return new conversation
    - `getConversations(scopeMetadata: Map<String, Any> = emptyMap()): List<Conversation>` - list all conversations matching scope
  - Update javadoc to reflect that it manages the full lifecycle of conversations

- [ ] 3.2 Update ConversationFactory implementations to support lifecycle operations
  - **MemoryConversationFactory**: 
    - Implement createConversation() - create new MemoryConversation and store in backing map
    - Implement getConversations(scopeMetadata) - filter stored conversations by metadata
    - Update getConversation() and deleteConversation() to work with backing storage
    - Maintain interceptor chain logic
  
  - **FileConversationFactory**:
    - Implement createConversation() - create new FileConversation and create backing storage
    - Implement getConversations(scopeMetadata) - scan filesystem and filter by metadata
    - Update getConversation() and deleteConversation() to work with filesystem
    - Maintain interceptor chain logic

- [ ] 3.3 Create ConversationFactory implementations for each backend
  - **MemoryConversationFactory**: Implements ConversationFactory, creates MemoryConversation instances from in-memory storage
  - **FileConversationFactory**: Implements ConversationFactory, creates FileConversation instances backed by filesystem
  - Both support withInterceptor() for composing interceptors
  - Both handle scope metadata validation for all operations (get, list, create, delete)

- [ ] 3.4 Update factory scope metadata handling for all operations
  - All four operations (getConversation, getConversations, createConversation, deleteConversation) must accept optional scopeMetadata
  - Scope metadata is enforced at factory level before passing to underlying implementation
  - Interceptors can enrich/validate metadata for all operations

- [ ] 3.5 Update ConversationInterceptor interface if needed
  - Verify interceptors work for all factory operations: get, list, create, delete
  - Document that interceptors apply to all operations
  - Support metadata enrichment and access control validation

- [ ] 3.6 Add unit tests for ConversationFactory with different implementations

## 4. Delete DAO Module Entirely

- [ ] 4.1 Delete all DAO files from aimo-core
  - Delete `aimo-core/src/main/kotlin/org/ivcode/aimo/core/dao/` directory
  - Includes: AimoChatClientDao.kt, AimoChatClientDaoMemory.kt, AimoChatClientDaoFile.kt, AimoChatClientDaoEntities.kt, ConversationMetadataMatcher.kt

- [ ] 4.2 Delete ConversationImpl class
  - Delete `aimo-core/src/main/kotlin/org/ivcode/aimo/core/conversation/ConversationImpl.kt`
  - It was the DAO wrapper; no longer needed

- [ ] 4.3 Delete any DAO-related tests
  - Remove `aimo-core/src/test/` tests for DAO
  - Remove `aimo-core/src/test/` tests for ConversationImpl

- [ ] 4.4 Remove DAO imports from codebase
  - Search for `import.*dao.*` across aimo-core
  - Search for `ConversationImpl` references and replace with direct implementations
  - Verify no lingering DAO dependencies

## 5. Update Spring Configuration & Wiring

- [ ] 5.1 Update aimo-core autoconfiguration (AimoConfig.kt)
  - Remove the `conversationStore: AimoChatClientDao` parameter from the `createConversationFactory` bean; `ConversationFactoryImpl` (or its replacements) must be constructed from `MemoryConversationFactory`/`FileConversationFactory` instead
  - Make ConversationFactory bean creation flexible
  - Support configuration to choose between MemoryConversationFactory or FileConversationFactory
  - Default to MemoryConversationFactory for development
  - Update javadoc/comments to reflect new architecture (no DAO)

- [ ] 5.2 Ensure ChatClientProvider still works unchanged
  - It takes a Conversation and doesn't know implementation details
  - No changes needed to ChatClientProviderImpl

- [ ] 5.3 Verify ChatService in aimo-server works unchanged
  - It calls conversationFactory.getConversation() and uses Conversation interface
  - Should work without modification

## 5a. Migrate Direct DAO Consumers Outside aimo-core

- [ ] 5a.1 Migrate `aimo-server/service/ConversationService.kt` off `AimoChatClientDao`
  - Replace injected `AimoChatClientDao` with `ConversationFactory`
  - `createConversation(metadata)` -> `conversationFactory.createConversation(metadata)`
  - `getConversations(scopeMetadata)` -> `conversationFactory.getConversations(scopeMetadata)`
  - `getConversation(chatId, scopeMetadata)` -> `conversationFactory.getConversation(chatId, scopeMetadata)`
  - `deleteConversation(chatId, scopeMetadata)` -> `conversationFactory.deleteConversation(chatId, scopeMetadata)`
  - `getMetadata`/`upsertMetadata`/`deleteMetadata` -> delegate to `Conversation.getChatMetadata()` / `writeChatProperties()` / `deleteChatProperties()` (batch metadata methods added in task 1.6)

- [ ] 5a.2 Migrate `aimo-server/service/HistoryService.kt` off `AimoChatClientDao`
  - Replace injected `AimoChatClientDao` with `ConversationFactory`
  - `getHistory(chatId, scopeMetadata)` -> `conversationFactory.getConversation(chatId, scopeMetadata)?.getHistory()` (grouped-history accessor added in task 1.5)

- [ ] 5a.3 Migrate `aimo-plugin-ui/controller/TitleController.kt` off `AimoChatClientDao`
  - Remove the injected `AimoChatClientDao` (`conversationStore`) constructor parameter
  - `getTitles()` -> use `conversationFactory.getConversations()` instead of `conversationStore.getChatConversations()`

- [ ] 5a.4 Update/replace `ServiceTransformers.kt` dead code
  - `toChatHistoryRequest()`/`toChatConversationInfo()` extensions on `AimoHistoryRequest`/`AimoConversationInfo` already exist but are unused
  - Wire them up as the real transformers once `Conversation`/`ConversationFactory` return these (or equivalent) types; remove the now-redundant extensions on DAO entity types (`ChatConversationEntity`, `ChatRequestEntity`)

## 6. Update Example Applications

- [ ] 6.1 Update simple-ollama example
  - Verify ConversationFactory bean configuration
  - Choose and configure appropriate Conversation implementation (Memory or File)
  - Verify it still works end-to-end

- [ ] 6.2 Update simple-bedrock example
  - Same as simple-ollama

- [ ] 6.3 Update mcp-client-ollama example
  - Same as simple-ollama

- [ ] 6.4 Update any test fixtures using ConversationFactory
  - Ensure tests use appropriate implementation (MemoryConversation recommended)

## 7. Documentation & Deprecation

- [ ] 7.1 Update aimo-core/README.md
  - Reflect that Conversation is primary abstraction
  - Explain DAO has been removed entirely (remove/replace the existing `AimoChatClientDao` documentation section and code samples)
  - Document the two ways to implement storage (Memory, File)
  - Mention Future: how to add new backends

- [ ] 7.2 Update Conversation.kt interface javadoc
  - Emphasize it's the primary contract for all storage
  - Document expected scoping/metadata behavior
  - Clarify DAO is no longer part of architecture

- [ ] 7.3 Update ConversationFactory javadoc
  - Explain it creates direct Conversation implementations
  - Document interceptor support
  - Provide examples of Memory and File implementations

- [ ] 7.4 Create CONVERSATION_IMPLEMENTATIONS.md documenting
  - MemoryConversation: use case, limitations
  - FileConversation: use case, limitations
  - How to add new implementations in future

- [ ] 7.5 Add migration guide: "Removing DAO from aimo-core"
  - Explain why DAO was removed
  - Show how to migrate ConversationImpl usage to MemoryConversation or FileConversation
  - Document that ConversationFactory now creates direct implementations

- [ ] 7.6 Update example app READMEs that show DAO-based wiring
  - `examples/simple-ollama/README.md` and `examples/mcp-client-ollama/README.md` currently show `AimoChatClientDao`/DAO bean wiring snippets - update to MemoryConversationFactory/FileConversationFactory wiring
  - Check `examples/simple-bedrock/README.md` for the same pattern

## 8. Testing & Validation

- [ ] 8.1 Run full test suite: `./gradlew.bat test`
  - Verify no regressions in aimo-core tests
  - Verify example apps still pass tests
  - Confirm all DAO imports are cleaned up

- [ ] 8.2 Run example apps locally
  - `./gradlew.bat :examples:simple-ollama:bootRun`
  - Test basic chat functionality end-to-end
  - Verify message history persists correctly with new implementations

- [ ] 8.3 Verify ConversationContractTest passes for all implementations
  - MemoryConversation
  - FileConversation

- [ ] 8.4 Integration test: verify ChatService works with new architecture
  - Create conversation via factory (using direct implementation)
  - Make chat requests
  - Verify messages persist and are retrievable

## 9. Code Review & Cleanup

- [ ] 9.1 Search and remove all DAO-related imports and references
  - No remaining `import.*dao.*` statements in aimo-core
  - No remaining `ConversationImpl` references
  - No remaining `AimoChatClientDao` type references

- [ ] 9.2 Ensure consistent error handling across implementations
  - MemoryConversation and FileConversation handle edge cases consistently
  
- [ ] 9.3 Verify clean separation of concerns
  - ConversationFactory handles scoping and interceptors
  - Implementations focus only on storage

- [ ] 9.4 Search for any remaining DAO or ConversationImpl references in aimo-server
  - `ConversationService.kt` and `HistoryService.kt` directly inject `AimoChatClientDao` today - confirm task 5a.1/5a.2 migrations are complete and no references remain

- [ ] 9.5 Search for DAO/ConversationImpl references in examples and aimo-plugin-ui
  - simple-ollama, simple-bedrock, mcp-client-ollama
  - Update bean wiring to use MemoryConversationFactory or FileConversationFactory
  - `aimo-plugin-ui/controller/TitleController.kt` directly injects `AimoChatClientDao` today - confirm task 5a.3 migration is complete

- [ ] 9.6 Add CHANGELOG entry describing the refactoring
  - "Removed AimoChatClientDao and ConversationImpl; all storage backends now implement Conversation directly"
  - List files deleted
  - Explain migration path











