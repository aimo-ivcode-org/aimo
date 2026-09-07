package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.ChatServiceProviderManagerImpl
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for the global scope provider-aggregation bug.
 *
 * Previously, [ChatScopeProviderImpl]'s global scope only looked up a provider with
 * id == "annotated", which never matched any real provider (AnnotatedChatServiceProvider
 * uses the Spring bean name as its id). As a result, tools contributed exclusively via
 * [ChatServiceProvider] beans (e.g. the MCP client's provider) were silently excluded
 * from the global scope and never reached the model, even though the provider was
 * correctly registered with the [org.ivcode.aimo.core.chatservice.ChatServiceProviderManager].
 */
class ChatScopeProviderGlobalScopeProviderAggregationTest {

    private class FakeToolCallback(private val toolName: String) : ToolCallback {
        override val scopes: Set<String> = emptySet()
        override val toolDefinition: ToolDefinition = ToolDefinition(
            name = toolName,
            description = "fake tool",
            inputSchema = ObjectMapper().createObjectNode(),
        )
        override fun call(argumentsJson: String, context: Map<String, Any>): String = "{}"
    }

    private class FakeNonAnnotatedProvider(
        override val id: String,
        private val tool: ToolCallback,
    ) : ChatServiceProvider {
        override val scopes: Set<String> = emptySet()
        override fun getTools(): List<ToolCallback> = listOf(tool)
        override fun getSystemMessages(): List<SystemMessageCallback> = emptyList()
    }

    @Test
    fun `global scope includes tools from non-annotated providers like MCP`() {
        val mcpTool = FakeToolCallback("mcp-server:search")
        val mcpProvider = FakeNonAnnotatedProvider(id = "mcp", tool = mcpTool)
        val providerManager = ChatServiceProviderManagerImpl(listOf(mcpProvider))

        val chatScopeProvider = ChatScopeProviderImpl(
            allTools = emptyList(),
            allSystemMessages = emptyList(),
            predefinedScopes = emptyMap(),
            providerManager = providerManager,
        )

        val globalScope = chatScopeProvider.getGlobalScope()
        val allTools = globalScope.getAllTools()

        assertTrue(
            allTools.any { it.toolDefinition.name == "mcp-server:search" },
            "Global scope should include tools from all registered providers, " +
                "not just a provider literally named 'annotated'. " +
                "Found tools: ${allTools.map { it.toolDefinition.name }}"
        )
    }
}
