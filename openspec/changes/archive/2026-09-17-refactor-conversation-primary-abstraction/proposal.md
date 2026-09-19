## Why

The current architecture makes `AimoChatClientDao` a required abstraction for conversation storage. This over-constrains implementations: every Conversation must wrap a DAO, even when direct implementation would be simpler. Making `Conversation` the primary contract and removing the DAO entirely simplifies the core abstraction while maintaining flexibility and extensibility.

## What Changes

- **Conversation interface becomes the primary contract** for conversation storage; all implementations satisfy this interface directly
- **ConversationFactory creates Conversation implementations directly** (MemoryConversation, FileConversation) without DAO
- **AimoChatClientDao is completely removed** from aimo-core along with ConversationImpl
- **Simplify direct implementations**: MemoryConversation and FileConversation implement Conversation without any DAO layer
- **Two factory implementations** provide configuration flexibility: MemoryConversationFactory and FileConversationFactory

## Capabilities

### New Capabilities
- `conversation-primary-abstraction`: Refactored core abstraction making Conversation the primary contract for storage
- `direct-conversation-implementations`: Direct Conversation implementations (MemoryConversation, FileConversation)

### Modified Capabilities
- `conversation-factory`: Updated to create and manage Conversation implementations directly via specialized factories (MemoryConversationFactory, FileConversationFactory), supporting interceptors and metadata scoping

## Impact

**Code changes**:
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/conversation/` - refactor Conversation interface and create factory implementations
- `aimo-core/src/main/kotlin/org/ivcode/aimo/core/dao/` - **deleted entirely**
- `aimo-server/src/main/kotlin/org/ivcode/aimo/server/service/ChatService.kt` - no changes needed (already uses Conversation interface)
- `aimo-server/src/main/kotlin/org/ivcode/aimo/server/service/ConversationService.kt` - **migrate off `AimoChatClientDao`**; currently injects the DAO directly to power create/list/get/delete conversation and metadata upsert/delete endpoints. Must be rewired to use `ConversationFactory` (including its new `createConversation()`/`getConversations()` methods).
- `aimo-server/src/main/kotlin/org/ivcode/aimo/server/service/HistoryService.kt` - **migrate off `AimoChatClientDao`**; currently injects the DAO directly to return request-grouped chat history (`ChatHistoryRequest` with `requestId`/`createdAt`). Must be rewired to use `ConversationFactory`/`Conversation` once a grouped-history accessor exists on the interface.
- `aimo-plugin-ui/src/main/kotlin/org/ivcode/aimo/ui/controller/TitleController.kt` - **migrate off `AimoChatClientDao`**; currently injects the DAO directly (alongside `ConversationFactory`) to list all conversations for the "get all titles" endpoint. Must use `ConversationFactory.getConversations()` instead.
- Example apps (simple-ollama, simple-bedrock, mcp-client-ollama) - update bean wiring to use MemoryConversationFactory or FileConversationFactory

**Affected subsystems**:
- Core conversation storage abstraction
- Conversation factory wiring
- All storage implementations (now direct, no DAO wrapper)
- `aimo-server` conversation/history REST services (`ConversationService`, `HistoryService`)
- `aimo-plugin-ui` title listing (`TitleController`)

**Breaking changes**: `AimoChatClientDao` is a hard dependency of `ConversationService`, `HistoryService`, and `TitleController` today. These three classes must be migrated in this change (not merely "no changes needed") or the codebase will not compile once the DAO is removed.



