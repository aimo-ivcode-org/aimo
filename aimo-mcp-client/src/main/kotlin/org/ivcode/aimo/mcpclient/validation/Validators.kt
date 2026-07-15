package org.ivcode.aimo.mcpclient.validation

import org.ivcode.aimo.mcpclient.config.McpServerConfig
import org.slf4j.LoggerFactory

class ConfigurationValidator(private val definedScopes: Set<String>) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun validate(config: McpServerConfig) {
        val duplicateIds = config.servers
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Duplicate MCP server ids are not allowed: ${duplicateIds.joinToString(", ")}"
        }

        for (server in config.servers) {
            validateServer(server)
        }
        log.info("MCP configuration validation passed")
    }

    private fun validateServer(server: McpServerConfig.Server) {
        if (server.id.isBlank()) throw IllegalArgumentException("Server ID cannot be blank")
        
        when (server.transport) {
            is McpServerConfig.Transport.StdioTransport -> {
                val stdio = server.transport as McpServerConfig.Transport.StdioTransport
                if (stdio.command.isBlank()) throw IllegalArgumentException("Stdio command cannot be blank")
            }
            is McpServerConfig.Transport.SseTransport -> {
                val sse = server.transport as McpServerConfig.Transport.SseTransport
                if (sse.url.isBlank()) throw IllegalArgumentException("SSE URL cannot be blank")
            }
            is McpServerConfig.Transport.HttpTransport -> {
                val http = server.transport as McpServerConfig.Transport.HttpTransport
                if (http.url.isBlank()) throw IllegalArgumentException("HTTP URL cannot be blank")
            }
        }

        for (scope in server.scope) {
            if (!definedScopes.contains(scope)) {
                throw IllegalArgumentException("Server '${server.id}' references unknown scope '$scope'")
            }
        }
    }
}

class ToolRefValidator(
    private val annotatedToolNames: Set<String>,
    private val mcpToolNames: Set<String>,
) {
    fun validateToolRefs(scope: String, toolRefs: List<String>) {
        val allToolNames = annotatedToolNames + mcpToolNames
        for (toolRef in toolRefs) {
            if (!allToolNames.contains(toolRef)) {
                throw IllegalArgumentException("Scope '$scope' references unknown tool '$toolRef'")
            }
        }
    }
}
