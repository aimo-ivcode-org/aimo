package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.SystemMessage
import org.ivcode.aimo.core.chatservice.Tool
import org.ivcode.aimo.core.chatservice.ToolParam
import org.springframework.context.annotation.Configuration

/**
 * Test agent definitions for ChatScope integration testing.
 * These agents demonstrate scope restrictions and system messages.
 */

/**
 * GLOBAL SCOPE: Tools available everywhere
 * These tools have no scope restriction (empty scope array = all scopes)
 */
@ChatService
class GlobalToolsTest {

    @SystemMessage(name = "global_context")
    fun globalContext(): String = """
        You are an AI assistant with access to a variety of tools organized by scope.
        The current scope determines which tools you can use.
        Always inform the user about scope limitations if they ask for unavailable tools.
    """.trimIndent()

    @SystemMessage(name = "power_user_capabilities")
    fun powerUserCapabilities(): String = """
        POWER USER MODE - You have elevated access
        You can combine tools from multiple scopes (public, admin, research).
        Use administrative and research tools with appropriate caution.
        Always validate user intent before executing sensitive operations.
    """.trimIndent()

    @Tool(description = "Get help about available tools")
    fun getHelp(): String = """
        Available Commands:
        - help: Show this message
        - status: Check system status
        - list-scopes: List all available scopes
    """.trimIndent()

    @Tool(description = "Check current system status")
    fun getStatus(): String = """
        System Status:
        ✓ Online
        ✓ All tools operational
        ✓ No errors
    """.trimIndent()
}

/**
 * PUBLIC SCOPE: General-purpose tools available to everyone
 */
@ChatService(scope = ["public"])
class PublicToolsTest {

    @SystemMessage(name = "public_scope_intro")
    fun publicIntro(): String = """
        PUBLIC SCOPE - General Purpose Tools
        You have access to basic arithmetic and greeting tools.
        Use these for general computations and friendly interactions.
    """.trimIndent()

    @Tool(description = "Add two numbers")
    fun add(
        @ToolParam("First number") a: Int,
        @ToolParam("Second number") b: Int
    ): Int = a + b

    @Tool(description = "Multiply two numbers")
    fun multiply(
        @ToolParam("First number") a: Int,
        @ToolParam("Second number") b: Int
    ): Int = a * b

    @Tool(description = "Greet a user")
    fun greet(@ToolParam("User name") name: String): String = "Hello, $name!"
}

/**
 * ADMIN SCOPE: Administrative tools (stricter scope)
 */
@ChatService(scope = ["admin"])
class AdminToolsTest {

    @SystemMessage(name = "admin_scope_warning")
    fun adminWarning(): String = """
        ⚠️ ADMIN SCOPE - Restricted Actions
        You have access to sensitive administrative operations.
        Always confirm with the user before executing deletion or ban operations.
        Log all administrative actions performed.
    """.trimIndent()

    @Tool(description = "Delete a conversation (admin only)")
    fun deleteConversation(
        @ToolParam("Conversation ID") conversationId: String
    ): String = "✓ Deleted conversation: $conversationId (simulated)"

    @Tool(description = "Ban a user (admin only)")
    fun banUser(
        @ToolParam("User ID") userId: String,
        @ToolParam("Reason") reason: String = "No reason provided"
    ): String = "✓ Banned user: $userId (Reason: $reason) (simulated)"
}

/**
 * RESEARCH SCOPE: Research-specific tools
 */
@ChatService(scope = ["research"])
class ResearchToolsTest {

    @SystemMessage(name = "research_scope_intro")
    fun researchIntro(): String = """
        RESEARCH SCOPE - Academic and Data Analysis Tools
        You have access to paper searching and data analysis capabilities.
        Help researchers find relevant academic papers and analyze research data.
        Always provide citations and methodological recommendations.
    """.trimIndent()

    @Tool(description = "Search academic papers by topic")
    fun searchPapers(
        @ToolParam("Search query") query: String
    ): String = """
        Found 5 papers matching "$query":
        1. "A Study on ChatScopes" - 2026
        2. "Scope Management in AI" - 2025
        3. "Multi-Tenant Tool Isolation" - 2025
    """.trimIndent()

    @Tool(description = "Analyze research data")
    fun analyzeData(
        @ToolParam("Data description") description: String
    ): String = "Analysis complete for: $description (simulated)"
}

/**
 * MULTI-SCOPE SERVICE: Mixed scope availability
 */
@ChatService(scope = ["public", "admin", "research"])
class MixedToolsTest {

    @SystemMessage(name = "multi_scope_intro")
    fun multiScopeIntro(): String = """
        MULTI-SCOPE SERVICE
        This service provides scope-specific guidance for different user types.
        Help users understand what tools are available in their current scope.
    """.trimIndent()

    @Tool(description = "Get general help (available in all scopes)")
    fun publicHelp(): String = """
        General Help:
        - For calculations, use: add, multiply
        - For greetings, use: greet
    """.trimIndent()

    @Tool(scope = ["admin"])
    fun adminHelp(): String = """
        Admin Help:
        - Use deleteConversation to remove conversations
        - Use banUser to ban users
    """.trimIndent()

    @Tool(scope = ["research"])
    fun researchHelp(): String = """
        Research Help:
        - Use searchPapers to find academic papers
        - Use analyzeData to analyze research data
    """.trimIndent()
}

