package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Fluent builder API for constructing chat clients with runtime composition.
 *
 * Enables composition of:
 * - Conversation (wrapped with security/audit interceptors)
 * - Models (LLM selection)
 * - Interceptors (guard-rails, logging, tracing)
 *
 * The builder defers construction until `build()` is called, allowing per-request
 * customization without mutating global state.
 *
 * **Only accepts ChatClientInterceptor**, not ConversationInterceptor.
 */
interface ChatClientBuilder {
    /**
     * Set the conversation for this chat client.
     *
     * The conversation should already be wrapped with security/audit interceptors
     * (for example, via ConversationFactory.withInterceptor(...)) before being passed here.
     *
     * @param conversation The pre-built conversation instance
     * @return this builder for chaining
     */
    fun withConversation(conversation: Conversation): ChatClientBuilder

    /**
     * Select a model by name.
     *
     * The model must be registered in the factory. If not found, build() will throw an exception.
     *
     * @param name The model name (e.g., "gpt-4", "claude-3", "llama3:8b")
     * @return this builder for chaining
     */
    fun withModel(name: String): ChatClientBuilder

    /**
     * Use an inline model configuration.
     *
     * This allows using a model config that was created programmatically rather than
     * loaded from properties. Useful for dynamic model selection or testing.
     *
     * @param config The model configuration to use
     * @return this builder for chaining
     */
    fun withModel(config: AimoChatModelConfig): ChatClientBuilder

    /**
     * Register a chat-level interceptor.
     *
     * Interceptors are applied in registration order. Builder-level interceptors execute
     * before (outside) factory-level default interceptors.
     *
     * @param interceptor The interceptor to register
     * @return this builder for chaining
     */
    fun withInterceptor(interceptor: ChatClientInterceptor): ChatClientBuilder

      /**
       * Select a chat scope for this client.
       *
       * If not set, defaults to the global scope.
       * The scope should be obtained from a `ChatScopeProvider` (or constructed explicitly in tests).
       * @param scope The chat scope to use, or null to use the global scope
       * @return this builder for chaining
       */
      fun withChatScope(scope: ChatScope?): ChatClientBuilder

    /**
     * Build the chat client with all registered components and interceptors applied.
     *
     * Resolution logic:
     * - ChatScope: Use withChatScope() selection, or default to global scope
     * - Model: Use withModel() selection, or factory primary model, or throw exception
     * - Interceptors: Builder-level (outermost) + factory defaults (innermost)
     * - Tools/SystemMessages: Taken from the selected ChatScope (no additional filtering in the builder)
     *
     * @return The composed chat client instance ready for use
     * @throws IllegalStateException if required components cannot be resolved
     */
    fun build(): AimoChatClient
}