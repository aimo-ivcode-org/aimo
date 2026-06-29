package org.ivcode.aimo.core.conversation

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.dao.AimoChatClientDao
import java.util.UUID

class ConversationFactoryImpl(
    private val conversationStore: AimoChatClientDao,
    private val interceptors: List<ConversationInterceptor> = emptyList()
) : ConversationFactory {

    override fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory {
        return ConversationFactoryImpl(conversationStore, interceptors + interceptor)
    }

    override fun getConversation(chatId: UUID, metadata: Map<String, Any>): Conversation? {
        if (conversationStore.getChatConversation(chatId, metadata) == null) {
            return null
        }

        val baseConversation = ConversationImpl(chatId, conversationStore, metadata)

        if (interceptors.isEmpty()) {
            return baseConversation
        }

        return InterceptedConversation(baseConversation, interceptors, metadata.toMutableMap())
    }
}

private class InterceptedConversation(
    private val delegate: Conversation,
    private val interceptors: List<ConversationInterceptor>,
    private val scopeMetadata: MutableMap<String, Any>,
) : Conversation {

    override val chatId: UUID
        get() = delegate.chatId

    override fun getMessages(maxCacheCharacters: Long?): List<AimoChatMessage>? {
        val metadata = scopeMetadata.toMutableMap()
        if (maxCacheCharacters != null) {
            metadata["maxCacheCharacters"] = maxCacheCharacters
        }

        val chain = buildChain(interceptors, 0) { cid, md ->
            val max = md["maxCacheCharacters"] as? Long
            delegate.getMessages(max)
        }

        @Suppress("UNCHECKED_CAST")
        return chain.proceed(chatId, metadata) as? List<AimoChatMessage>
    }

    override fun addMessages(requestId: UUID, messages: List<AimoChatMessage>, maxCacheCharacters: Long?) {
        val metadata = scopeMetadata.toMutableMap().apply {
            put("requestId", requestId)
            put("messages", messages)
            if (maxCacheCharacters != null) {
                put("maxCacheCharacters", maxCacheCharacters)
            }
        }

        val chain = buildChain(interceptors, 0) { cid, md ->
            val rid = md["requestId"] as UUID
            @Suppress("UNCHECKED_CAST")
            val msgs = md["messages"] as List<AimoChatMessage>
            val max = md["maxCacheCharacters"] as? Long
            delegate.addMessages(rid, msgs, max)
        }

        chain.proceed(chatId, metadata)
    }

    override fun getChatMetadata(): Map<String, Any> {
        val metadata = scopeMetadata.toMutableMap()

        val chain = buildChain(interceptors, 0) { _, _ ->
            delegate.getChatMetadata()
        }

        @Suppress("UNCHECKED_CAST")
        return chain.proceed(chatId, metadata) as Map<String, Any>
    }

    override fun getChatProperty(property: String): Any? {
        val metadata = scopeMetadata.toMutableMap().apply {
            put("property", property)
        }

        val chain = buildChain(interceptors, 0) { _, md ->
            val prop = md["property"] as String
            delegate.getChatProperty(prop)
        }

        return chain.proceed(chatId, metadata)
    }

    override fun writeChatProperty(property: String, value: Any) {
        val metadata = scopeMetadata.toMutableMap().apply {
            put("property", property)
            put("value", value)
        }

        val chain = buildChain(interceptors, 0) { _, md ->
            val prop = md["property"] as String
            val v = md["value"] as Any
            delegate.writeChatProperty(prop, v)
        }

        chain.proceed(chatId, metadata)
    }

    override fun deleteChatProperty(property: String): Boolean {
        val metadata = scopeMetadata.toMutableMap().apply {
            put("property", property)
        }

        val chain = buildChain(interceptors, 0) { _, md ->
            val prop = md["property"] as String
            delegate.deleteChatProperty(prop)
        }

        return chain.proceed(chatId, metadata) as Boolean
    }

    private fun buildChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (UUID, MutableMap<String, Any>) -> Any?
    ): ConversationInterceptor.Chain {
        return object : ConversationInterceptor.Chain {
            override fun proceed(cid: UUID, metadata: MutableMap<String, Any>): Any? {
                return if (index < interceptors.size) {
                    val nextChain = buildChain(interceptors, index + 1, finalAction)
                    interceptors[index].intercept(nextChain, cid, metadata)
                } else {
                    finalAction(cid, metadata)
                }
            }
        }
    }
}
