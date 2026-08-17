package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Factory for creating immutable ChatClientProvider instances.
 *
 * Purpose
 * - Replaces ChatClientBuilderFactory with a lean factory focused on provider creation.
 * - Does NOT perform model discovery or resolution; callers are responsible for obtaining
 *   a concrete AimoChatModelConfig before calling the factory.
 * - Exposes default interceptors so callers can inspect what will be applied by default.
 * - Supports optional inclusion of factory-level default interceptors on a per-provider basis.
 *
 * Lifecycle & thread-safety
 * - Implementations are long-lived and must be safe for concurrent use (typically a singleton).
 * - Providers returned by createProvider() are typically single-use or reusable depending on
 *   implementation; they should be used from a request thread if not thread-safe.
 *
 * Usage
 * ```kotlin
 * // Factory is typically injected or obtained once at startup
 * val factory: ChatClientProviderFactory = ...
 *
 * // Get model (from separate model resolver)
 * val model = modelFactory.getPrimaryModel()
 *
 * // Create provider with defaults
 * val provider = factory.createProvider(
 *     model = model,
 *     scope = null,  // or select a specific scope
 *     interceptors = emptyList(),  // or add builder-level interceptors
 *     includeDefaultInterceptors = true
 * )
 *
 * // Use provider to create clients
 * val client = provider.get(conversation)
 * ```
 */
interface ChatClientProvider {

    /**
     * Create an AimoChatClient bound to the provided conversation using the given configuration.
     *
     * This unified API combines the previous provider + get(conversation) flow into a single call:
     * callers pass the resolved model and the conversation and receive a ready-to-use [AimoChatClient].
     *
     * @param model The concrete model configuration (required; must be pre-resolved by caller)
     * @param conversation The conversation instance to bind the client to
     * @param scope Optional chat scope; null means the global scope
     * @param interceptors Optional builder-level interceptors to add on top of factory defaults
     * @param includeDefaultInterceptors If true, factory-level default interceptors are included.
     *                                     If false, only the provided interceptors are used.
     *                                     Merge order: factory defaults (if included) run as innermost;
     *                                     provided interceptors run as outermost.
     * @return A new [AimoChatClient] configured with the given model, conversation, scope, and interceptors
     */
    fun createClient(
        model: AimoChatModelConfig,
        conversation: Conversation,
        scope: ChatScope? = null,
        interceptors: List<ChatClientInterceptor> = emptyList(),
        includeDefaultInterceptors: Boolean = true,
    ): AimoChatClient

     /**
      * Get an immutable snapshot of the factory's default chat-level interceptors in registration order.
      *
      * These interceptors are applied to all providers created with includeDefaultInterceptors=true.
      * Callers may inspect this list to understand what defaults will be applied or to compose
      * custom interceptor sets.
      *
      * Contract
      * - The returned list is immutable (or a defensive copy). Callers MUST NOT attempt to modify it.
      * - The list reflects interceptors bound at application startup (e.g., Spring @Bean instances).
      * - Interceptors are in registration order; implementations should apply them in that order
      *   during composition.
      *
      * @return immutable List of [ChatClientInterceptor] in registration order
      */
    fun getDefaultInterceptors(): List<ChatClientInterceptor>
}

