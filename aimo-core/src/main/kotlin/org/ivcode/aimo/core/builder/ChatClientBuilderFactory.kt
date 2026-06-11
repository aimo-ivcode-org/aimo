package org.ivcode.aimo.core.builder

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Factory for creating chat client builders.
 *
 * This factory is initialized once at application startup from `application.yaml` properties.
 * It acts as a singleton registry for:
 * - Models (from model provider factories)
 * - Agents (from `aimo.agents` properties, Phase 2)
 * - Guard-rails (from `aimo.guardRails` properties, Phase 7)
 * - Default interceptors (logging, tracing, error handling)
 *
 * Users should inject both `ChatClientBuilderFactory` (for creating chat clients)
 * and `ConversationFactory` (for creating conversations) separately.
 */
interface ChatClientBuilderFactory {
    /**
     * Create a chat client builder with default settings.
     *
     * The builder starts with no conversation or model selection. Caller must either:
     * - Call `withConversation()` to set a conversation, OR
     * - Accept that no conversation history will be used
     *
     * Model resolution: Uses factory primary model if `withModel()` not called.
     *
     * @return A builder for composing a chat client
     */
    fun builder(): ChatClientBuilder<AimoChatClient>

    /**
     * Create a chat client builder with a pre-bound conversation.
     *
     * The conversation should already be wrapped with security/audit interceptors.
     *
     * Typical usage:
     * ```kotlin
     * // Inject both factories
     * val conversationFactory: ConversationFactory
     * val chatClientBuilderFactory: ChatClientBuilderFactory
     *
     * // Create conversation
     * val conversation = conversationFactory
     *     .withInterceptor(SecurityConversationInterceptor(userId))
     *     .getConversation(chatId, userId)
     *
     * // Create chat client
     * val chatClient = chatClientBuilderFactory
     *     .builder(conversation)
     *     .withModel("gpt-4")
     *     .withInterceptor(GuardRailsInterceptor())
     *     .build()
     * ```
     *
     * @param conversation The conversation to use for history storage
     * @return A builder for composing a chat client
     */
    fun builder(conversation: Conversation): ChatClientBuilder<AimoChatClient>

    /**
     * Get the list of all available model names.
     *
     * @return List of model names registered across all providers
     */
    fun getAvailableModels(): List<String>

    /**
     * Get the primary (default) model configuration.
     *
     * @return The primary model, or throws IllegalStateException if none configured
     */
    fun getPrimaryModel(): AimoChatModelConfig

    /**
     * Look up a model by name.
     *
     * @param name The model name
     * @return The model configuration, or null if not found
     */
    fun getModel(name: String): AimoChatModelConfig?

    /**
     * Look up an agent by ID (Phase 2 feature).
     *
     * @param agentId The agent identifier
     * @return The agent configuration, or null if not found
     */
    fun getAgent(agentId: String): Any? // TODO: Return proper Agent type in Phase 2
}



