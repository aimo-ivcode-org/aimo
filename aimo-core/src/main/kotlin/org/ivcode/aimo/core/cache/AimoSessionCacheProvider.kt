package org.ivcode.aimo.core.cache

import java.util.UUID

/**
 * Provider for [AimoSessionCache] instances scoped to a specific conversation.
 *
 * Providers expose a shared backing datasource, but do not need to pool wrapper
 * instances themselves. Callers may request multiple cache wrappers for the same
 * [chatId]; those wrappers should observe the same underlying cached state.
 */
interface AimoSessionCacheProvider {
    /**
     * Get a cache instance for the given conversation.
     *
     * Returned instances may be newly created lightweight wrappers. Data sharing is
     * provided by the shared datasource behind the provider, not by Kotlin object identity.
     */
    fun get(chatId: UUID): AimoSessionCache
}

/**
 * Default no-op provider that returns stateless cache instances.
 */
object NoOpAimoSessionCacheProvider : AimoSessionCacheProvider {
    override fun get(chatId: UUID): AimoSessionCache = NoOpAimoSessionCache(chatId)
}



