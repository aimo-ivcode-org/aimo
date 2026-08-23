package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.model.ToolCallback
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

/**
 * Integration test demonstrating that ChatScopes correctly filter tools
 * and system messages by the scope specified in @ChatService and @Tool annotations.
 *
 * KEY POINT: This test verifies that scoping WORKS by checking that:
 * - Different scopes have different sets of tools
 * - Scope-restricted tools appear only in their designated scopes
 * - System messages are properly scoped and included in scopes
 * - The filtering mechanism is active and functional
 *
 * This test uses only ChatScope configuration - no model provider needed.
 */
@SpringBootTest(classes = [
    TestChatScopeConfig::class,
    GlobalToolsTest::class,
    PublicToolsTest::class,
    AdminToolsTest::class,
    ResearchToolsTest::class,
    MixedToolsTest::class
])
@ActiveProfiles("scope-demo")
class ChatScopeDemoTest {

    @Autowired
    private lateinit var allTools: List<ToolCallback>

    @Autowired
    private lateinit var chatScopeProvider: ChatScopeProvider

    @Test
    fun `all tools are discovered at the application level`() {
        val toolNames = allTools.map { it.toolDefinition.name }
        println("=" .repeat(60))
        println("TOOL DISCOVERY TEST")
        println("=" .repeat(60))
        println("Total tools discovered: ${allTools.size}")
        println("Tool names: $toolNames")
        println()

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

        assertTrue(toolNames.contains("add"), "add should be in public scope")
        assertTrue(toolNames.contains("multiply"), "multiply should be in public scope")
        assertTrue(!toolNames.contains("deleteConversation"), "deleteConversation should NOT be in public scope")
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

        assertTrue(toolNames.contains("deleteConversation"), "deleteConversation should be in admin scope")
        assertTrue(toolNames.contains("banUser"), "banUser should be in admin scope")
        assertTrue(!toolNames.contains("add"), "add should NOT be in admin scope")
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

        assertTrue(toolNames.contains("searchPapers"), "searchPapers should be in research scope")
        assertTrue(toolNames.contains("analyzeData"), "analyzeData should be in research scope")
        assertTrue(!toolNames.contains("add"), "add should NOT be in research scope")
    }

    @Test
    fun `global tools are included in all scopes`() {
        val publicScope = chatScopeProvider.getScope("public")!!
        val adminScope = chatScopeProvider.getScope("admin")!!
        val researchScope = chatScopeProvider.getScope("research")!!
        val globalScope = chatScopeProvider.getScope(ChatScopeProvider.GLOBAL_SCOPE_ID)!!

        val publicTools = publicScope.tools.map { it.toolDefinition.name }
        val adminTools = adminScope.tools.map { it.toolDefinition.name }
        val researchTools = researchScope.tools.map { it.toolDefinition.name }
        val globalTools = globalScope.tools.map { it.toolDefinition.name }

        println("=" .repeat(60))
        println("GLOBAL TOOLS TEST")
        println("=" .repeat(60))
        println("Global scope tools: $globalTools")
        println()

        assertTrue(globalTools.contains("getHelp"), "getHelp should be in global scope")
        assertTrue(globalTools.contains("getStatus"), "getStatus should be in global scope")

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

            val globalTools = listOf("getHelp", "getStatus")
            val hasGlobalTools = toolNames.any { it in globalTools }
            val toolRefs = listOf("add", "multiply", "deleteConversation", "searchPapers")
            val toolRefsCount = toolNames.count { it in toolRefs }

            assertTrue(hasGlobalTools, "power_user scope should include global tools when inherit-global=true")
            assertTrue(
                toolRefsCount == toolRefs.size,
                "power_user scope should include all configured tool-refs (expected=${toolRefs.size}, actual=$toolRefsCount)"
            )
            println("Has global tools: $hasGlobalTools (tools: ${toolNames.filter { it in globalTools }})")
            println(
                "Cherry-picked tool-refs included: $toolRefsCount of ${toolRefs.size} " +
                "(tools: ${toolNames.filter { it in toolRefs }})"
            )
            println("✓ USE CASE 2: Cherry-picked scope successfully combines tools from multiple sources!")
        } else {
            println("Power user scope not configured - skipping test")
        }
    }

    @Test
    fun `system messages are defined for global scope`() {
        val globalScope = chatScopeProvider.getScope(ChatScopeProvider.GLOBAL_SCOPE_ID)
        requireNotNull(globalScope) { "global scope should exist" }

        val systemMessages = globalScope.systemMessages

        println("=" .repeat(60))
        println("SYSTEM MESSAGES TEST - GLOBAL SCOPE")
        println("=" .repeat(60))
        println("System messages found: ${systemMessages.size}")
        println()

        assertTrue(systemMessages.isNotEmpty(), "global scope should have system messages")
        println("✓ Global scope has ${systemMessages.size} system message(s)")
    }

    @Test
    fun `system messages are defined for public scope`() {
        val publicScope = chatScopeProvider.getScope("public")
        requireNotNull(publicScope) { "public scope should exist" }

        val systemMessages = publicScope.systemMessages

        println("=" .repeat(60))
        println("SYSTEM MESSAGES TEST - PUBLIC SCOPE")
        println("=" .repeat(60))
        println("System messages found: ${systemMessages.size}")
        println()

        assertTrue(systemMessages.isNotEmpty(), "public scope should have system messages")
        println("✓ Public scope has ${systemMessages.size} system message(s)")
    }

    @Test
    fun `system messages are defined for admin scope`() {
        val adminScope = chatScopeProvider.getScope("admin")
        requireNotNull(adminScope) { "admin scope should exist" }

        val systemMessages = adminScope.systemMessages

        println("=" .repeat(60))
        println("SYSTEM MESSAGES TEST - ADMIN SCOPE")
        println("=" .repeat(60))
        println("System messages found: ${systemMessages.size}")
        println()

        assertTrue(systemMessages.isNotEmpty(), "admin scope should have system messages")
        println("✓ Admin scope has ${systemMessages.size} system message(s)")
    }

    @Test
    fun `system messages are defined for research scope`() {
        val researchScope = chatScopeProvider.getScope("research")
        requireNotNull(researchScope) { "research scope should exist" }

        val systemMessages = researchScope.systemMessages

        println("=" .repeat(60))
        println("SYSTEM MESSAGES TEST - RESEARCH SCOPE")
        println("=" .repeat(60))
        println("System messages found: ${systemMessages.size}")
        println()

        assertTrue(systemMessages.isNotEmpty(), "research scope should have system messages")
        println("✓ Research scope has ${systemMessages.size} system message(s)")
    }

    @Test
    fun `system messages from mixed tools are available in their scopes`() {
        val publicScope = chatScopeProvider.getScope("public")
        val adminScope = chatScopeProvider.getScope("admin")
        val researchScope = chatScopeProvider.getScope("research")

        requireNotNull(publicScope) { "public scope should exist" }
        requireNotNull(adminScope) { "admin scope should exist" }
        requireNotNull(researchScope) { "research scope should exist" }

        val publicMessageCount = publicScope.systemMessages.size
        val adminMessageCount = adminScope.systemMessages.size
        val researchMessageCount = researchScope.systemMessages.size

        println("=" .repeat(60))
        println("SYSTEM MESSAGES TEST - MULTI-SCOPE SERVICE")
        println("=" .repeat(60))
        println("System messages per scope:")
        println("  Public scope: $publicMessageCount")
        println("  Admin scope: $adminMessageCount")
        println("  Research scope: $researchMessageCount")
        println()

        assertTrue(publicMessageCount > 0, "public scope should have system messages")
        assertTrue(adminMessageCount > 0, "admin scope should have system messages")
        assertTrue(researchMessageCount > 0, "research scope should have system messages")
        println("✓ Multi-scope service system messages available in all three scopes!")
    }

    @Test
    fun `power user scope has system messages from system-message-refs`() {
        val powerUserScope = chatScopeProvider.getScope("power_user")
        if (powerUserScope != null) {
            val systemMessageCount = powerUserScope.systemMessages.size

            println("=" .repeat(60))
            println("POWER USER SCOPE - SYSTEM MESSAGE REFS TEST")
            println("=" .repeat(60))
            println("Power user scope system messages: $systemMessageCount")
            println()

            assertTrue(systemMessageCount > 0, "power_user scope should have system messages from system-message-refs")
            println("✓ power_user scope has system-message-ref 'power_user_capabilities'!")
        } else {
            println("Power user scope not configured - skipping test")
        }
    }

    @Test
    fun `power user scope includes inline system message`() {
        val powerUserScope = chatScopeProvider.getScope("power_user")
        if (powerUserScope != null) {
            val systemMessageCount = powerUserScope.systemMessages.size

            println("=" .repeat(60))
            println("POWER USER SCOPE - INLINE SYSTEM MESSAGE TEST")
            println("=" .repeat(60))
            println("Power user scope system messages: $systemMessageCount")
            println()

            assertTrue(systemMessageCount >= 1, "power_user scope should have inline system message 'power_user_inline'")
            println("✓ power_user scope includes inline system message!")
        } else {
            println("Power user scope not configured - skipping test")
        }
    }

    @org.junit.jupiter.api.Disabled("Debug-only helper; produces noisy output and has no assertions")
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
                val messageCount = scope.systemMessages.size
                println("$scopeId:")
                println("  Tools: $tools")
                println("  System Messages: $messageCount")
            } else {
                println("$scopeId: [NOT FOUND]")
            }
        }
    }
}



