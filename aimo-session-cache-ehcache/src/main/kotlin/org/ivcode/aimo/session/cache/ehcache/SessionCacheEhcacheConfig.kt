package org.ivcode.aimo.session.cache.ehcache

import org.ivcode.aimo.core.cache.AimoSessionCache
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EhcacheSessionCacheProperties::class)
class SessionCacheEhcacheConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AimoSessionCache::class)
    fun createSessionCache(properties: EhcacheSessionCacheProperties): AimoSessionCache {
        return EhcacheSessionCache(
            maxEntries = properties.maxEntries,
            ttl = properties.ttl,
        )
    }
}

