package org.ivcode.aimo.core.builder

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Fluent builder API for constructing chat clients with runtime composition.
 *
 * Enables composition of:
 * - Conversation (wrapped with security/audit interceptors)
 * - Models (LLM selection)
 * - Agents (tool/message scoping, Phase 2)
 * - Interceptors (guard-rails, logging, tracing)
 *
 * The builder defers construction until `build()` is called, allowing per-request
 * customization without mutating global state.
 *
 * **Only accepts ChatClientInterceptor**, not ConversationInterceptor.
 *
 * @param T The type being built (typically `AimoChatClient`)
 */
interface ChatClientBuilder<T> {
    /**
     * Set the conversation for this chat client.
     *
     * The conversation should already be wrapped with security/audit interceptors
     * via ConversationBuilder before being passed here.
     *
     * @param conversation The pre-built conversation instance
     * @return this builder for chaining
     */
    fun withConversation(conversation: Conversation): ChatClientBuilder<T>

    /**
     * Select a model by name.
     *
     * The model must be registered in the factory. If not found, build() will throw an exception.
     *
     * @param name The model name (e.g., "gpt-4", "claude-3", "llama3:8b")
     * @return this builder for chaining
     */
    fun withModel(name: String): ChatClientBuilder<T>

    /**
     * Use an inline model configuration.
     *
     * This allows using a model config that was created programmatically rather than
     * loaded from properties. Useful for dynamic model selection or testing.
     *
     * @param config The model configuration to use
     * @return this builder for chaining
     */
    fun withModel(config: AimoChatModelConfig): ChatClientBuilder<T>

    /**
     * Select an agent by ID.
     *
     * Agents control tool/message scoping (Phase 2 feature). If not specified, the builder
     * will attempt to resolve the agent from conversation metadata, or use the default agent.
     *
     * @param agentId The agent identifier
     * @return this builder for chaining
     */
    fun withAgent(agentId: String): ChatClientBuilder<T>

    /**
     * Register a chat-level interceptor.
     *
     * Interceptors are applied in registration order. Builder-level interceptors execute
     * before (outside) factory-level default interceptors.
     *
     * @param interceptor The interceptor to register
     * @return this builder for chaining
     */
    fun withInterceptor(interceptor: ChatClientInterceptor): ChatClientBuilder<T>

    /**
     * Build the chat client with all registered components and interceptors applied.
     *
     * Resolution logic:
     * - Model: Use withModel() selection, or factory primary model, or throw exception
     * - Agent: Use withAgent() selection, or read from conversation metadata, or use default
     * - Interceptors: Builder-level (outermost) + factory defaults (innermost)
     *
     * @return The composed chat client instance ready for use
     * @throws IllegalStateException if required components cannot be resolved
     */
    fun build(): T
}

