package org.ivcode.aimo.core.chatservice

/**
 * Default implementation of [ChatServiceProviderManager] backed by a fixed list of providers.
 *
 * Providers are registered at startup (via Spring bean injection) and can be queried at runtime.
 */
class ChatServiceProviderManagerImpl(
    private val providers: List<ChatServiceProvider> = emptyList()
) : ChatServiceProviderManager {

    override fun getProviders(): List<ChatServiceProvider> = providers

    override fun getProvider(id: String): ChatServiceProvider? = providers.find { it.id == id }
}
