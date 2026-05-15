package org.ivcode.aimo.core.cache

import java.util.UUID

/**
 * Cache abstraction for runtime data associated with a specific chat conversation.
 *
 * Each instance is scoped to a single [chatId]. DAO storage remains the source of truth
 * for durable conversation metadata. This cache provides an acceleration layer for
 * cache-lifetime state using a generic runtime property map.
 */
interface AimoSessionCache {
    /**
     * The conversation ID this cache instance serves.
     */
    val chatId: UUID

    /**
     * Runtime-only properties that live only for the cache lifetime.
     * Stored as a simple String -> Any mapping for flexibility.
     */
    fun getRuntimeProperty(key: String): Any?
    fun getRuntimeProperties(): Map<String, Any>
    fun writeRuntimeProperty(key: String, value: Any)
    fun deleteRuntimeProperty(key: String): Boolean


    fun evict()
}

