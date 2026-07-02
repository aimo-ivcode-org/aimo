package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatService
 import org.ivcode.aimo.core.chatservice.SystemMessage
import org.ivcode.aimo.core.chatservice.Tool
import org.ivcode.aimo.core.chatservice.ToolParam
import org.ivcode.aimo.core.chatservice.toToolCallbacks
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests ChatScope discovery from @ChatService, @Tool, and @SystemMessage annotations.
 *
 * Verifies that scopes are correctly extracted from annotations and used to filter
 * tools and system messages at configuration time.
 */
class ChatScopeAnnotationDiscoveryTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `tools without explicit scope inherit parent scopes or are unrestricted`() {
        // When parent has no scopes, tool with no explicit scope is available everywhere
        val service = GeneralService()
        val parentServiceScopes = emptySet<String>()

        val scopedTools = toToolCallbacks(service, objectMapper, parentServiceScopes)

        assertEquals(1, scopedTools.size)
        val globalTool = scopedTools[0]
        assertEquals("getHelp", globalTool.toolDefinition.name)
        // Parent has no scopes, and tool has no explicit scope -> tool gets empty scope set
        assertTrue(globalTool.scopes.isEmpty(), "Tool without scope in unrestricted parent should be available everywhere")
    }

    @Test
    fun `tools with scope are restricted to specified scopes`() {
        val service = ResearchService()
        val parentServiceScopes = setOf("research", "admin")

        val scopedTools = toToolCallbacks(service, objectMapper, parentServiceScopes)

        assertEquals(1, scopedTools.size)
        val scopedTool = scopedTools[0]
        assertEquals("searchPapers", scopedTool.toolDefinition.name)
        assertEquals(setOf("research"), scopedTool.scopes, "Tool scoped to [research] should match parent scopes")
    }

     @Test
     fun `system messages without scope are available to all scopes`() {
         val service = GeneralService()
         val parentServiceScopes = emptySet<String>()

         val scopedMessages = toSystemMessageCallbacks(service, parentServiceScopes)

         assertEquals(1, scopedMessages.size)
         val globalMessage = scopedMessages[0]
         assertEquals("generalPrompt", globalMessage.name, "Auto-generated name uses method name as-is")
         assertTrue(globalMessage.scopes.isEmpty(), "System message without scope should have empty scope set")
     }

     @Test
     fun `system messages with scope are restricted to specified scopes`() {
         val service = AdminService()
         val parentServiceScopes = setOf("admin")

         val scopedMessages = toSystemMessageCallbacks(service, parentServiceScopes)

         assertEquals(1, scopedMessages.size)
         val adminMessage = scopedMessages[0]
         assertEquals("admin_rules", adminMessage.name)
         assertEquals(setOf("admin"), adminMessage.scopes)
     }

    @Test
    fun `parent service scope validates tool scope containment`() {
        // Parent service is restricted to ["research"] but tool tries to use ["admin"]
        val service = MisConfiguredService()
        val parentServiceScopes = setOf("research")

        val exception = assertFailsWith<IllegalArgumentException> {
            toToolCallbacks(service, objectMapper, parentServiceScopes)
        }

        assertTrue(
            exception.message?.contains("has scopes not in parent service") == true,
            "Should validate that tool scopes are subset of parent service scopes: ${exception.message}"
        )
    }

     @Test
     fun `system message names are auto-generated from method name`() {
         val service = GeneralService()
         val parentServiceScopes = emptySet<String>()

         val scopedMessages = toSystemMessageCallbacks(service, parentServiceScopes)

         // generalPrompt method should get name "generalPrompt" (method name as-is)
         val message = scopedMessages.find { it.name == "generalPrompt" }
         assertTrue(message != null, "System message should have auto-generated name from method name")
     }

     @Test
     fun `explicit system message name is used over auto-generated`() {
         val service = AdminService()
         val parentServiceScopes = setOf("admin")

         val scopedMessages = toSystemMessageCallbacks(service, parentServiceScopes)

         // adminRulesMethod has explicit @SystemMessage(name = "admin_rules")
         val message = scopedMessages.find { it.name == "admin_rules" }
         assertTrue(message != null, "System message should use explicit name from annotation")
     }

    @Test
    fun `multiple scoped tools in same service are all discovered`() {
        val service = MultiToolService()
        val parentServiceScopes = setOf("research", "admin")

        val scopedTools = toToolCallbacks(service, objectMapper, parentServiceScopes)

        assertEquals(3, scopedTools.size)
        val toolNames = scopedTools.map { it.toolDefinition.name }.toSet()
        assertEquals(setOf("searchPapers", "analyzeData", "getHelp"), toolNames)
    }

    @Test
    fun `tool scope can be empty meaning available everywhere`() {
        val service = MultiToolService()
        val parentServiceScopes = setOf("research", "admin")

        val scopedTools = toToolCallbacks(service, objectMapper, parentServiceScopes)

        val helpTool = scopedTools.find { it.toolDefinition.name == "getHelp" }
        assertTrue(helpTool != null, "getHelp tool should exist")
        // Tool with no scope annotation inherits parent service scopes
        assertEquals(setOf("research", "admin"), helpTool!!.scopes, "Tool without scope annotation inherits parent scopes")
    }

    @Test
    fun `service without scope makes tools available everywhere`() {
        val service = GeneralService()
        // No scope on @ChatService - empty list means available to all
        val parentServiceScopes = emptySet<String>()

        val scopedTools = toToolCallbacks(service, objectMapper, parentServiceScopes)

        assertEquals(1, scopedTools.size)
        assertEquals(setOf(), scopedTools[0].scopes)
    }

    @Test
    fun `empty parent service scope allows any tool scope declaration`() {
        val service = UnscopedService()
        val parentServiceScopes = emptySet<String>()

        val scopedTools = toToolCallbacks(service, objectMapper, parentServiceScopes)

        assertEquals(1, scopedTools.size)
        // Tool declares scope=["admin"] but parent has no scopes, so tool gets its declared scope as-is
        assertEquals(setOf("admin"), scopedTools[0].scopes)
    }

    // Test classes

    @ChatService  // No scope = available everywhere
    private class GeneralService {
        @Tool(name = "getHelp", description = "Get general help")
        fun getHelp(): String = "How can I help?"

        @SystemMessage
        fun generalPrompt(): String = "You are a helpful assistant."
    }

    @ChatService(scope = ["research", "admin"])  // Scoped service
    private class ResearchService {
        @Tool(name = "searchPapers", description = "Search research papers", scope = ["research"])
        fun searchPapers(@ToolParam("Query") query: String): String = "Found papers matching: $query"

        @SystemMessage(scope = ["research"])
        fun researchPrompt(): String = "Focus on academic sources."
    }

    @ChatService(scope = ["admin"])
    private class AdminService {
        @Tool(name = "viewLogs", description = "View system logs")
        fun viewLogs(): String = "System logs..."

        @SystemMessage(name = "admin_rules", scope = ["admin"])
        fun adminRulesMethod(): String = "Admin rules apply."
    }

    @ChatService(scope = ["research", "admin"])
    private class MultiToolService {
        @Tool(name = "searchPapers", description = "Search", scope = ["research"])
        fun searchPapers(@ToolParam("Query") query: String): String = "Papers"

        @Tool(name = "analyzeData", description = "Analyze", scope = ["admin"])
        fun analyzeData(@ToolParam("Data") data: String): String = "Analysis"

        @Tool(name = "getHelp", description = "Help")
        fun getHelp(): String = "Help"
    }

    @ChatService(scope = ["research"])
    private class MisConfiguredService {
        // This tries to restrict to ["admin"] but parent service is ["research"]
        @Tool(name = "forbidden", description = "Should fail", scope = ["admin"])
        fun forbidden(): String = "This should not work"
    }

    @ChatService  // No scope annotation
    private class UnscopedService {
        @Tool(name = "scopedTool", description = "Tool with scope", scope = ["admin"])
        fun scopedTool(): String = "Allowed because parent has no scope restrictions"
    }
}








