package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig

/**
 * Concrete implementation of [ChatClientProvider].
 *
 * Creates [AimoChatClient] instances with support for request-level interceptor composition.
 * Manages a list of default interceptors to be applied to all created clients when requested.
 *
 * @property chatScopeProvider Provides chat scopes for filtering tools/system messages by scope
 * @property defaultInterceptors Default interceptors (logging, tracing, retry, etc.) to apply when creating clients
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
             interceptors + defaultInterceptors
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

         // Pre-compose the interceptor chain for non-streaming calls (reused per client).
         val nonStreamingChain = composeChatInterceptors(
             effectiveInterceptors,
             { req -> coreClient.chat(req) }
         )

         // Return a wrapped client that reuses the pre-composed chain and composes streaming chain per-call.
          return object : AimoChatClient {
              override val chatId = coreClient.chatId

              override fun chat(
                   request: org.ivcode.aimo.core.model.AimoChatRequest
               ): org.ivcode.aimo.core.model.AimoChatResponse {
                   return nonStreamingChain(request)
               }

               override fun chatStream(
                   request: org.ivcode.aimo.core.model.AimoChatRequest,
                   callback: (org.ivcode.aimo.core.model.AimoChatResponse) -> Unit
               ): org.ivcode.aimo.core.model.AimoChatResponse {
                   val base: (org.ivcode.aimo.core.model.AimoChatRequest) ->
                       org.ivcode.aimo.core.model.AimoChatResponse = { req ->
                       coreClient.chatStream(req, callback)
                   }
                   val chain = composeChatInterceptors(effectiveInterceptors, base)
                   return chain(request)
               }
           }
    }

    override fun getDefaultInterceptors(): List<ChatClientInterceptor> {
        return defaultInterceptors.toList()
    }
}
