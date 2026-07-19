package org.ivcode.aimo.core.chatservice

/**
 * Manages all registered chat service providers.
 *
 * The provider manager acts as the source of truth for the current set of providers,
 * allowing dynamic provider discovery and tool/system message resolution at runtime.
 *
 * This supports future scenarios where providers may be added or removed between requests,
 * such as when an MCP server refreshes its tool list.
 */
interface ChatServiceProviderManager {
    /**
     * Get all currently registered providers.
     *
     * @return List of all active providers, potentially empty
     */
    fun getProviders(): List<ChatServiceProvider>

    /**
     * Get a specific provider by ID.
     *
     * @param id The provider ID
     * @return The provider if found, null otherwise
     */
    fun getProvider(id: String): ChatServiceProvider?
}
