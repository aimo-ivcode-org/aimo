package org.ivcode.aimo.session.cache.ehcache

import org.ehcache.Cache
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.SessionCacheStats
import org.ivcode.aimo.core.cache.SessionTokenCalibration
import java.time.Duration
import java.util.UUID

internal class EhcacheSessionCache(
    maxEntries: Long,
    ttl: Duration,
) : AimoSessionCache {
    private val lock = Any()
    private val cacheManager: CacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache(
            CACHE_NAME,
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                UUID::class.java,
                EhcacheCachedSessionState::class.java,
                ResourcePoolsBuilder.heap(maxEntries.coerceAtLeast(1)),
            ).withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(ttl.coerceAtLeast(Duration.ofMinutes(1))))
        )
        .build(true)

    private val cache: Cache<UUID, EhcacheCachedSessionState> = cacheManager.getCache(
        CACHE_NAME,
        UUID::class.java,
        EhcacheCachedSessionState::class.java,
    )

    override fun getRuntimeMetadata(chatId: UUID): Map<String, Any>? {
        return cache.get(chatId)?.runtimeMetadata?.toMap()
    }

    override fun putRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>) {
        synchronized(lock) {
            val current = cache.get(chatId) ?: EhcacheCachedSessionState()
            cache.put(chatId, current.copy(runtimeMetadata = metadata.toMap()))
        }
    }

    override fun upsertRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>) {
        if (metadata.isEmpty()) return

        synchronized(lock) {
            val current = cache.get(chatId) ?: EhcacheCachedSessionState()
            cache.put(chatId, current.copy(runtimeMetadata = current.runtimeMetadata + metadata))
        }
    }

    override fun removeRuntimeMetadata(chatId: UUID, keys: List<String>) {
        if (keys.isEmpty()) return

        synchronized(lock) {
            val current = cache.get(chatId) ?: return
            val updated = current.runtimeMetadata.toMutableMap().apply { keys.forEach { remove(it) } }
            cache.put(chatId, current.copy(runtimeMetadata = updated.toMap()))
        }
    }

    override fun getMessages(chatId: UUID): List<AimoChatMessage>? {
        return cache.get(chatId)?.messages?.toList()
    }

    override fun putMessages(chatId: UUID, messages: List<AimoChatMessage>) {
        synchronized(lock) {
            val current = cache.get(chatId) ?: EhcacheCachedSessionState()
            cache.put(chatId, current.copy(messages = messages.toList()))
        }
    }

    override fun appendMessages(chatId: UUID, messages: List<AimoChatMessage>) {
        if (messages.isEmpty()) return

        synchronized(lock) {
            val current = cache.get(chatId) ?: EhcacheCachedSessionState()
            cache.put(chatId, current.copy(messages = current.messages + messages))
        }
    }

    override fun getTokenCalibration(chatId: UUID): SessionTokenCalibration? {
        return cache.get(chatId)?.tokenCalibration
    }

    override fun putTokenCalibration(chatId: UUID, calibration: SessionTokenCalibration) {
        synchronized(lock) {
            val current = cache.get(chatId) ?: EhcacheCachedSessionState()
            cache.put(chatId, current.copy(tokenCalibration = calibration))
        }
    }

    override fun getCacheStats(chatId: UUID): SessionCacheStats? {
        return cache.get(chatId)?.cacheStats
    }

    override fun putCacheStats(chatId: UUID, stats: SessionCacheStats) {
        synchronized(lock) {
            val current = cache.get(chatId) ?: EhcacheCachedSessionState()
            cache.put(chatId, current.copy(cacheStats = stats))
        }
    }

    override fun evict(chatId: UUID) {
        cache.remove(chatId)
    }

    fun close() {
        cacheManager.close()
    }

    private companion object {
        const val CACHE_NAME = "aimo-session-cache"
    }
}

private data class EhcacheCachedSessionState(
    val runtimeMetadata: Map<String, Any> = emptyMap(),
    val messages: List<AimoChatMessage> = emptyList(),
    val tokenCalibration: SessionTokenCalibration? = null,
    val cacheStats: SessionCacheStats? = null,
)

