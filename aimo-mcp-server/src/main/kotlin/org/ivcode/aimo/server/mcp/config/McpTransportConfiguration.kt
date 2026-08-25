package org.ivcode.aimo.server.mcp.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.handler.McpRequestHandler
import org.ivcode.aimo.server.mcp.transport.HttpMcpTransport
import org.ivcode.aimo.server.mcp.transport.SseMcpTransport
import org.ivcode.aimo.server.mcp.transport.StdioMcpTransport
import org.ivcode.aimo.server.mcp.transport.TransportCoordinator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers MCP transport beans and the transport coordinator.
 *
 * Owns transport-specific bean creation so the primary auto-configuration can
 * stay focused on core handler and registry infrastructure.
 */
@Configuration
class McpTransportConfiguration {
    /**
     * Register HTTP transport bean.
     *
     * @param requestHandler request router used by the transport.
     * @param objectMapper JSON mapper used for request and response payloads.
     * @return HTTP transport when the feature is enabled.
     */
    @Bean
    @ConditionalOnMissingBean(HttpMcpTransport::class)
    @ConditionalOnProperty(
        prefix = "aimo.mcp-server.transports.http",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun httpMcpTransport(
        requestHandler: McpRequestHandler,
        objectMapper: ObjectMapper
    ): HttpMcpTransport {
        return HttpMcpTransport(requestHandler, objectMapper)
    }

    /**
     * Register SSE transport bean.
     *
     * @param requestHandler request router used by the transport.
     * @return SSE transport when the feature is enabled.
     */
    @Bean
    @ConditionalOnMissingBean(SseMcpTransport::class)
    @ConditionalOnProperty(
        prefix = "aimo.mcp-server.transports.sse",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun sseMcpTransport(requestHandler: McpRequestHandler): SseMcpTransport {
        return SseMcpTransport(requestHandler)
    }

    /**
     * Register stdio transport bean.
     *
     * @param requestHandler request router used by the transport.
     * @param objectMapper JSON mapper used for parsing stdin and writing stdout.
     * @return stdio transport when the feature is enabled.
     */
    @Bean
    @ConditionalOnMissingBean(StdioMcpTransport::class)
    @ConditionalOnProperty(
        prefix = "aimo.mcp-server.transports.stdio",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun stdioMcpTransport(
        requestHandler: McpRequestHandler,
        objectMapper: ObjectMapper
    ): StdioMcpTransport {
        return StdioMcpTransport(requestHandler, objectMapper)
    }

    /**
     * Register the transport coordinator bean.
     *
     * @param properties server properties that control transport enablement.
     * @param httpTransportProvider optional HTTP transport provider.
     * @param sseTransportProvider optional SSE transport provider.
     * @param stdioTransportProvider optional stdio transport provider.
     * @return transport coordinator used during startup and shutdown.
     */
    @Bean
    @ConditionalOnMissingBean(TransportCoordinator::class)
    fun transportCoordinator(
        properties: McpServerProperties,
        httpTransportProvider: ObjectProvider<HttpMcpTransport>,
        sseTransportProvider: ObjectProvider<SseMcpTransport>,
        stdioTransportProvider: ObjectProvider<StdioMcpTransport>
    ): TransportCoordinator {
        return TransportCoordinator(
            properties = properties,
            httpTransportProvider = httpTransportProvider,
            sseTransportProvider = sseTransportProvider,
            stdioTransportProvider = stdioTransportProvider
        )
    }
}


