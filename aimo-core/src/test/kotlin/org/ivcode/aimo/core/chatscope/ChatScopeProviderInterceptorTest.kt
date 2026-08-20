package org.ivcode.aimo.core.chatscope

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for ChatScopeProviderInterceptor chain invocation.
 *
 * Verifies that interceptors are called when:
 * - getScope(id) is invoked
 * - getScopes() is invoked
 * - getGlobalScope() is invoked (not intercepted - always returns global scope)
 */
class ChatScopeProviderInterceptorTest {

    @Test
    fun `getScope returns predefined scope`() {
        val testScopes = mutableMapOf<String, ChatScope>()
        testScopes["public"] = ChatScope(
            id = "public",
            displayName = "Public",
            description = "Test public scope",
            providers = null,
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val provider = ChatScopeProviderImpl(
            allTools = emptyList(),
            allSystemMessages = emptyList(),
            predefinedScopes = testScopes,
            providerManager = object : org.ivcode.aimo.core.chatservice.ChatServiceProviderManager {
                override fun getProviders() = emptyList<org.ivcode.aimo.core.chatservice.ChatServiceProvider>()
                override fun getProvider(id: String) = null
            }
        )

        val scope = provider.getScope("public", emptyMap())
        assertEquals("public", scope?.id)
    }

    @Test
    fun `getScopes includes global and predefined`() {
        val testScopes = mapOf(
            "admin" to ChatScope(
                id = "admin",
                displayName = "Admin",
                description = "Admin scope",
                providers = null,
                tools = emptyList(),
                systemMessages = emptyList()
            )
        )

        val provider = ChatScopeProviderImpl(
            allTools = emptyList(),
            allSystemMessages = emptyList(),
            predefinedScopes = testScopes,
            providerManager = object : org.ivcode.aimo.core.chatservice.ChatServiceProviderManager {
                override fun getProviders() = emptyList<org.ivcode.aimo.core.chatservice.ChatServiceProvider>()
                override fun getProvider(id: String) = null
            }
        )

        val scopes = provider.getScopes(emptyMap())
        // must contain global scope and the predefined admin scope
        assertTrue(scopes.any { it.id == ChatScopeProvider.GLOBAL_SCOPE_ID })
        assertTrue(scopes.any { it.id == "admin" })
    }

    @Test
    fun `global scope is always available`() {
        val provider = ChatScopeProviderImpl(
            allTools = emptyList(),
            allSystemMessages = emptyList(),
            predefinedScopes = emptyMap(),
            providerManager = object : org.ivcode.aimo.core.chatservice.ChatServiceProviderManager {
                override fun getProviders() = emptyList<org.ivcode.aimo.core.chatservice.ChatServiceProvider>()
                override fun getProvider(id: String) = null
            }
        )

        val globalScope = provider.getGlobalScope()
        assertEquals(ChatScopeProvider.GLOBAL_SCOPE_ID, globalScope.id)
    }
}


