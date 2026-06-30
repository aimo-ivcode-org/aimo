package org.ivcode.aimo.core.conversation

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
        if (interceptors.isEmpty()) {
            // No interceptors: check DAO and build conversation directly
            if (conversationStore.getChatConversation(chatId, metadata) == null) {
                return null
            }
            return ConversationImpl(chatId, conversationStore, metadata)
        }

        // Build the interceptor chain for get operation and proceed
        // Interceptors can mutate metadata before DAO scoping occurs
        val chain = buildGetChain(interceptors, 0) { cid, md ->
            if (conversationStore.getChatConversation(cid, md) == null) {
                return@buildGetChain null
            }
            ConversationImpl(cid, conversationStore, md.toMap())
        }
        return chain.proceed(chatId, metadata.toMutableMap())
    }

    override fun deleteConversation(chatId: UUID, metadata: Map<String, Any>): Boolean {
        if (interceptors.isEmpty()) {
            return conversationStore.deleteChatConversation(chatId, metadata)
        }

        // Build the interceptor chain for delete operation and proceed
        val chain = buildDeleteChain(interceptors, 0) { cid, md ->
            conversationStore.deleteChatConversation(cid, md)
        }
        return chain.proceed(chatId, metadata.toMutableMap())
    }

    private fun buildGetChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (UUID, MutableMap<String, Any>) -> Conversation?
    ): ConversationInterceptor.GetChain {
        return object : ConversationInterceptor.GetChain {
            override fun proceed(cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
                return if (index < interceptors.size) {
                    val nextChain = buildGetChain(interceptors, index + 1, finalAction)
                    interceptors[index].interceptGet(nextChain, cid, metadata)
                } else {
                    finalAction(cid, metadata)
                }
            }
        }
    }

    private fun buildDeleteChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (UUID, MutableMap<String, Any>) -> Boolean
    ): ConversationInterceptor.DeleteChain {
        return object : ConversationInterceptor.DeleteChain {
            override fun proceed(cid: UUID, metadata: MutableMap<String, Any>): Boolean {
                return if (index < interceptors.size) {
                    val nextChain = buildDeleteChain(interceptors, index + 1, finalAction)
                    interceptors[index].interceptDelete(nextChain, cid, metadata)
                } else {
                    finalAction(cid, metadata)
                }
            }
        }
    }
}
