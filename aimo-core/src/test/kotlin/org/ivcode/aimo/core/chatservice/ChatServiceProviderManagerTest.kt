package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ChatServiceProviderManager.
 *
 * Verifies that the provider manager correctly manages and retrieves providers.
 */
class ChatServiceProviderManagerTest {

    @Test
    fun `manager returns all registered providers`() {
        val provider1 = createMockProvider("provider1")
        val provider2 = createMockProvider("provider2")

        val manager = ChatServiceProviderManagerImpl(listOf(provider1, provider2))
        val providers = manager.getProviders()

        assertEquals(2, providers.size)
        assertTrue(providers.contains(provider1))
        assertTrue(providers.contains(provider2))
    }

    @Test
    fun `manager retrieves provider by id`() {
        val provider1 = createMockProvider("provider1")
        val provider2 = createMockProvider("provider2")

        val manager = ChatServiceProviderManagerImpl(listOf(provider1, provider2))

        assertEquals(provider1, manager.getProvider("provider1"))
        assertEquals(provider2, manager.getProvider("provider2"))
    }

    @Test
    fun `manager returns null for unknown provider id`() {
        val provider1 = createMockProvider("provider1")
        val manager = ChatServiceProviderManagerImpl(listOf(provider1))

        assertNull(manager.getProvider("unknown"))
    }

    @Test
    fun `manager handles empty provider list`() {
        val manager = ChatServiceProviderManagerImpl(emptyList())

        assertTrue(manager.getProviders().isEmpty())
        assertNull(manager.getProvider("any-id"))
    }

    @Test
    fun `manager preserves provider order`() {
        val providers = (1..5).map { createMockProvider("provider$it") }
        val manager = ChatServiceProviderManagerImpl(providers)

        val retrieved = manager.getProviders()
        assertEquals(providers, retrieved)
    }

    // Helper methods

    private fun createMockProvider(id: String, scopes: Set<String> = emptySet()): ChatServiceProvider {
        return object : ChatServiceProvider {
            override val id = id
            override val scopes = scopes

            override fun getTools(): List<ToolCallback> = emptyList()
            override fun getSystemMessages(): List<SystemMessageCallback> = emptyList()
        }
    }
}
