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
        val chain = buildChain(interceptors, 0) { cid, md ->
            if (conversationStore.getChatConversation(cid, md) == null) {
                return@buildChain null
            }
            ConversationImpl(cid, conversationStore, md.toMap())
        }
        return chain.proceed(chatId, metadata.toMutableMap())
    }

    private fun buildChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (UUID, MutableMap<String, Any>) -> Conversation?
    ): ConversationInterceptor.Chain {
        return object : ConversationInterceptor.Chain {
            override fun proceed(cid: UUID, metadata: MutableMap<String, Any>): Conversation? {
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
