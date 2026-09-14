package org.ivcode.aimo.mcpclient.scheduler

import org.ivcode.aimo.mcpclient.client.McpClientManager
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
@ConditionalOnProperty(prefix = "aimo.mcp", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("\${aimo.mcp.discovery-interval-minutes:5} > 0")
class DiscoveryScheduler(
    private val mcpClientManager: McpClientManager
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
        if (mcpClientManager.serverIds.isEmpty()) return
        runCatching { mcpClientManager.refresh() }
            .onSuccess { log.debug("MCP refresh completed") }
            .onFailure { log.warn("MCP refresh failed", it) }
    }
}
