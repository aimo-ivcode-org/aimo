package org.ivcode.aimo.session.cache.ehcache

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "aimo.session-cache.ehcache")
data class EhcacheSessionCacheProperties(
    val maxEntries: Long = 10_000,
    /**
     * Time-to-idle duration for session cache entries.
     *
     * After this duration of inactivity (no reads or writes), a cached entry is evicted.
     * Accessing the cache resets the idle timer. Minimum enforced value is 1 minute.
     *
     * Example: tti: 1h means conversation sessions expire after 1 hour of inactivity.
     */
    val tti: Duration = Duration.ofHours(1),
)

