package org.ivcode.aimo.mcpclient.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * MCP configuration from application.yml
 */
@ConfigurationProperties(prefix = "aimo.mcp")
@Validated
data class McpProperties(
    val enabled: Boolean = true,
    val required: Boolean = true,
    val discoveryIntervalMinutes: Long = 5,
    val servers: List<ServerProperties> = emptyList(),
)

data class ServerProperties(
    val id: String,
    val transport: TransportProperties,
    val scope: List<String> = emptyList(),
)

sealed class TransportProperties(open val type: String) {
    data class StdioProperties(
        override val type: String = "stdio",
        val command: String,
        val args: List<String> = emptyList(),
    ) : TransportProperties(type)

    data class SseProperties(
        override val type: String = "sse",
        val url: String,
        val authToken: String? = null,
    ) : TransportProperties(type)
}

/**
 * Internal model for MCP server configuration.
 */
data class McpServerConfig(
    val servers: List<Server> = emptyList(),
) {
    data class Server(
        val id: String,
        val transport: Transport,
        val scope: List<String> = emptyList(),
    )

    sealed class Transport(open val type: String) {
        data class StdioTransport(
            override val type: String = "stdio",
            val command: String,
            val args: List<String> = emptyList(),
        ) : Transport(type)

        data class SseTransport(
            override val type: String = "sse",
            val url: String,
            val authToken: String? = null,
        ) : Transport(type)
    }
}

/**
 * Converts Spring properties to internal config model.
 */
fun McpProperties.toServerConfig(): McpServerConfig {
    return McpServerConfig(
        servers = servers.map { serverProps ->
            McpServerConfig.Server(
                id = serverProps.id,
                transport = when (serverProps.transport) {
                    is TransportProperties.StdioProperties -> {
                        val stdio = serverProps.transport as TransportProperties.StdioProperties
                        McpServerConfig.Transport.StdioTransport(command = stdio.command, args = stdio.args)
                    }
                    is TransportProperties.SseProperties -> {
                        val sse = serverProps.transport as TransportProperties.SseProperties
                        McpServerConfig.Transport.SseTransport(url = sse.url, authToken = sse.authToken)
                    }
                    else -> throw IllegalArgumentException("Unknown transport type: ${serverProps.transport.type}")
                },
                scope = serverProps.scope,
            )
        }
    )
}
