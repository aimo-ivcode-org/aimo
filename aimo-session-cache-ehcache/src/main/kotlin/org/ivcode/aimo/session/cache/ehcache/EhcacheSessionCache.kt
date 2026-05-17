package org.ivcode.aimo.session.cache.ehcache

import org.ehcache.Cache
import org.ivcode.aimo.core.cache.AimoSessionCache
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

    private val lock = Any()

    override fun getSessionProperty(key: String): Any? {
        return globalCache.get(chatId)?.runtimeMetadata?.get(key)
    }

    override fun getSessionProperties(): Map<String, Any> {
        return globalCache.get(chatId)?.runtimeMetadata?.toMap() ?: emptyMap()
    }

    override fun writeSessionProperty(key: String, value: Any) {
        synchronized(lock) {
            val current = globalCache.get(chatId) ?: EhcacheCachedSessionState()
            val updated = current.runtimeMetadata.toMutableMap().apply { this[key] = value }
            globalCache.put(chatId, current.copy(runtimeMetadata = updated.toMap()))
        }
    }

    override fun deleteSessionProperty(key: String): Boolean {
        synchronized(lock) {
            val current = globalCache.get(chatId) ?: return false
            if (!current.runtimeMetadata.containsKey(key)) return false
            val updated = current.runtimeMetadata.toMutableMap().apply { remove(key) }
            globalCache.put(chatId, current.copy(runtimeMetadata = updated.toMap()))
            return true
        }
    }


    override fun evict() {
        globalCache.remove(chatId)
    }
}

