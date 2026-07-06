package org.ivcode.aimo.mcpclient.scheduler

import org.ivcode.aimo.mcpclient.config.McpToolRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

@Service
@EnableScheduling
class DiscoveryScheduler(
    private val mcpToolRegistry: McpToolRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${aimo.mcp.discovery-interval-minutes:5}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    fun refreshTools() {
        try {
            log.debug("Running periodic MCP discovery refresh")
            mcpToolRegistry.refresh()
            log.debug("Periodic MCP refresh completed")
        } catch (e: Exception) {
            log.warn("Periodic MCP refresh failed", e)
        }
    }
}
