# Plan: Implement Phase 2 ChatScopes

ChatScopes are scoped collections of tools with customizable system messages. A ChatScope defines which tools and system messages are available in a conversation—the autonomous decision-making capabilities. ChatScopes are purely metadata (identifiers); system messages and context are resolved at chat time by the ChatClient builder. Every aimo instance has a built-in **global scope** that includes all available tools; predefined scopes can restrict this. The implementation requires:
1. **Annotation enhancements** to support chat scope on `@ChatService`, `@Tool`, and `@SystemMessage`
2. **ChatScope model & registry** for storing predefined and runtime-created chat scopes
3. **SystemMessageContext enhancement** to include `chatScopeId` for system message filtering
4. **ChatScope provider service** with optional interceptor support for filtering/access control
5. **Runtime tool/system-message filtering** in the ChatClient builder to pass only chat-scope-allowed components
6. **DAO integration** for persisting chat scope selection in conversation metadata via existing `writeChatProperty` mechanism

## Steps

1. **Enhance annotations** with optional `scope: Array<String>` property on `@ChatService`, `@Tool`, and `@SystemMessage` in `Annotations.kt`. If unset, component is available to all chat scopes (backwards compatible).

2. **Create ChatScope model classes** in new `aimo-core/.../chatscope/` package: `ChatScope` (id, displayName, description, systemMessageIds list, toolIds list), `ChatScopeConfig` (YAML-deserializable from `aimo.scope`), and `ChatScopeProvider` interface for retrieving chat scopes with optional interceptor chain support. ChatScope is purely metadata (which tools and system messages apply); the actual system messages are invoked at chat time by the ChatClient builder after filtering.

3. **Enhance SystemMessageContext** in `SystemMessageContext.kt` to add `chatScopeId: String?` field so system message callbacks can be aware of the active scope. SystemMessageContext is populated at chat time with request-provided context; update discovery in `ControllerHelpers.kt` to track which system messages belong to which scopes.

4. **Update tool/system message discovery** in `AimoConfig.kt` and `ChatServiceEntity.kt` to preserve tool/system-message-to-chatscope mappings at startup.

5. **Add chat scope selection to ChatClientBuilder**: new parameter `withChatScope(chatScopeId: String)` in `ChatClientBuilder.kt` and implementation in `ChatClientBuilderImpl.kt`. At `build()` time, the builder resolves the selected ChatScope, filters available tools and system messages to include only those in the scope, and passes the filtered lists to `AimoChatClientImpl`. System messages are invoked at chat time with context-of-the-moment.

6. **Update AimoChatClientImpl constructor** to accept filtered tool and system-message lists (no changes to core loop logic).

7. **Register ChatScopeProvider as Spring bean** in `AimoConfig.kt`; expose via `ChatClientBuilderFactory.getChatScopes()` and `ChatClientBuilderFactory.getChatScope(id)` for UI queries.

8. **Store chat scope selection in conversation metadata**: Add helper in `Conversation.kt` (e.g., `getSelectedChatScope(): String?` and `setSelectedChatScope(chatScopeId: String)`) that wraps `getChatProperty`/`writeChatProperty` for the reserved key `"aimo.chatScopeId"`.

9. **Update server controller** `ChatController.kt` to optionally read `chatScopeId` from request body and pass to builder via `withChatScope()`.

10. **Configuration loading**: Add YAML deserialization in `AimoConfig.kt` for predefined chat scopes under `aimo.scope:` with schema validation.






















