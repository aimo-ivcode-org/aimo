package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for AnnotatedChatServiceProvider.
 *
 * Verifies that each annotated chat service is wrapped as its own provider.
 */
class AnnotatedChatServiceProviderTest {

    @Test
    fun `provider id matches bean name`() {
        val tool1 = createMockToolCallback("tool1")
        
        val entity = ChatServiceEntity(
            name = "myService",
            clazz = String::class.java,
            instance = "dummy",
            tools = listOf(tool1),
            systemMessages = emptyList()
        )

        val provider = AnnotatedChatServiceProvider(entity)
        assertEquals("myService", provider.id)
    }

    @Test
    fun `provider reports empty scopes global unrestricted`() {
        val entity = ChatServiceEntity(
            name = "service1",
            clazz = String::class.java,
            instance = "dummy",
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val provider = AnnotatedChatServiceProvider(entity)
        assertTrue(provider.scopes.isEmpty(), "Provider should have no scope restrictions")
    }

    @Test
    fun `provider exposes tools from entity`() {
        val tool1 = createMockToolCallback("tool1")
        val tool2 = createMockToolCallback("tool2")

        val entity = ChatServiceEntity(
            name = "service1",
            clazz = String::class.java,
            instance = "dummy",
            tools = listOf(tool1, tool2),
            systemMessages = emptyList()
        )

        val provider = AnnotatedChatServiceProvider(entity)
        val tools = provider.getTools()

        assertEquals(2, tools.size)
        assertTrue(tools.any { it.toolDefinition.name == "tool1" })
        assertTrue(tools.any { it.toolDefinition.name == "tool2" })
    }

    @Test
    fun `provider exposes system messages from entity`() {
        val msg1 = createMockSystemMessageCallback("msg1")
        val msg2 = createMockSystemMessageCallback("msg2")

        val entity = ChatServiceEntity(
            name = "service1",
            clazz = String::class.java,
            instance = "dummy",
            tools = emptyList(),
            systemMessages = listOf(msg1, msg2)
        )

        val provider = AnnotatedChatServiceProvider(entity)
        val messages = provider.getSystemMessages()

        assertEquals(2, messages.size)
        assertTrue(messages.any { it.name == "msg1" })
        assertTrue(messages.any { it.name == "msg2" })
    }

    @Test
    fun `provider returns empty lists when entity has no callbacks`() {
        val entity = ChatServiceEntity(
            name = "service1",
            clazz = String::class.java,
            instance = "dummy",
            tools = emptyList(),
            systemMessages = emptyList()
        )

        val provider = AnnotatedChatServiceProvider(entity)

        assertTrue(provider.getTools().isEmpty())
        assertTrue(provider.getSystemMessages().isEmpty())
    }

    @Test
    fun `provider maintains tool scopes from entity`() {
        val tool1 = createMockToolCallback("tool1", scopes = setOf("admin"))
        val tool2 = createMockToolCallback("tool2", scopes = emptySet())

        val entity = ChatServiceEntity(
            name = "service1",
            clazz = String::class.java,
            instance = "dummy",
            tools = listOf(tool1, tool2),
            systemMessages = emptyList()
        )

        val provider = AnnotatedChatServiceProvider(entity)
        val tools = provider.getTools()

        val adminTool = tools.find { it.toolDefinition.name == "tool1" }
        val globalTool = tools.find { it.toolDefinition.name == "tool2" }

        assertEquals(setOf("admin"), adminTool?.scopes)
        assertEquals(emptySet(), globalTool?.scopes)
    }

    @Test
    fun `provider maintains system message scopes from entity`() {
        val msg1 = createMockSystemMessageCallback("msg1", scopes = setOf("research"))
        val msg2 = createMockSystemMessageCallback("msg2", scopes = emptySet())

        val entity = ChatServiceEntity(
            name = "service1",
            clazz = String::class.java,
            instance = "dummy",
            tools = emptyList(),
            systemMessages = listOf(msg1, msg2)
        )

        val provider = AnnotatedChatServiceProvider(entity)
        val messages = provider.getSystemMessages()

        val researchMsg = messages.find { it.name == "msg1" }
        val globalMsg = messages.find { it.name == "msg2" }

        assertEquals(setOf("research"), researchMsg?.scopes)
        assertEquals(emptySet(), globalMsg?.scopes)
    }

    // Helper methods

    private fun createMockToolCallback(
        toolName: String,
        scopes: Set<String> = emptySet()
    ): ToolCallback {
        return object : ToolCallback {
            override val toolDefinition = ToolDefinition(
                name = toolName,
                description = "Mock tool $toolName",
                inputSchema = ObjectMapper().createObjectNode()
            )
            override val scopes = scopes

            override fun call(argumentsJson: String, context: Map<String, Any>): String {
                return "mock response from $toolName"
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
}
