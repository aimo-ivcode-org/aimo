package org.ivcode.aimo.server.mcp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * MCP server configuration properties.
 *
 * Binds to application.yml configuration under "aimo.mcp-server" prefix.
 */
@ConfigurationProperties(prefix = "aimo.mcp-server")
data class McpServerProperties(

    /**
     * Server name.
     */
    var name: String = "aimo-mcp-server",

    /**
     * Server version.
     */
    var version: String = "1.0.0",

    /**
     * Master enable flag for the MCP server framework.
     * When false, auto-configuration should be skipped and transports/controllers not registered.
     */
    var enabled: Boolean = true,

    /**
     * Transport configurations.
     */
    @NestedConfigurationProperty
    var transports: TransportProperties = TransportProperties(),

    /**
     * Service discovery settings.
     */
    @NestedConfigurationProperty
    var discovery: DiscoveryProperties = DiscoveryProperties(),

    /**
     * Error handling settings.
     */
    @NestedConfigurationProperty
    var errorHandling: ErrorHandlingProperties = ErrorHandlingProperties()
)

/**
 * Transport-specific configuration.
 */
data class TransportProperties(
    /**
     * HTTP transport configuration.
     */
    @NestedConfigurationProperty
    var http: HttpTransportProperties = HttpTransportProperties(),

    /**
     * SSE transport configuration.
     */
    @NestedConfigurationProperty
    var sse: SseTransportProperties = SseTransportProperties(),

    /**
     * Stdio transport configuration.
     */
    @NestedConfigurationProperty
    var stdio: StdioTransportProperties = StdioTransportProperties()
)

/**
 * HTTP transport configuration.
 */
data class HttpTransportProperties(
    /**
     * Enable HTTP transport.
     */
    var enabled: Boolean = true,

    /**
     * Server port (uses Spring's default if not set).
     */
    var port: Int? = null,

    /**
     * Base path for MCP endpoints.
     */
    var basePath: String = "/mcp",

    /**
     * Connection timeout in milliseconds.
     */
    var connectionTimeout: Long = 30000,

    /**
     * Read timeout in milliseconds.
     */
    var readTimeout: Long = 30000
)

/**
 * SSE transport configuration.
 */
data class SseTransportProperties(
    /**
     * Enable SSE transport.
     */
    var enabled: Boolean = false,

    /**
     * Base path for SSE endpoints.
     */
    var basePath: String = "/mcp/sse",

    /**
     * Connection timeout in milliseconds.
     */
    var connectionTimeout: Long = 300000,

    /**
     * Keep-alive interval in milliseconds.
     */
    var keepAliveInterval: Long = 30000
)

/**
 * Stdio transport configuration.
 */
data class StdioTransportProperties(
    /**
     * Enable stdio transport.
     */
    var enabled: Boolean = false
)

/**
 * Service discovery configuration.
 */
data class DiscoveryProperties(
    /**
     * Enable automatic service discovery.
     */
    var enabled: Boolean = true,

    /**
     * Base package(s) to scan for @McpService beans (comma-separated).
     * If empty, scans all packages.
     */
    var basePackages: String = "",

    /**
     * Fail startup if discovery finds no services.
     */
    var failIfEmpty: Boolean = false
)

/**
 * Error handling configuration.
 */
data class ErrorHandlingProperties(
    /**
     * Include stack traces in error responses.
     */
    var includeStackTrace: Boolean = false,

    /**
     * Include detailed error data in responses.
     */
    var includeErrorData: Boolean = true,

    /**
     * Fail tool invocation on validation error.
     */
    var failOnValidationError: Boolean = true
)

