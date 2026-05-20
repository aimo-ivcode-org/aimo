package org.ivcode.aimo.session.cache.ehcache

import org.ehcache.Cache
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.session.cache.ehcache.utils.KeyedReentrantLock
import java.util.UUID

/**
 * Ehcache-backed session cache instance scoped to a single conversation.
 *
 * @property chatId The conversation ID this cache instance serves.
 * @param globalCache The shared underlying ehcache instance (managed by factory).
 */
internal class EhcacheSessionCache(
    override val chatId: UUID,
    private val globalCache: Cache<UUID, EhcacheCachedSessionState>,
) : AimoSessionCache {

    override fun getSessionProperty(key: String): Any? {
        return globalCache.get(chatId)?.runtimeMetadata?.get(key)
    }

    override fun getSessionProperties(): Map<String, Any> {
        return globalCache.get(chatId)?.runtimeMetadata?.toMap() ?: emptyMap()
    }

    override fun writeSessionProperty(key: String, value: Any) {
        val lock = keyedLock.acquire(chatId)
        try {
            val current = globalCache.get(chatId) ?: EhcacheCachedSessionState()
            val updated = current.runtimeMetadata.toMutableMap().apply { this[key] = value }
            globalCache.put(chatId, current.copy(runtimeMetadata = updated.toMap()))
        } finally {
            keyedLock.release(chatId, lock)
        }
    }

    override fun deleteSessionProperty(key: String): Boolean {
        val lock = keyedLock.acquire(chatId)
        return try {
            val current = globalCache.get(chatId)
            if (current == null || !current.runtimeMetadata.containsKey(key)) {
                false
            } else {
                val updated = current.runtimeMetadata.toMutableMap().apply { remove(key) }
                globalCache.put(chatId, current.copy(runtimeMetadata = updated.toMap()))
                true
            }
        } finally {
            keyedLock.release(chatId, lock)
        }
    }

    override fun appendToSessionProperty(key: String, items: List<Any>) {
        if (items.isEmpty()) return
        val lock = keyedLock.acquire(chatId)
        try {
            val current = globalCache.get(chatId) ?: EhcacheCachedSessionState()
            @Suppress("UNCHECKED_CAST")
            val existingList = (current.runtimeMetadata[key] as? List<Any>).orEmpty()
            val updated = current.runtimeMetadata.toMutableMap().apply {
                this[key] = existingList + items
            }
            globalCache.put(chatId, current.copy(runtimeMetadata = updated.toMap()))
        } finally {
            keyedLock.release(chatId, lock)
        }
    }

    override fun evict() {
        val lock = keyedLock.acquire(chatId)
        try {
            globalCache.remove(chatId)
        } finally {
            keyedLock.release(chatId, lock)
        }
    }

    companion object {
        private val keyedLock = KeyedReentrantLock<UUID>()
    }
}
