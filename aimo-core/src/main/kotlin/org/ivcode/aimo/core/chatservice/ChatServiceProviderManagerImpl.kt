package org.ivcode.aimo.core.chatservice

/**
 * Default implementation of [ChatServiceProviderManager] that holds a mutable list
 * of providers.
 *
 * This allows providers to be registered at startup (via Spring bean injection) and
 * accessed dynamically at runtime by scope-building code.
 */
class ChatServiceProviderManagerImpl(
    private val providers: List<ChatServiceProvider> = emptyList()
) : ChatServiceProviderManager {

    override fun getProviders(): List<ChatServiceProvider> = providers

    override fun getProvider(id: String): ChatServiceProvider? = providers.find { it.id == id }
}
