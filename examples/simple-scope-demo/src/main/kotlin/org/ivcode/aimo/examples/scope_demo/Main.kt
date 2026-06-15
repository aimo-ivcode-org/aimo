package org.ivcode.aimo.examples.scope_demo

import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.Tool
import org.ivcode.aimo.core.chatservice.ToolParam
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.AimoChatClientDaoFile
import org.ivcode.aimo.core.security.AimoUserProvider
import org.ivcode.aimo.core.security.GlobalUserProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.io.File

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration
class ScopeDemoConfig {

    @Bean
    fun createAimoDao(
        @Value("\${aimo.data-dir:./data}") dataDirPath: String,
        objectMapper: ObjectMapper
    ): AimoChatClientDao {
        val dataDir = File(dataDirPath)
        return AimoChatClientDaoFile(dataDir, objectMapper)
    }

    @Bean
    fun aimoUserProvider(
        @Value("\${aimo.global-user-id:global}") globalUserId: String
    ): AimoUserProvider {
        return GlobalUserProvider(globalUserId)
    }
}

/**
 * GLOBAL SCOPE: Tools available everywhere
 * These tools have no scope restriction (empty scope array = all scopes)
 * They appear in: public, admin, research, and the global scope
 */
@ChatService
class GlobalTools {

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
        Scopes: public, admin, research (+ global)
    """.trimIndent()
}

/**
 * PUBLIC SCOPE: General-purpose tools available to everyone
 * Tools: calculator, greet
 */
@ChatService(scope = ["public"])
class PublicTools {

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
 * Tools: delete_conversation, ban_user
 */
@ChatService(scope = ["admin"])
class AdminTools {

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
 * Tools: search_papers, analyze_data
 */
@ChatService(scope = ["research"])
class ResearchTools {

    @Tool(description = "Search academic papers by topic")
    fun searchPapers(
        @ToolParam("Search query") query: String
    ): String = """
        Found 5 papers matching "$query":
        1. "A Study on ChatScopes" - 2026
        2. "Scope Management in AI" - 2025
        3. "Multi-Tenant Tool Isolation" - 2025
        4. "Annotation-Based Filtering" - 2024
        5. "Conversational AI Architecture" - 2024
    """.trimIndent()

    @Tool(description = "Analyze research data")
    fun analyzeData(
        @ToolParam("Data description") description: String
    ): String = "Analysis complete for: $description (simulated)"
}

/**
 * MULTI-SCOPE SERVICE: Mixed scope availability
 * - public_help: available to all scopes (empty scope array)
 * - admin_help: only in admin scope
 * - research_help: only in research scope
 */
@ChatService(scope = ["public", "admin", "research"])
class MixedTools {

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


