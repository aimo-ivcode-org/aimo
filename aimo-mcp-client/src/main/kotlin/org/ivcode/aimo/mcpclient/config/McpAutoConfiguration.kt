package org.ivcode.aimo.mcpclient.config

import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.mcpclient.client.McpClientManager
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
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
    fun mcpToolRegistry(mcpClientManager: McpClientManager): McpToolRegistry {
        return McpToolRegistry(mcpClientManager)
    }
}

/**
 * Registry that provides MCP tools to the AIMO tool pipeline.
 */
@Component
class McpToolRegistry(
    private val mcpClientManager: McpClientManager,
) : AimoToolRegistry {
    private val log = LoggerFactory.getLogger(javaClass)
    private var initialized = false

    init {
        try {
            mcpClientManager.initializeAll()
            initialized = true
            log.info("MCP tool registry initialized")
        } catch (e: Exception) {
            log.error("Failed to initialize MCP client manager", e)
            throw e
        }
    }

    override fun getTools(): List<ToolCallback> {
        return if (initialized) {
            mcpClientManager.getAllCallbacks()
        } else {
            emptyList()
        }
    }

    override fun getToolsByScope(scope: String): List<ToolCallback> {
        return mcpClientManager.getAllCallbacks().filter { it.scopes.isEmpty() || it.scopes.contains(scope) }
    }

    fun refresh() {
        mcpClientManager.refresh()
    }
}

/**
 * Interface that tool registries must implement.
 */
interface AimoToolRegistry {
    fun getTools(): List<ToolCallback>
    fun getToolsByScope(scope: String): List<ToolCallback>
}
