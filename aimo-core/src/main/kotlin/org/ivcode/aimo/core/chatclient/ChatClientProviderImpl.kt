package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Concrete implementation of [ChatClientProviderFactory].
 *
 * This factory creates immutable ChatClientProvider instances and manages default interceptors.
 * It also provides convenience methods for model discovery (primary model lookup, model by name).
 *
 * @property modelProviderFactories Map of provider name → factory for creating models
 * @property chatScopeProvider Provider for retrieving chat scopes
 * @property defaultInterceptors Default interceptors applied to all providers (logging, tracing, error handling)
 */
internal class ChatClientProviderImpl(
    private val chatScopeProvider: ChatScopeProvider,
    private val defaultInterceptors: List<ChatClientInterceptor> = emptyList(),
): ChatClientProvider {

    override fun createClient(
        model: AimoChatModelConfig,
        conversation: Conversation,
        scope: ChatScope?,
        interceptors: List<ChatClientInterceptor>,
        includeDefaultInterceptors: Boolean,
    ): AimoChatClient {
        val effectiveInterceptors = if (includeDefaultInterceptors) {
            defaultInterceptors + interceptors
        } else {
            interceptors
        }

        val chatScope = scope ?: chatScopeProvider.getGlobalScope()

        // Construct the core client bound to the conversation
        val coreClient = AimoChatClientImpl(
            chatId = conversation.chatId,
            conversation = conversation,
            model = model,
            chatScope = chatScope,
        )

        // If no interceptors are configured, return the core client directly.
        if (effectiveInterceptors.isEmpty()) return coreClient

        // Compose the interceptor chain into a single callable for non-streaming calls.
        // For streaming calls we capture the provided callback in the base function so
        // that interceptors wrap the full stream lifecycle as intended.
        return object : AimoChatClient {
            override val chatId = coreClient.chatId

            override fun chat(request: org.ivcode.aimo.core.model.AimoChatRequest): org.ivcode.aimo.core.model.AimoChatResponse {
                val base: (org.ivcode.aimo.core.model.AimoChatRequest, MutableMap<String, Any>) -> org.ivcode.aimo.core.model.AimoChatResponse = { req, ctx ->
                    coreClient.chat(req)
                }
                val chain = composeChatInterceptors(effectiveInterceptors, base)
                val ctx = mutableMapOf<String, Any>()
                return chain(request, ctx)
            }

            override fun chatStream(request: org.ivcode.aimo.core.model.AimoChatRequest, callback: (org.ivcode.aimo.core.model.AimoChatResponse) -> Unit): org.ivcode.aimo.core.model.AimoChatResponse {
                val base: (org.ivcode.aimo.core.model.AimoChatRequest, MutableMap<String, Any>) -> org.ivcode.aimo.core.model.AimoChatResponse = { req, ctx ->
                    coreClient.chatStream(req, callback)
                }
                val chain = composeChatInterceptors(effectiveInterceptors, base)
                val ctx = mutableMapOf<String, Any>()
                return chain(request, ctx)
            }
        }
    }

    override fun getDefaultInterceptors(): List<ChatClientInterceptor> {
        return defaultInterceptors.toList()
    }
}
