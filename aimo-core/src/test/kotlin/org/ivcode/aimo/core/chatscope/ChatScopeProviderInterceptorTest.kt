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
    fun `interceptor is called when getScope is invoked`() {
        var interceptorCalled = false
        var capturedOperation: String? = null
        var capturedScopeId: String? = null

        val testInterceptor = TestChatScopeProviderInterceptor { ctx ->
            interceptorCalled = true
            capturedOperation = ctx["operation"] as? String
            capturedScopeId = ctx["scopeId"] as? String
        }

        val provider = createTestProvider(listOf(testInterceptor))

        // Invoke getScope
        val scope = provider.getScope("public")

        assertTrue(interceptorCalled, "Interceptor should be called for getScope")
        assertEquals("getScope", capturedOperation)
        assertEquals("public", capturedScopeId)
        assertEquals("public", scope?.id)
    }

    @Test
    fun `interceptor is called when getScopes is invoked`() {
        var interceptorCalled = false
        var capturedOperation: String? = null

        val testInterceptor = TestChatScopeProviderInterceptor { ctx ->
            interceptorCalled = true
            capturedOperation = ctx["operation"] as? String
        }

        val provider = createTestProvider(listOf(testInterceptor))

        // Invoke getScopes
        val scopes = provider.getScopes()

        assertTrue(interceptorCalled, "Interceptor should be called for getScopes")
        assertEquals("getScopes", capturedOperation)
        assertTrue(scopes.isNotEmpty(), "Should have returned scopes")
    }

    @Test
    fun `multiple interceptors are called in order`() {
        val callOrder = mutableListOf<String>()

        val interceptor1 = TestChatScopeProviderInterceptor { _ ->
            callOrder.add("interceptor1")
        }

        val interceptor2 = TestChatScopeProviderInterceptor { _ ->
            callOrder.add("interceptor2")
        }

        val provider = createTestProvider(listOf(interceptor1, interceptor2))

        // Invoke getScope
        provider.getScope("public")

        assertEquals(listOf("interceptor1", "interceptor2"), callOrder)
    }

    @Test
    fun `interceptor can access and modify scope selection`() {
        var scopeFromContext: ChatScope? = null

        val testInterceptor = TestChatScopeProviderInterceptor { ctx ->
            scopeFromContext = ctx["scope"] as? ChatScope
        }

        val provider = createTestProvider(listOf(testInterceptor))

        // Invoke getScope
        val scope = provider.getScope("admin")

        assertEquals("admin", scopeFromContext?.id)
        assertEquals("admin", scope?.id)
    }

    @Test
    fun `global scope is always available`() {
        val provider = createTestProvider(emptyList())

        val globalScope = provider.getGlobalScope()

        assertEquals("global", globalScope.id)
    }

    // Helper classes and functions

    private fun createTestProvider(interceptors: List<ChatScopeProviderInterceptor>): ChatScopeProvider {
        // Create minimal test scopes
        val testScopes = mutableMapOf<String, ChatScope>()

        for (scopeId in listOf("public", "admin", "research")) {
            testScopes[scopeId] = ChatScope(
                id = scopeId,
                displayName = scopeId.replaceFirstChar { it.uppercase() },
                description = "Test $scopeId scope",
                tools = emptyList(),
                systemMessages = emptyList()
            )
        }

        return ChatScopeProviderImpl(
            allTools = emptyList(),
            allSystemMessages = emptyList(),
            predefinedScopes = testScopes,
            toolScopeMap = emptyMap(),
            systemMessageScopeMap = emptyMap(),
            interceptors = interceptors
        )
    }

    private class TestChatScopeProviderInterceptor(
        val onIntercept: (context: MutableMap<String, Any>) -> Unit
    ) : ChatScopeProviderInterceptor {

        override fun intercept(
            chain: ChatScopeProviderInterceptor.Chain,
            context: MutableMap<String, Any>
        ): Any? {
            onIntercept(context)
            return chain.proceed(context)
        }
    }
}


