package org.ivcode.aimo.session.cache.ehcache

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "aimo.session-cache.ehcache")
data class EhcacheSessionCacheProperties(
    val maxEntries: Long = 10_000,
    val ttl: Duration = Duration.ofHours(1),
)

