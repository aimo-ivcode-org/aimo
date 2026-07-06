package org.ivcode.aimo.mcpclient.controller

import org.ivcode.aimo.mcpclient.config.McpToolRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.slf4j.LoggerFactory

/**
 * Admin endpoint for MCP server refresh and management.
 */
@RestController
@RequestMapping("/aimo-api/admin/mcp-servers")
@ConditionalOnBean(McpToolRegistry::class)
class McpAdminController(
    private val mcpToolRegistry: McpToolRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/refresh")
    fun refresh(): ResponseEntity<Map<String, Any>> {
        return try {
            mcpToolRegistry.refresh()
            log.info("MCP servers refreshed")
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "MCP servers refreshed successfully"
            ))
        } catch (e: Exception) {
            log.error("MCP refresh failed", e)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to refresh MCP servers: ${e.message}"
            ))
        }
    }
}
