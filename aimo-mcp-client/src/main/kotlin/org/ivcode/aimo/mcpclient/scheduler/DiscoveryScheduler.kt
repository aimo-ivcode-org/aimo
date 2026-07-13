package org.ivcode.aimo.mcpclient.scheduler

import org.ivcode.aimo.mcpclient.client.McpClientManager
import org.ivcode.aimo.mcpclient.config.McpProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
@ConditionalOnProperty(prefix = "aimo.mcp", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DiscoveryScheduler(
    private val mcpClientManager: McpClientManager,
    private val mcpProperties: McpProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Periodically refresh MCP server tool discovery and retry failed servers.
     *
     * Configuration via aimo.mcp.discovery-interval-minutes:
     * - Positive value (e.g., 5): refresh every N minutes
     * - Zero (0): disable automatic refresh/retries
     * - Negative value (e.g., -1): disable automatic refresh/retries
     *
     * Default: 5 minutes when not specified
     */
    @Scheduled(
        fixedDelayString = "#{T(java.lang.Math).max(1, \${aimo.mcp.discovery-interval-minutes:5})}",
        timeUnit = TimeUnit.MINUTES,
        initialDelayString = "1"
    )
    fun refreshTools() {
        // Disable refresh when discovery-interval-minutes <= 0
        if (mcpProperties.discoveryIntervalMinutes <= 0) {
            return
        }

        try {
            log.debug("Running MCP server refresh (discovery/retry)")
            mcpClientManager.refresh()
            log.debug("MCP refresh completed")
        } catch (e: Exception) {
            log.warn("MCP refresh failed", e)
        }
    }
}
