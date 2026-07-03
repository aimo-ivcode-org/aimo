package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for dynamic scope filtering with providers.
 *
 * Verifies that ChatScope correctly applies two-condition AND filtering:
 * - Provider's scope set allows the scope id
 * - AND callback's scope set allows the scope id
 */
class ChatScopeDynamicFilteringTest {

    @Test
    fun `global scope with unrestricted provider and tools`() {
        val tool1 = createMockToolCallback("tool1", scopes = emptySet())
        val tool2 = createMockToolCallback("tool2", scopes = emptySet())
        
        val provider = createMockProvider("provider1", scopes = emptySet(), tools = listOf(tool1, tool2))

        val scope = ChatScope(
            id = "global",
            displayName = "Global",
            description = "Global scope",
            providers = listOf(provider),
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        assertEquals(2, allTools.size)
        assertTrue(allTools.any { it.toolDefinition.name == "tool1" })
        assertTrue(allTools.any { it.toolDefinition.name == "tool2" })
    }

    @Test
    fun `restricted provider excludes all tools when scope not in provider scopes`() {
        val tool1 = createMockToolCallback("tool1", scopes = emptySet())
        val tool2 = createMockToolCallback("tool2", scopes = emptySet())
        
        // Provider restricted to "admin" scope
        val provider = createMockProvider("provider1", scopes = setOf("admin"), tools = listOf(tool1, tool2))

        val scope = ChatScope(
            id = "global",
            displayName = "Global",
            description = "Global scope",
            providers = listOf(provider),
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        // Provider doesn't allow "global" scope, so no tools
        assertEquals(0, allTools.size)
    }

    @Test
    fun `restricted tool excludes tool when scope not in tool scopes`() {
        val tool1 = createMockToolCallback("tool1", scopes = setOf("admin"))
        val tool2 = createMockToolCallback("tool2", scopes = emptySet())
        
        val provider = createMockProvider("provider1", scopes = emptySet(), tools = listOf(tool1, tool2))

        val scope = ChatScope(
            id = "global",
            displayName = "Global",
            description = "Global scope",
            providers = listOf(provider),
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        // tool1 is restricted to "admin" scope only, so only tool2 is included
        assertEquals(1, allTools.size)
        assertEquals("tool2", allTools[0].toolDefinition.name)
    }

    @Test
    fun `both provider and tool restricted to same scope includes tool`() {
        val tool1 = createMockToolCallback("tool1", scopes = setOf("admin"))
        val tool2 = createMockToolCallback("tool2", scopes = setOf("admin"))
        
        // Provider restricted to "admin" scope
        val provider = createMockProvider("provider1", scopes = setOf("admin"), tools = listOf(tool1, tool2))

        val scope = ChatScope(
            id = "admin",
            displayName = "Admin",
            description = "Admin scope",
            providers = listOf(provider),
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        // Both provider and tools allow "admin" scope
        assertEquals(2, allTools.size)
    }

    @Test
    fun `both provider and tool restricted to different scopes excludes tool`() {
        val tool1 = createMockToolCallback("tool1", scopes = setOf("research"))
        val tool2 = createMockToolCallback("tool2", scopes = emptySet())
        
        // Provider restricted to "admin" scope
        val provider = createMockProvider("provider1", scopes = setOf("admin"), tools = listOf(tool1, tool2))

        val scope = ChatScope(
            id = "admin",
            displayName = "Admin",
            description = "Admin scope",
            providers = listOf(provider),
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        // tool1 requires "research" scope, provider only allows "admin"
        // tool2 has no restrictions
        assertEquals(1, allTools.size)
        assertEquals("tool2", allTools[0].toolDefinition.name)
    }

    @Test
    fun `multiple providers with different restrictions`() {
        val tool1 = createMockToolCallback("tool1", scopes = emptySet())
        val tool2 = createMockToolCallback("tool2", scopes = setOf("admin"))
        
        val provider1 = createMockProvider("provider1", scopes = emptySet(), tools = listOf(tool1))
        val provider2 = createMockProvider("provider2", scopes = setOf("admin"), tools = listOf(tool2))

        val scope = ChatScope(
            id = "admin",
            displayName = "Admin",
            description = "Admin scope",
            providers = listOf(provider1, provider2),
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        // tool1 from provider1 (unrestricted provider, unrestricted tool)
        // tool2 from provider2 (admin provider, admin tool)
        assertEquals(2, allTools.size)
    }

    @Test
    fun `system messages apply same two-condition AND filtering`() {
        val msg1 = createMockSystemMessageCallback("msg1", scopes = emptySet())
        val msg2 = createMockSystemMessageCallback("msg2", scopes = setOf("admin"))
        
        val provider = createMockProvider("provider1", scopes = emptySet(), messages = listOf(msg1, msg2))

        val scope = ChatScope(
            id = "global",
            displayName = "Global",
            description = "Global scope",
            providers = listOf(provider),
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val allMessages = scope.getAllSystemMessages()
        // msg1 has no restrictions, msg2 is restricted to "admin"
        assertEquals(1, allMessages.size)
        assertEquals("msg1", allMessages[0].name)
    }

    @Test
    fun `static tools and provider tools are combined`() {
        val providerTool = createMockToolCallback("provider_tool", scopes = emptySet())
        val staticTool = createMockToolCallback("static_tool", scopes = emptySet())
        
        val provider = createMockProvider("provider1", scopes = emptySet(), tools = listOf(providerTool))

        val scope = ChatScope(
            id = "global",
            displayName = "Global",
            description = "Global scope",
            providers = listOf(provider),
            tools = listOf(staticTool),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        // Both provider tool and static tool should be included
        assertEquals(2, allTools.size)
        assertTrue(allTools.any { it.toolDefinition.name == "provider_tool" })
        assertTrue(allTools.any { it.toolDefinition.name == "static_tool" })
    }

    @Test
    fun `duplicate tools are deduplicated by name`() {
        val tool1 = createMockToolCallback("tool1", scopes = emptySet())
        
        val provider = createMockProvider("provider1", scopes = emptySet(), tools = listOf(tool1))

        val scope = ChatScope(
            id = "global",
            displayName = "Global",
            description = "Global scope",
            providers = listOf(provider),
            tools = listOf(tool1),
            systemMessages = emptyList()
        )

        val allTools = scope.getAllTools()
        // Should have only one tool despite being in both provider and static
        assertEquals(1, allTools.size)
        assertEquals("tool1", allTools[0].toolDefinition.name)
    }

    // Helper methods

    private fun createMockToolCallback(
        name: String,
        scopes: Set<String> = emptySet()
    ): ToolCallback {
        return object : ToolCallback {
            override val toolDefinition = ToolDefinition(
                name = name,
                description = "Mock tool $name",
                inputSchema = ObjectMapper().createObjectNode()
            )
            override val scopes = scopes

            override fun call(argumentsJson: String, context: Map<String, Any>): String {
                return "mock response from $name"
            }
        }
    }

    private fun createMockSystemMessageCallback(
        name: String,
        scopes: Set<String> = emptySet()
    ): SystemMessageCallback {
        return object : SystemMessageCallback {
            override val name = name
            override val scopes = scopes

            override fun call(context: SystemMessageContext): String? {
                return "Mock system message: $name"
            }
        }
    }

    private fun createMockProvider(
        id: String,
        scopes: Set<String> = emptySet(),
        tools: List<ToolCallback> = emptyList(),
        messages: List<SystemMessageCallback> = emptyList()
    ): ChatServiceProvider {
        return object : ChatServiceProvider {
            override val id = id
            override val scopes = scopes

            override fun getTools(): List<ToolCallback> = tools
            override fun getSystemMessages(): List<SystemMessageCallback> = messages
        }
    }
}
