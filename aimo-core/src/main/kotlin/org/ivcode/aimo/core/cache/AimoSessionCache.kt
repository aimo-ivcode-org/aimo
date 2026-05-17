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
    fun getSessionProperty(key: String): Any?
    fun getSessionProperties(): Map<String, Any>
    fun writeSessionProperty(key: String, value: Any)
    fun deleteSessionProperty(key: String): Boolean

    /**
     * Atomically appends items to a list property.
     *
     * This is an atomic read-modify-write operation: the current list is fetched,
     * items are appended, and the updated list is written back as a single unit.
     * Concurrent calls are serialized to prevent lost updates.
     *
     * @param key The property key (should reference a List<T>).
     * @param items The items to append.
     */
    fun appendToSessionProperty(key: String, items: List<Any>)

    fun evict()
}

