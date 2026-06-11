package org.ivcode.aimo.core.builder.impl

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.builder.ChatClientBuilder
import org.ivcode.aimo.core.builder.interceptor.ChatClientInterceptor
import org.ivcode.aimo.core.client.chat.AimoChatClientImpl
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.controller.SystemMessageCallback
import java.util.UUID

/**
 * Concrete implementation of [ChatClientBuilder] that composes chat clients with interceptors.
 *
 * This builder collects configuration during the building phase and applies all interceptors
 * when `build()` is called. Interceptors are applied in registration order, with builder-level
 * interceptors executing before (outside) factory defaults.
 *
 * @property conversation The conversation instance for history storage (optional until build)
 * @property selectedModel The selected model configuration (null means use factory primary)
 * @property selectedAgent The selected agent ID (null means resolve from conversation or use default)
 * @property builderInterceptors Builder-level interceptors registered via withInterceptor()
 * @property factoryDefaultInterceptors Factory-level default interceptors (logging, tracing, error handling)
 * @property toolCallbacks All registered tool callbacks
 * @property systemMessages All registered system message callbacks
 * @property getPrimaryModel Lambda to resolve primary model from factory
 * @property getModelByName Lambda to resolve model by name from factory
 */
class ChatClientBuilderImpl(
    private var conversation: Conversation? = null,
    private val factoryDefaultInterceptors: List<ChatClientInterceptor>,
    private val toolCallbacks: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val getPrimaryModel: () -> AimoChatModelConfig,
    private val getModelByName: (String) -> AimoChatModelConfig?,
) : ChatClientBuilder<AimoChatClient> {

    private var selectedModel: AimoChatModelConfig? = null
    private var selectedAgent: String? = null
    private val builderInterceptors = mutableListOf<ChatClientInterceptor>()

    override fun withConversation(conversation: Conversation): ChatClientBuilder<AimoChatClient> {
        this.conversation = conversation
        return this
    }

    override fun withModel(name: String): ChatClientBuilder<AimoChatClient> {
        this.selectedModel = getModelByName(name)
            ?: throw IllegalArgumentException("Model not found: $name")
        return this
    }

    override fun withModel(config: AimoChatModelConfig): ChatClientBuilder<AimoChatClient> {
        this.selectedModel = config
        return this
    }

    override fun withAgent(agentId: String): ChatClientBuilder<AimoChatClient> {
        this.selectedAgent = agentId
        return this
    }

    override fun withInterceptor(interceptor: ChatClientInterceptor): ChatClientBuilder<AimoChatClient> {
        builderInterceptors.add(interceptor)
        return this
    }

    override fun build(): AimoChatClient {
        // Resolve model: use selected, or factory primary
        val model = selectedModel ?: getPrimaryModel()

        // Resolve conversation (optional - some use cases might not need history)
        val conv = conversation
            ?: throw IllegalStateException("Conversation is required for ChatClient")

        // Create base AimoChatClient
        val baseChatClient: AimoChatClient = AimoChatClientImpl(
            chatId = conv.chatId,
            conversation = conv,
            model = model,
            tools = toolCallbacks,
            systemMessages = systemMessages,
        )

        // If no interceptors, return base client
        val allInterceptors = builderInterceptors + factoryDefaultInterceptors
        if (allInterceptors.isEmpty()) {
            return baseChatClient
        }

        // Wrap with interceptors
        return InterceptedChatClient(baseChatClient, allInterceptors)
    }
}


/**
 * Wrapped chat client that applies interceptor chain to all operations.
 */
private class InterceptedChatClient(
    private val delegate: AimoChatClient,
    private val interceptors: List<ChatClientInterceptor>
) : AimoChatClient {

    override val chatId: UUID
        get() = delegate.chatId

    override fun chat(request: AimoChatRequest): AimoChatResponse {
        val context = mutableMapOf<String, Any>(
            "operation" to "chat",
            "chatId" to chatId,
            "request" to request
        )

        val chain = buildChain(interceptors, 0) { ctx ->
            @Suppress("UNCHECKED_CAST")
            val req = ctx["request"] as AimoChatRequest
            delegate.chat(req)
        }

        return chain.proceed(context)
    }

    override fun chatStream(request: AimoChatRequest, callback: (AimoChatResponse) -> Unit): AimoChatResponse {
        val context = mutableMapOf<String, Any>(
            "operation" to "chatStream",
            "chatId" to chatId,
            "request" to request,
            "callback" to callback
        )

        val chain = buildChain(interceptors, 0) { ctx ->
            @Suppress("UNCHECKED_CAST")
            val req = ctx["request"] as AimoChatRequest
            @Suppress("UNCHECKED_CAST")
            val cb = ctx["callback"] as (AimoChatResponse) -> Unit
            delegate.chatStream(req, cb)
        }

        return chain.proceed(context)
    }

    private fun buildChain(
        interceptors: List<ChatClientInterceptor>,
        index: Int,
        finalAction: (MutableMap<String, Any>) -> AimoChatResponse
    ): ChatClientInterceptor.Chain {
        return object : ChatClientInterceptor.Chain {
            override fun proceed(context: MutableMap<String, Any>): AimoChatResponse {
                return if (index < interceptors.size) {
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, context)
                } else {
                    finalAction(context)
                }
            }
        }
    }
}

