package org.ivcode.aimo.mcpclient.config

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.mcpclient.client.McpClientManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

@ConditionalOnProperty(prefix = "aimo.mcp", name = ["enabled"], havingValue = "true", matchIfMissing = true)
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
        mcpClientManager.initializeAll()
        log.info("MCP ChatServiceProvider initialized")
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
