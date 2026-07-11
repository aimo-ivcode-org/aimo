package org.ivcode.aimo.mcpclient.config

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.mcpclient.client.McpClientManager
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

@Configuration
@EnableConfigurationProperties(McpProperties::class)
class McpClientAutoConfiguration(
    private val mcpProperties: McpProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun mcpClientManager(): McpClientManager {
        val config = mcpProperties.toServerConfig()
        return McpClientManager(config, objectMapper, mcpProperties.required)
    }

    @Bean
    fun mcpChatServiceProvider(mcpClientManager: McpClientManager): ChatServiceProvider {
        return McpChatServiceProvider(mcpClientManager)
    }
}

/**
 * Registers MCP tools as a ChatServiceProvider for AIMO's tool discovery.
 */
class McpChatServiceProvider(
    private val mcpClientManager: McpClientManager,
) : ChatServiceProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        try {
            mcpClientManager.initializeAll()
            log.info("MCP ChatServiceProvider initialized")
        } catch (e: Exception) {
            log.warn("Failed to initialize MCP client manager (will continue without MCP tools): ${e.message}")
            // Don't throw - allow app to start without MCP if connection fails
        }
    }

    override val id: String = "mcp"
    override val scopes: Set<String> = emptySet()

    override fun getTools(): List<ToolCallback> {
        return mcpClientManager.getAllCallbacks()
    }

    override fun getSystemMessages(): List<SystemMessageCallback> {
        return emptyList()
    }
}
