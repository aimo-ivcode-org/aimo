package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * In-memory implementation of ConversationFactory.
 *
 * Creates and manages MemoryConversation instances, storing them in an in-memory map.
 * All data is lost when the application terminates. Suitable for development, testing,
 * and single-session scenarios.
 *
 * Supports interceptors for auditing, caching, and other cross-cutting concerns.
 */
class MemoryConversationFactory(
    private val interceptors: List<ConversationInterceptor> = emptyList(),
    private val conversations: MutableMap<UUID, Conversation> = mutableMapOf()
) : ConversationFactory {

    override fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory {
        return MemoryConversationFactory(interceptors + interceptor, conversations)
    }

    override fun createConversation(metadata: Map<String, Any>): Conversation {
        val chatId = UUID.randomUUID()
        val mutableMetadata = metadata.toMutableMap()
        
        val conversation = if (interceptors.isEmpty()) {
            MemoryConversation(chatId)
        } else {
            val chain = buildCreateChain(interceptors, 0) { _ ->
                MemoryConversation(chatId)
            }
            chain.proceed(mutableMetadata) as Conversation
        }
        
        // Persist metadata if provided
        if (mutableMetadata.isNotEmpty()) {
            conversation.writeChatProperties(mutableMetadata)
        }
        
        conversations[chatId] = conversation
        return conversation
    }

    override fun getConversation(chatId: UUID, metadata: Map<String, Any>): Conversation? {
        val conversation = conversations[chatId] ?: return null
        
        // Validate scope match against stored metadata
        val storedMetadata = conversation.getChatMetadata()
        if (!matchesScope(storedMetadata, metadata)) return null
        
        return if (interceptors.isEmpty()) {
            conversation
        } else {
            val mutableMetadata = metadata.toMutableMap()
            val chain = buildGetChain(interceptors, 0) { _ ->
                conversation
            }
            chain.proceed(chatId, mutableMetadata)
        }
    }

    override fun getConversations(metadata: Map<String, Any>): List<Conversation> {
        val filtered = conversations.values.filter { conversation ->
            matchesScope(conversation.getChatMetadata(), metadata)
        }
        
        return if (interceptors.isEmpty()) {
            filtered
        } else {
            val mutableMetadata = metadata.toMutableMap()
            val chain = buildListChain(interceptors, 0) {
                filtered
            }
            chain.proceed(mutableMetadata)
        }
    }

    override fun deleteConversation(chatId: UUID, metadata: Map<String, Any>): Boolean {
        val conversation = conversations[chatId] ?: return false
        
        // Validate scope match before deleting
        val storedMetadata = conversation.getChatMetadata()
        if (!matchesScope(storedMetadata, metadata)) return false
        
        val mutableMetadata = metadata.toMutableMap()
        
        return if (interceptors.isEmpty()) {
            conversations.remove(chatId) != null
        } else {
            val chain = buildDeleteChain(interceptors, 0) { _ ->
                conversations.remove(chatId) != null
            }
            chain.proceed(chatId, mutableMetadata)
        }
    }

    private fun matchesScope(storedMetadata: Map<String, Any>, requestedMetadata: Map<String, Any>): Boolean {
        // All keys in requestedMetadata must exist in storedMetadata with matching values
        return requestedMetadata.all { (key, value) ->
            storedMetadata[key] == value
        }
    }

    private fun buildCreateChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (MutableMap<String, Any>) -> Conversation
    ): ConversationInterceptor.CreateChain {
        return object : ConversationInterceptor.CreateChain {
            override fun proceed(metadata: MutableMap<String, Any>): Conversation {
                return if (index < interceptors.size) {
                    val nextChain = buildCreateChain(interceptors, index + 1, finalAction)
                    interceptors[index].interceptCreate(nextChain, metadata)
                } else {
                    finalAction(metadata)
                }
            }
        }
    }

    private fun buildGetChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (UUID) -> Conversation
    ): ConversationInterceptor.GetChain {
        return object : ConversationInterceptor.GetChain {
            override fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Conversation? {
                return if (index < interceptors.size) {
                    val nextChain = buildGetChain(interceptors, index + 1, finalAction)
                    interceptors[index].interceptGet(nextChain, chatId, metadata)
                } else {
                    finalAction(chatId)
                }
            }
        }
    }

    private fun buildListChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: () -> List<Conversation>
    ): ConversationInterceptor.ListChain {
        return object : ConversationInterceptor.ListChain {
            override fun proceed(metadata: MutableMap<String, Any>): List<Conversation> {
                return if (index < interceptors.size) {
                    val nextChain = buildListChain(interceptors, index + 1, finalAction)
                    interceptors[index].interceptList(nextChain, metadata)
                } else {
                    finalAction()
                }
            }
        }
    }

    private fun buildDeleteChain(
        interceptors: List<ConversationInterceptor>,
        index: Int,
        finalAction: (UUID) -> Boolean
    ): ConversationInterceptor.DeleteChain {
        return object : ConversationInterceptor.DeleteChain {
            override fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Boolean {
                return if (index < interceptors.size) {
                    val nextChain = buildDeleteChain(interceptors, index + 1, finalAction)
                    interceptors[index].interceptDelete(nextChain, chatId, metadata)
                } else {
                    finalAction(chatId)
                }
            }
        }
    }
}
