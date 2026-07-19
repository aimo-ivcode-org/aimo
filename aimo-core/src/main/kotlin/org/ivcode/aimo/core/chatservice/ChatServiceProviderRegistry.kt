package org.ivcode.aimo.core.chatservice

/**
 * Registry for collecting providers from different sources.
 * Each source (annotated, MCP, etc.) can implement this to provide its own set of ChatServiceProviders.
 * Spring will collect all registries, and we flatten them to get all providers.
 */
interface ChatServiceProviderRegistry {
    /**
     * Get all providers from this registry source.
     */
    fun getProviders(): List<ChatServiceProvider>
}

