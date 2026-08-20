package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Factory for creating immutable ChatClient instances.
 *
 * Purpose
 * - Creates AimoChatClient instances bound to conversations with configurable models, scopes, and interceptors.
 * - Does NOT perform model discovery or resolution; callers are responsible for obtaining
 *   a concrete AimoChatModelConfig before calling the factory.
 * - Exposes default interceptors so callers can inspect what will be applied by default.
 * - Supports optional inclusion of factory-level default interceptors on a per-client basis.
 *
 * Lifecycle & thread-safety
 * - Implementations are long-lived and must be safe for concurrent use (typically a singleton).
 * - Clients returned by createClient() are conversation-bound and typically single-use or
 *   request-scoped; they should be used from a request thread if not thread-safe.
 *
 * Usage
 * ```kotlin
 * // Factory is typically injected at startup (Spring @Bean, etc.)
 * val provider: ChatClientProvider = ...
 *
 * // Obtain a model (caller's responsibility; e.g., from a model registry)
 * val model = modelProvider.getModel("gpt-4")  // or getPrimaryModel(), etc.
 *
 * // Create a client for a conversation
 * val conversation = ...  // your Conversation instance
 * val client = provider.createClient(
 *     model = model,
 *     conversation = conversation,
 *     scope = null,                      // null = global scope, or select a specific scope
 *     interceptors = emptyList(),        // optional: add custom request-level interceptors
 *     includeDefaultInterceptors = true  // include factory's default interceptors
 * )
 *
 * // Use the client for chat operations
 * val response = client.chat(AimoChatRequest(prompt = "Hello", context = mapOf(...)))
 * ```
 */
interface ChatClientProvider {

     /**
      * Create an AimoChatClient bound to the provided conversation using the given configuration.
      *
      * Callers pass the resolved model and the conversation and receive a ready-to-use [AimoChatClient].
      *
      * @param model The concrete model configuration (required; must be pre-resolved by caller)
      * @param conversation The conversation instance to bind the client to
      * @param scope Optional chat scope; null means the global scope
      * @param interceptors Optional client-level interceptors to add on top of factory defaults
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
       * These interceptors are applied to all clients created with includeDefaultInterceptors=true.
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

