package org.ivcode.aimo.core.builder.impl

import org.ivcode.aimo.core.builder.ConversationFactory
import org.ivcode.aimo.core.builder.interceptor.ConversationInterceptor
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.conversation.ConversationImpl
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.AimoChatMessage
import java.util.UUID

/**
 * Concrete implementation of [ConversationFactory] that creates conversations from DAO/store.
 *
 * This factory creates Conversation instances from the underlying store,
 * then optionally wraps them with interceptors.
 *
 * Interceptors are applied in registration order, with the first registered interceptor
 * being the outermost link (executes first).
 *
 * This implementation is immutable - withInterceptor() returns a new factory instance
 * with the additional interceptor, ensuring thread-safety and preventing cross-request leakage.
 *
 * @property conversationStore The DAO/store for conversation persistence
 * @property interceptors Immutable list of interceptors for this factory instance
 */
class ConversationFactoryImpl(
    private val conversationStore: AimoChatClientDao,
    private val interceptors: List<ConversationInterceptor> = emptyList()
) : ConversationFactory {

    override fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory {
        return ConversationFactoryImpl(conversationStore, interceptors + interceptor)
    }
    override fun getConversation(chatId: UUID, userId: String): Conversation? {
        // Check if the conversation exists and user has access
        if(conversationStore.getChatConversation(chatId, userId) == null) {
            return null
        }

        // Create the base conversation
        val baseConversation = ConversationImpl(chatId, conversationStore, userId)

        // If no interceptors, return base conversation
        if (interceptors.isEmpty()) {
            return baseConversation
        }

        // Wrap with all registered interceptors
        return InterceptedConversation(baseConversation, interceptors)
    }
}

/**
 * Wrapped conversation that applies interceptor chain to all operations.
 *
 * Each method invocation builds a context map with operation parameters, then
 * executes the interceptor chain.
 */
private class InterceptedConversation(
    private val delegate: Conversation,
    private val interceptors: List<ConversationInterceptor>
) : Conversation {

    override val chatId: UUID
        get() = delegate.chatId

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
        val context = mutableMapOf<String, Any>(
            "operation" to "getMessages",
            "chatId" to chatId
        )
        if (maxCacheCharacters != null) {
            context["maxCacheCharacters"] = maxCacheCharacters
        }

        val chain = buildChain(interceptors, 0) { ctx ->
            val max = ctx["maxCacheCharacters"] as? Long
            delegate.getMessages(max)
        }

        @Suppress("UNCHECKED_CAST")
        return chain.proceed(context) as? List<AimoChatMessage>
    }

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) {
        val context = mutableMapOf<String, Any>(
            "operation" to "addMessages",
            "chatId" to chatId,
            "requestId" to requestId,
            "messages" to messages
        )
        if (maxCacheCharacters != null) {
            context["maxCacheCharacters"] = maxCacheCharacters
        }

        val chain = buildChain(interceptors, 0) { ctx ->
            val rid = ctx["requestId"] as UUID
            @Suppress("UNCHECKED_CAST")
            val msgs = ctx["messages"] as List<AimoChatMessage>
            val max = ctx["maxCacheCharacters"] as? Long
            delegate.addMessages(rid, msgs, max)
        }

        chain.proceed(context)
    }

    override fun getChatMetadata(): Map<String, Any> {
        val context = mutableMapOf<String, Any>(
            "operation" to "getChatMetadata",
            "chatId" to chatId
        )

        val chain = buildChain(interceptors, 0) { _ ->
            delegate.getChatMetadata()
        }

        @Suppress("UNCHECKED_CAST")
        return chain.proceed(context) as Map<String, Any>
    }

    override fun getChatProperty(property: String): Any? {
        val context = mutableMapOf<String, Any>(
            "operation" to "getChatProperty",
            "chatId" to chatId,
            "property" to property
        )

        val chain = buildChain(interceptors, 0) { ctx ->
            val prop = ctx["property"] as String
            delegate.getChatProperty(prop)
        }


        return chain.proceed(context)
    }

    override fun writeChatProperty(property: String, value: Any) {
        val context = mutableMapOf<String, Any>(
            "operation" to "writeChatProperty",
            "chatId" to chatId,
            "property" to property,
            "value" to value
        )

        val chain = buildChain(interceptors, 0) { ctx ->
            val prop = ctx["property"] as String
            val v = ctx["value"] as Any
            delegate.writeChatProperty(prop, v)
        }

        chain.proceed(context)
    }

    override fun deleteChatProperty(property: String): Boolean {
        val context = mutableMapOf<String, Any>(
            "operation" to "deleteChatProperty",
            "chatId" to chatId,
            "property" to property
        )

        val chain = buildChain(interceptors, 0) { ctx ->
            val prop = ctx["property"] as String
            delegate.deleteChatProperty(prop)
        }

        return chain.proceed(context) as Boolean
    }

    /**
     * Builds a chain of responsibility for the interceptors.
     *
     * @param interceptors List of interceptors to chain
     * @param index Current index in the interceptors list
     * @param finalAction The final action to execute after all interceptors
     * @return A Chain that will execute the interceptors and final action
     */
    private fun buildChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (MutableMap<String, Any>) -> Any?
    ): ConversationInterceptor.Chain {
        return object : ConversationInterceptor.Chain {
            override fun proceed(context: MutableMap<String, Any>): Any? {
                return if (index < interceptors.size) {
                    // Call the next interceptor
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, context)
                } else {
                    // All interceptors processed, execute final action
                    finalAction(context)
                }
            }
        }
    }
}



