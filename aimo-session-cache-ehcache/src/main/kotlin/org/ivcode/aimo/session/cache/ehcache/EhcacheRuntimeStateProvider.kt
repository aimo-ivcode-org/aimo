package org.ivcode.aimo.session.cache.ehcache

import org.ehcache.Cache
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.AimoSessionCacheProvider
import java.time.Duration
import java.util.UUID

/**
 * Ehcache-backed session cache provider.
 *
 * Maintains a shared ehcache datasource and returns lightweight conversation-scoped
 * wrappers over that datasource. Multiple wrappers for the same [chatId] observe the
 * same underlying cached state.
 */
internal class EhcacheRuntimeStateProvider(
    maxEntries: Long,
    ttl: Duration,
) : AimoSessionCacheProvider {

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

    override fun get(chatId: UUID): AimoSessionCache = EhcacheSessionCache(chatId, cache)

    fun close() {
        cacheManager.close()
    }

    private companion object {
        const val CACHE_NAME = "aimo-session-cache"
    }
}

internal data class EhcacheCachedSessionState(
    val runtimeMetadata: Map<String, Any> = emptyMap(),
)


