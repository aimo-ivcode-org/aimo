package org.ivcode.aimo.mcpclient.config

import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.ChatServiceProviderRegistry
import org.ivcode.aimo.mcpclient.client.McpClientManager
import org.ivcode.aimo.mcpclient.scheduler.DiscoveryScheduler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper

@ConditionalOnProperty(prefix = "aimo.mcp", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@Configuration
@EnableScheduling
@Import(DiscoveryScheduler::class)
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

    /**
     * Registers a ChatServiceProviderRegistry for MCP servers.
     * Each MCP server becomes its own ChatServiceProvider with its own scopes.
     */
    @Bean
    fun mcpServerChatServiceProviderRegistry(
        mcpClientManager: McpClientManager
    ): ChatServiceProviderRegistry {
        // Initialize all servers (discovery, connection, caching)
        mcpClientManager.initializeAll()
        log.info("MCP servers initialized for provider registration")

        // Return registry of all MCP server providers
        return object : ChatServiceProviderRegistry {
            override fun getProviders(): List<ChatServiceProvider> {
                val serverIds = mcpClientManager.getServerIds()
                log.debug("MCP Registry.getProviders() called: found ${serverIds.size} server(s): $serverIds")

                val providers = serverIds.map { serverId ->
                    PerServerMcpChatServiceProvider(serverId, mcpClientManager).also {
                        log.debug("Created MCP server provider: id=${it.id}, scopes=${it.scopes}")
                    }
                }
                log.debug("MCP Registry returning ${providers.size} provider(s)")
                return providers
            }
        }
    }
}


