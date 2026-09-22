package org.ivcode.aimo.core.conversation

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jsonMapper
import java.io.File
import java.util.UUID

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
class FileConversationFactory(
    private val storageDir: File,
    private val objectMapper: ObjectMapper = jsonMapper(),
    private val interceptors: List<ConversationInterceptor> = emptyList()
) : ConversationFactory {
    init {
        storageDir.mkdirs()
    }

    override fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory {
        return FileConversationFactory(storageDir, objectMapper, interceptors + interceptor)
    }

    override fun createConversation(metadata: Map<String, Any>): Conversation {
        val chatId = UUID.randomUUID()
        val mutableMetadata = metadata.toMutableMap()
        
        val conversation = if (interceptors.isEmpty()) {
            FileConversation(chatId, storageDir, objectMapper)
        } else {
            val chain = buildCreateChain(interceptors, 0) { _ ->
                FileConversation(chatId, storageDir, objectMapper)
            }
            chain.proceed(mutableMetadata) as Conversation
        }
        
        // Persist metadata if provided
        if (mutableMetadata.isNotEmpty()) {
            conversation.writeChatProperties(mutableMetadata)
        }
        
        return conversation
    }

    override fun getConversation(chatId: UUID, metadata: Map<String, Any>): Conversation? {
        val conversationDir = File(storageDir, chatId.toString())
        if (!conversationDir.exists()) return null
        
        // Load the stored metadata and validate scope match
        val storedMetadata = loadStoredMetadata(conversationDir)
        if (!matchesScope(storedMetadata, metadata)) return null
        
        val conversation = FileConversation(chatId, storageDir, objectMapper, storedMetadata)
        
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
        val conversations = mutableListOf<Conversation>()
        
        if (!storageDir.exists()) return emptyList()
        
        storageDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val chatIdStr = dir.name
                try {
                    val chatId = UUID.fromString(chatIdStr)
                    val storedMetadata = loadStoredMetadata(dir)
                    if (matchesScope(storedMetadata, metadata)) {
                        conversations.add(FileConversation(chatId, storageDir, objectMapper, storedMetadata))
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
        if (!conversationDir.exists()) return false
        
        val mutableMetadata = metadata.toMutableMap()
        
        return if (interceptors.isEmpty()) {
            conversationDir.deleteRecursively()
        } else {
            val chain = buildDeleteChain(interceptors, 0) { _ ->
                conversationDir.deleteRecursively()
            }
            chain.proceed(chatId, mutableMetadata)
        }
    }

    private fun loadStoredMetadata(conversationDir: File): Map<String, Any> {
        val metadataFile = File(conversationDir, "metadata.json")
        if (!metadataFile.exists()) return emptyMap()
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(metadataFile, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            emptyMap()
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
