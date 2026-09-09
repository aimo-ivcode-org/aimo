package org.ivcode.aimo.mcpclient.validation

import org.ivcode.aimo.core.chatscope.ChatScopeProvider
import org.ivcode.aimo.mcpclient.config.McpServerConfig
import org.slf4j.LoggerFactory

/**
 * Validates MCP configuration against reserved scopes, known scopes, and required transport fields.
 */
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

    /**
     * Validates a single configured MCP server.
     *
     * @param server the server definition to validate.
     */
    private fun validateServer(server: McpServerConfig.Server) {
        require(server.id.isNotBlank()) { "Server ID cannot be blank" }

        when (val transport = server.transport) {
            is McpServerConfig.Transport.StdioTransport -> {
                require(transport.command.isNotBlank()) { "Stdio command cannot be blank" }
            }

            is McpServerConfig.Transport.SseTransport -> {
                require(transport.url.isNotBlank()) { "SSE URL cannot be blank" }
            }

            is McpServerConfig.Transport.HttpTransport -> {
                require(transport.url.isNotBlank()) { "HTTP URL cannot be blank" }
            }
        }

        for (scope in server.scope) {
            require(scope != ChatScopeProvider.GLOBAL_SCOPE_ID) {
                "Server '${server.id}' cannot use reserved scope '${ChatScopeProvider.GLOBAL_SCOPE_ID}' " +
                    "(built-in global scope is always available)"
            }
            require(definedScopes.contains(scope)) {
                "Server '${server.id}' references unknown scope '$scope'"
            }
        }
    }
}
