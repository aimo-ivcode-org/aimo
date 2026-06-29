package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Factory for creating chat client builders.
 *
 * This factory is initialized once at application startup from `application.yaml` properties.
 * It acts as a singleton registry for:
 * - Models (from model provider factories)
 * - Default interceptors (logging, tracing, error handling)
 *
 * Chat scope selection is handled by external code using ChatClientBuilder.withChatScope().
 * Users should inject both `ChatClientBuilderFactory` (for creating chat clients)
 * and `ConversationFactory` (for creating conversations) separately.
 */
interface ChatClientBuilderFactory {
    /**
     * Create a chat client builder with default settings.
     *
     * The builder starts with no conversation or model selection. Caller MUST:
     * - Either pass a conversation to this factory's `builder(conversation)` overload, OR
     * - Call `withConversation()` on the returned builder before calling `build()`
     *
     * Conversation is required; build() will throw IllegalStateException if not provided.
     *
     * Model resolution: Uses factory primary model if `withModel()` not called.
     *
     * @return A builder for composing a chat client
     * @throws IllegalStateException from build() if no conversation is set
     */
    fun builder(): ChatClientBuilder

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
     *     .withInterceptor(MyAccessInterceptor())
     *     .getConversation(chatId, mapOf("userId" to currentUserId))
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
    fun builder(conversation: Conversation): ChatClientBuilder

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
}
