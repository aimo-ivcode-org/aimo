package org.ivcode.aimo.session.cache.ehcache

import org.ivcode.aimo.core.cache.AimoSessionCacheProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EhcacheSessionCacheProperties::class)
class SessionCacheEhcacheConfig {

    @Bean
    @ConditionalOnMissingBean(AimoSessionCacheProvider::class)
    fun createSessionCacheProvider(properties: EhcacheSessionCacheProperties): AimoSessionCacheProvider {
        return EhcacheRuntimeStateProvider(
            maxEntries = properties.maxEntries,
            tti = properties.tti,
        )
    }
}

