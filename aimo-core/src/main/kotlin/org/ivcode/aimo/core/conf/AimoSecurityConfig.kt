package org.ivcode.aimo.core.conf

import org.ivcode.aimo.core.security.AimoUserProvider
import org.ivcode.aimo.core.security.GlobalUserProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for user isolation and security.
 *
 * Provides default beans for user provider and related security components.
 * Can be overridden by application-specific configurations.
 */
@Configuration
class AimoSecurityConfig {

    /**
     * Default UserProvider bean: GlobalUserProvider.
     *
     * This is used when no other UserProvider bean is registered.
     * In global mode, all requests use the "global" userId, effectively
     * giving all requests access to all conversations.
     *
     * Can be overridden by registering a different AimoUserProvider bean.
     */
    @Bean
    @ConditionalOnMissingBean(AimoUserProvider::class)
    fun aimoUserProvider(): AimoUserProvider = GlobalUserProvider()
}

