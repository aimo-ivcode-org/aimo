package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.SystemMessage
import org.ivcode.aimo.core.chatservice.Tool
import org.ivcode.aimo.core.chatservice.ToolParam
import org.ivcode.aimo.core.chatservice.toToolCallbacks
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import org.ivcode.aimo.core.properties.AimoChatScopeProperties
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests ChatScope creation and filtering via YAML configuration.
 *
 * Verifies that the scope properties work correctly with tool-refs and
 * system-message-refs when integrated with the actual scope builder in AimoConfig.
 */
class ChatScopeYamlDefinitionTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `scope properties accept tool references`() {
        val config = AimoChatScopeProperties(
            displayName = "Research",
            description = "Research tools only",
            toolRefs = listOf("searchPapers", "analyzeData"),
            systemMessageRefs = emptyList(),
            systemMessages = emptyMap()
        )

        assertEquals("Research", config.displayName)
        assertEquals(listOf("searchPapers", "analyzeData"), config.toolRefs)
    }

    @Test
    fun `scope properties accept system message references`() {
        val config = AimoChatScopeProperties(
            displayName = "Admin",
            description = "Admin only",
            toolRefs = emptyList(),
            systemMessageRefs = listOf("admin_rules", "security_prompt"),
            systemMessages = emptyMap()
        )

        assertEquals("Admin", config.displayName)
        assertEquals(listOf("admin_rules", "security_prompt"), config.systemMessageRefs)
    }

    @Test
    fun `scope properties accept inline system messages`() {
        val config = AimoChatScopeProperties(
            displayName = "Custom",
            description = "Custom scope",
            toolRefs = emptyList(),
            systemMessageRefs = emptyList(),
            systemMessages = mapOf(
                "intro" to "Welcome to this scope",
                "rules" to "Follow these rules"
            )
        )

        assertEquals(2, config.systemMessages.size)
        assertTrue(config.systemMessages.containsKey("intro"))
        assertTrue(config.systemMessages["rules"]?.contains("rules") == true)
    }

    @Test
    fun `scope properties can combine tool refs and system message refs`() {
        val config = AimoChatScopeProperties(
            displayName = "Combined",
            description = "Combined usage",
            toolRefs = listOf("search", "analyze"),
            systemMessageRefs = listOf("custom_prompt"),
            systemMessages = mapOf("header" to "Header text")
        )

        assertEquals(2, config.toolRefs.size)
        assertEquals(1, config.systemMessageRefs.size)
        assertEquals(1, config.systemMessages.size)
    }

    @Test
    fun `tools can be filtered by annotation scope and YAML reference`() {
        // Tools with and without scopes
        val service = FilteredToolService()
        val parentServiceScopes = setOf("research", "admin")
        val scopedTools = toToolCallbacks(service, objectMapper, parentServiceScopes)

        // Tool "research_search" has @Tool(scope=["research"])
        // Tool "general_help" has no scope annotation
        assertEquals(2, scopedTools.size)

        val researchTool = scopedTools.find { it.toolDefinition.name == "research_search" }
        val generalTool = scopedTools.find { it.toolDefinition.name == "general_help" }

        // research_tool scopes from annotation
        assertEquals(setOf("research"), researchTool!!.scopes, "Research tool should have declared scope")
        // general_tool has no explicit scope, so it inherits parent scopes
        assertEquals(
            setOf("research", "admin"),
            generalTool!!.scopes,
            "General tool without scope inherits parent scopes"
        )
    }

    @Test
    fun `system messages can be filtered by annotation scope`() {
        val service = FilteredMessageService()
        val parentServiceScopes = setOf("admin", "research")
        val scopedMessages = toSystemMessageCallbacks(service, parentServiceScopes)

        assertEquals(2, scopedMessages.size)

        // Messages with different scopes
        val adminMsg = scopedMessages.find { it.name == "admin_prompt" }
        val generalMsg = scopedMessages.find { it.name == "general_message" }

        // admin_prompt has explicit @SystemMessage(scope=["admin"])
        assertEquals(setOf("admin"), adminMsg!!.scopes)
        // general_message has no explicit scope, so inherits parent scopes
        assertEquals(setOf("admin", "research"), generalMsg!!.scopes)
    }

    @Test
    fun `tool names are discovered correctly from annotations`() {
        val service = TestToolService()
        val tools = toToolCallbacks(service, objectMapper, emptySet())

        val toolNames = tools.map { it.toolDefinition.name }
        assertTrue(toolNames.contains("searchPapers"), "searchPapers tool should be discovered")
        assertTrue(toolNames.contains("analyzeData"), "analyzeData tool should be discovered")
    }

     @Test
     fun `system message names are discovered correctly from annotations`() {
         val service = TestMessageService()
         val messages = toSystemMessageCallbacks(service, emptySet())

         val messageNames = messages.map { it.name }
         assertTrue(messageNames.contains("admin_prompt"), "admin_prompt message should be discovered")
         assertTrue(messageNames.contains("research_insight"), "research_insight message should be discovered")
     }

    @Test
    fun `scope properties can store and retrieve all reference types`() {
        val config = AimoChatScopeProperties(
            displayName = "Complete Scope",
            description = "Complete scope with multiple items",
            toolRefs = listOf("tool1", "tool2", "tool3"),
            systemMessageRefs = listOf("msg1", "msg2"),
            systemMessages = mapOf(
                "custom1" to "Custom message 1",
                "custom2" to "Custom message 2"
            )
        )

        assertEquals(3, config.toolRefs.size)
        assertEquals(2, config.systemMessageRefs.size)
        assertEquals(2, config.systemMessages.size)
    }

    // Test classes

    @ChatService(scope = ["research", "admin"])
    private class FilteredToolService {
        @Tool(name = "research_search", description = "Search papers", scope = ["research"])
        @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
        fun searchPapers(@ToolParam("Query") query: String): String = "Papers"

        @Tool(name = "general_help", description = "General help")
        @Suppress("FunctionOnlyReturningConstant")
        fun getHelp(): String = "Help"
    }

    @ChatService(scope = ["admin", "research"])
    private class FilteredMessageService {
        @SystemMessage(name = "admin_prompt", scope = ["admin"])
        @Suppress("FunctionOnlyReturningConstant")
        fun adminRules(): String = "Admin rules"

        @SystemMessage(name = "general_message")
        @Suppress("FunctionOnlyReturningConstant")
        fun generalMessage(): String = "General message"
    }

    @ChatService
    private class TestToolService {
        @Tool(name = "searchPapers", description = "Search papers")
        @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
        fun searchPapers(@ToolParam("Query") query: String): String = "Papers"

        @Tool(name = "analyzeData", description = "Analyze data")
        @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
        fun analyzeData(@ToolParam("Data") data: String): String = "Analysis"
    }

    @ChatService
    private class TestMessageService {
        @SystemMessage(name = "admin_prompt")
        @Suppress("FunctionOnlyReturningConstant")
        fun adminPrompt(): String = "Admin prompt"

        @SystemMessage(name = "research_insight")
        @Suppress("FunctionOnlyReturningConstant")
        fun researchInsight(): String = "Research insight"
    }
}



