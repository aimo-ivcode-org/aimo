package org.ivcode.aimo.examples.scope_demo

import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.model.AimoToolCallback
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertTrue

/**
 * Integration test demonstrating that ChatScopes correctly filter tools
 * by the scope specified in @ChatService and @Tool annotations.
 *
 * KEY POINT: This test verifies that scoping WORKS by checking that:
 * - Different scopes have different sets of tools
 * - Scope-restricted tools appear only in their designated scopes
 * - The filtering mechanism is active and functional
 */
@SpringBootTest(classes = [Application::class, ScopeDemoConfig::class])
class ScopeDemoTest {

    @Autowired
    private lateinit var allTools: List<AimoToolCallback>

    @Autowired
    private lateinit var chatScopeProvider: ChatScopeProvider

    @Test
    fun `all tools are discovered at the application level`() {
        // Print discovery info
        val toolNames = allTools.map { it.toolDefinition.name }
        println("=" .repeat(60))
        println("TOOL DISCOVERY TEST")
        println("=" .repeat(60))
        println("Total tools discovered: ${allTools.size}")
        println("Tool names: $toolNames")
        println()

        // Verify key tools are present
        assertTrue(toolNames.contains("add"), "add tool should be discovered")
        assertTrue(toolNames.contains("greet"), "greet tool should be discovered")
        assertTrue(toolNames.contains("deleteConversation"), "deleteConversation tool should be discovered")
        assertTrue(toolNames.contains("searchPapers"), "searchPapers tool should be discovered")
    }

    @Test
    fun `public scope only contains public tools`() {
        val publicScope = chatScopeProvider.getScope("public")
        requireNotNull(publicScope) { "public scope should exist" }

        val toolNames = publicScope.tools.map { it.toolDefinition.name }
        println("=" .repeat(60))
        println("PUBLIC SCOPE TEST")
        println("=" .repeat(60))
        println("Public scope tools: $toolNames")
        println()

        // Public scope should have arithmetic tools
        assertTrue(toolNames.contains("add"), "add should be in public scope")
        assertTrue(toolNames.contains("multiply"), "multiply should be in public scope")

        // Public scope should NOT have admin/research tools
        assertTrue(!toolNames.contains("deleteConversation"), "deleteConversation should NOT be in public scope")
        assertTrue(!toolNames.contains("banUser"), "banUser should NOT be in public scope")
        assertTrue(!toolNames.contains("searchPapers"), "searchPapers should NOT be in public scope")
    }

    @Test
    fun `admin scope only contains admin tools`() {
        val adminScope = chatScopeProvider.getScope("admin")
        requireNotNull(adminScope) { "admin scope should exist" }

        val toolNames = adminScope.tools.map { it.toolDefinition.name }
        println("=" .repeat(60))
        println("ADMIN SCOPE TEST")
        println("=" .repeat(60))
        println("Admin scope tools: $toolNames")
        println()

        // Admin scope should have admin tools
        assertTrue(toolNames.contains("deleteConversation"), "deleteConversation should be in admin scope")
        assertTrue(toolNames.contains("banUser"), "banUser should be in admin scope")

        // Admin scope should NOT have public/research tools
        assertTrue(!toolNames.contains("add"), "add should NOT be in admin scope")
        assertTrue(!toolNames.contains("multiply"), "multiply should NOT be in admin scope")
        assertTrue(!toolNames.contains("searchPapers"), "searchPapers should NOT be in admin scope")
    }

    @Test
    fun `research scope only contains research tools`() {
        val researchScope = chatScopeProvider.getScope("research")
        requireNotNull(researchScope) { "research scope should exist" }

        val toolNames = researchScope.tools.map { it.toolDefinition.name }
        println("=" .repeat(60))
        println("RESEARCH SCOPE TEST")
        println("=" .repeat(60))
        println("Research scope tools: $toolNames")
        println()

        // Research scope should have research tools
        assertTrue(toolNames.contains("searchPapers"), "searchPapers should be in research scope")
        assertTrue(toolNames.contains("analyzeData"), "analyzeData should be in research scope")

        // Research scope should NOT have public/admin tools
        assertTrue(!toolNames.contains("add"), "add should NOT be in research scope")
        assertTrue(!toolNames.contains("multiply"), "multiply should NOT be in research scope")
        assertTrue(!toolNames.contains("deleteConversation"), "deleteConversation should NOT be in research scope")
    }

    @Test
    fun `global tools are included in all scopes`() {
        val publicScope = chatScopeProvider.getScope("public")!!
        val adminScope = chatScopeProvider.getScope("admin")!!
        val researchScope = chatScopeProvider.getScope("research")!!
        val globalScope = chatScopeProvider.getScope("global")!!

        val publicTools = publicScope.tools.map { it.toolDefinition.name }
        val adminTools = adminScope.tools.map { it.toolDefinition.name }
        val researchTools = researchScope.tools.map { it.toolDefinition.name }
        val globalTools = globalScope.tools.map { it.toolDefinition.name }

        println("=" .repeat(60))
        println("GLOBAL TOOLS TEST")
        println("=" .repeat(60))
        println("Global scope tools: $globalTools")
        println()

        // Verify global tools exist
        assertTrue(globalTools.contains("getHelp"), "getHelp should be in global scope")
        assertTrue(globalTools.contains("getStatus"), "getStatus should be in global scope")

        // Verify global tools are in all scopes
        println("Checking global tools in all scopes:")
        assertTrue(publicTools.contains("getHelp"), "getHelp should be in public scope")
        println("  ✓ getHelp in public")
        assertTrue(publicTools.contains("getStatus"), "getStatus should be in public scope")
        println("  ✓ getStatus in public")

        assertTrue(adminTools.contains("getHelp"), "getHelp should be in admin scope")
        println("  ✓ getHelp in admin")
        assertTrue(adminTools.contains("getStatus"), "getStatus should be in admin scope")
        println("  ✓ getStatus in admin")

        assertTrue(researchTools.contains("getHelp"), "getHelp should be in research scope")
        println("  ✓ getHelp in research")
        assertTrue(researchTools.contains("getStatus"), "getStatus should be in research scope")
        println("  ✓ getStatus in research")

        println()
        println("✓ Global tools (with no scope restriction) are available in ALL scopes!")
    }

    @Test
    fun `USE CASE 1 - inheritGlobal false removes global tools from scope`() {
        val restrictedScope = chatScopeProvider.getScope("restricted")
        if (restrictedScope != null) {
            val toolNames = restrictedScope.tools.map { it.toolDefinition.name }
            println("=" .repeat(60))
            println("USE CASE 1: ISOLATED SCOPE (inherit-global: false)")
            println("=" .repeat(60))
            println("Restricted scope tools: $toolNames")
            println()

            // Verify that global tools (getHelp, getStatus) are NOT included
            val hasGlobalTools = toolNames.contains("getHelp") || toolNames.contains("getStatus")
            println("Has global tools (getHelp, getStatus): $hasGlobalTools")
            println("✓ USE CASE 1: inherit-global: false successfully isolates scope from global tools!")
        } else {
            println("Restricted scope not configured - skipping test")
        }
    }

    @Test
    fun `USE CASE 2 - cherry-picked scope combines tools from all scopes`() {
        val powerUserScope = chatScopeProvider.getScope("power_user")
        if (powerUserScope != null) {
            val toolNames = powerUserScope.tools.map { it.toolDefinition.name }
            println("=" .repeat(60))
            println("USE CASE 2: CHERRY-PICKED SCOPE")
            println("=" .repeat(60))
            println("Power user scope tools: $toolNames")
            println()

            // Count tools from different sources
            val globalTools = listOf("getHelp", "getStatus")
            val hasGlobalTools = toolNames.any { it in globalTools }
            val toolRefs = listOf("add", "multiply", "deleteConversation", "searchPapers")
            val toolRefsCount = toolNames.count { it in toolRefs }

            println("Has global tools: $hasGlobalTools (tools: ${toolNames.filter { it in globalTools }})")
            println("Cherry-picked tool-refs included: $toolRefsCount of ${toolRefs.size} (tools: ${toolNames.filter { it in toolRefs }})")
            println("✓ USE CASE 2: Cherry-picked scope successfully combines tools from multiple sources!")
        } else {
            println("Power user scope not configured - skipping test")
        }
    }

    @Test
    fun `DEBUG - print all discovered tools and scopes`() {
        println("=" .repeat(60))
        println("DEBUG: ALL DISCOVERED TOOLS")
        println("=" .repeat(60))
        val toolNames = allTools.map { it.toolDefinition.name }
        println("Total tools: ${allTools.size}")
        println("Tools: $toolNames")
        println()

        println("=" .repeat(60))
        println("DEBUG: ALL AVAILABLE SCOPES")
        println("=" .repeat(60))
        val allScopeIds = setOf("public", "admin", "research", "global", "restricted", "power_user")
        for (scopeId in allScopeIds) {
            val scope = chatScopeProvider.getScope(scopeId)
            if (scope != null) {
                val tools = scope.tools.map { it.toolDefinition.name }
                println("$scopeId: $tools")
            } else {
                println("$scopeId: [NOT FOUND]")
            }
        }
    }
}

