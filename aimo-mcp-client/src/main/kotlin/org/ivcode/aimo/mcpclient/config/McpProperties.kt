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
    val transport: TransportConfig,
    val scope: List<String> = emptyList(),
)

// Non-sealed intermediate class for YAML binding
data class TransportConfig(
    val type: String,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val url: String? = null,
    val authToken: String? = null,
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

    data class HttpProperties(
        override val type: String = "http",
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

        data class HttpTransport(
            override val type: String = "http",
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
            require(serverProps.id.isNotBlank()) { "MCP server id cannot be blank" }
            McpServerConfig.Server(
                id = serverProps.id,
                transport = serverProps.transport.toTransportProperties().toServerTransport(),
                scope = serverProps.scope,
            )
        }
    )
}

fun TransportConfig.toTransportProperties(): TransportProperties {
    return when (type) {
        "stdio" -> {
            require(!command.isNullOrBlank()) { "Stdio transport requires 'command'" }
            TransportProperties.StdioProperties(command = command, args = args)
        }
        "sse" -> {
            require(!url.isNullOrBlank()) { "SSE transport requires 'url'" }
            TransportProperties.SseProperties(url = url, authToken = authToken)
        }
        "http" -> {
            require(!url.isNullOrBlank()) { "HTTP transport requires 'url'" }
            TransportProperties.HttpProperties(url = url, authToken = authToken)
        }
        else -> throw IllegalArgumentException("Unknown transport type: $type")
    }
}

fun TransportProperties.toServerTransport(): McpServerConfig.Transport {
    return when (this) {
        is TransportProperties.StdioProperties -> {
            McpServerConfig.Transport.StdioTransport(command = command, args = args)
        }
        is TransportProperties.SseProperties -> {
            McpServerConfig.Transport.SseTransport(url = url, authToken = authToken)
        }
        is TransportProperties.HttpProperties -> {
            McpServerConfig.Transport.HttpTransport(url = url, authToken = authToken)
        }
    }
}
