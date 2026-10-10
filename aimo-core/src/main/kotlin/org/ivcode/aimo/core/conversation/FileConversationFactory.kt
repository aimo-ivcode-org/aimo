package org.ivcode.aimo.core.conversation

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jsonMapper
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * File-backed persistent implementation of ConversationFactory.
 *
 * Creates and manages FileConversation instances, storing each conversation in a
 * separate directory under a root storage directory. Suitable for single-process
 * deployments and testing scenarios requiring persistence across application restarts.
 *
 * Each conversation is stored in a directory named by its chatId, containing:
 * - `messages.json`: Request-grouped message history
 * - `metadata.json`: Chat metadata
 *
 * Supports interceptors for auditing, caching, encryption, and other cross-cutting concerns.
 *
 * @param storageDir the root directory for conversation storage (one subdirectory per chatId)
 * @param objectMapper Jackson ObjectMapper for JSON serialization (defaults to standard Kotlin-aware config)
 * @param interceptors list of interceptors to apply to all factory operations
 */
class FileConversationFactory private constructor(
    private val storageDir: File,
    private val objectMapper: ObjectMapper,
    private val interceptors: List<ConversationInterceptor>,
    private val conversationLocks: ConcurrentHashMap<UUID, Any>
) : ConversationFactory {
    constructor(
        storageDir: File,
        objectMapper: ObjectMapper = jsonMapper(),
        interceptors: List<ConversationInterceptor> = emptyList()
    ) : this(storageDir, objectMapper, interceptors, ConcurrentHashMap())

    init {
        storageDir.mkdirs()
    }

    override fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory {
        return FileConversationFactory(storageDir, objectMapper, interceptors + interceptor, conversationLocks)
    }

    override fun createConversation(metadata: Map<String, Any>): Conversation {
        val chatId = UUID.randomUUID()
        val mutableMetadata = metadata.toMutableMap()
        val lock = conversationLock(chatId)
        
        val conversation = if (interceptors.isEmpty()) {
            FileConversation.withLock(chatId, storageDir, objectMapper, lock)
        } else {
            val chain = buildCreateChain(interceptors, 0) { _ ->
                FileConversation.withLock(chatId, storageDir, objectMapper, lock)
            }
            chain.proceed(mutableMetadata) as Conversation
        }
        
        // Persist empty metadata too, so a missing metadata file indicates a failed load.
        conversation.writeChatProperties(mutableMetadata)
        
        return conversation
    }

    override fun getConversation(chatId: UUID, metadata: Map<String, Any>): Conversation? {
        val conversationDir = File(storageDir, chatId.toString())
        val lock = conversationLock(chatId)

        return synchronized(lock) {
            if (!conversationDir.exists()) return@synchronized null

            val mutableMetadata = metadata.toMutableMap()
            val conversation = FileConversation.withLock(chatId, storageDir, objectMapper, lock)

            // Run interceptor chain first to allow enrichment of metadata
            val result = if (interceptors.isEmpty()) {
                conversation
            } else {
                val chain = buildGetChain(interceptors, 0) { _ ->
                    conversation
                }
                chain.proceed(chatId, mutableMetadata)
            }

            // Validate against metadata while writes and deletes for this chat are excluded.
            val storedMetadata = loadStoredMetadata(conversationDir)
            if (matchesScope(storedMetadata, mutableMetadata)) result else null
        }
    }

    override fun getConversations(metadata: Map<String, Any>): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        
        if (!storageDir.exists()) return emptyList()
        
        storageDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val chatIdStr = dir.name
                try {
                    val chatId = UUID.fromString(chatIdStr)
                    val lock = conversationLock(chatId)
                    synchronized(lock) {
                        val storedMetadata = loadStoredMetadata(dir)
                        if (matchesScope(storedMetadata, metadata)) {
                            conversations.add(FileConversation.withLock(chatId, storageDir, objectMapper, lock))
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    // Skip directories that are not valid UUIDs
                }
            }
        }
        
        return if (interceptors.isEmpty()) {
            conversations
        } else {
            val mutableMetadata = metadata.toMutableMap()
            val chain = buildListChain(interceptors, 0) {
                conversations
            }
            chain.proceed(mutableMetadata)
        }
    }

    override fun deleteConversation(chatId: UUID, metadata: Map<String, Any>): Boolean {
        val conversationDir = File(storageDir, chatId.toString())
        val lock = conversationLock(chatId)

        return synchronized(lock) {
            if (!conversationDir.exists()) return@synchronized false

            // Validate scope and delete under the same per-chat lock as conversation writes.
            val storedMetadata = loadStoredMetadata(conversationDir)
            if (!matchesScope(storedMetadata, metadata)) return@synchronized false

            val mutableMetadata = metadata.toMutableMap()
            if (interceptors.isEmpty()) {
                conversationDir.deleteRecursively()
            } else {
                val chain = buildDeleteChain(interceptors, 0) { _ ->
                    conversationDir.deleteRecursively()
                }
                chain.proceed(chatId, mutableMetadata)
            }
        }
    }

    private fun conversationLock(chatId: UUID): Any =
        conversationLocks.computeIfAbsent(chatId) { Any() }

    private fun loadStoredMetadata(conversationDir: File): Map<String, Any> {
        val metadataFile = File(conversationDir, "metadata.json")
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(metadataFile, Map::class.java) as Map<String, Any>
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
