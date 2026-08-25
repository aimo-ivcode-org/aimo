package org.ivcode.aimo.server.mcp.transport

import org.ivcode.aimo.server.mcp.config.McpServerProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

/**
 * Coordinator for transport selection and initialization.
 *
 * Manages which transports are enabled and active based on configuration.
 */
class TransportCoordinator(
    private val properties: McpServerProperties,
    private val httpTransportProvider: ObjectProvider<HttpMcpTransport>,
    private val sseTransportProvider: ObjectProvider<SseMcpTransport>,
    private val stdioTransportProvider: ObjectProvider<out McpTransport>
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val activeTransports = mutableListOf<McpTransport>()

    /**
     * Initialize transports based on configuration.
     */
    fun initializeTransports() {
        logger.info("Initializing MCP transports")

        // HTTP transport (usually enabled by default)
        if (properties.transports.http.enabled) {
            val httpTransport = httpTransportProvider.getIfAvailable()
            if (httpTransport != null) {
                logger.info("Enabling HTTP transport")
                httpTransport.initialize()
                activeTransports.add(httpTransport)
            } else {
                logger.warn("HTTP transport is enabled in configuration but no HttpMcpTransport bean is available")
            }
        }

        // SSE transport
        if (properties.transports.sse.enabled) {
            val sseTransport = sseTransportProvider.getIfAvailable()
            if (sseTransport != null) {
                logger.info("Enabling SSE transport")
                sseTransport.initialize()
                activeTransports.add(sseTransport)
            } else {
                logger.warn("SSE transport is enabled in configuration but no SseMcpTransport bean is available")
            }
        }

        // Stdio transport
        if (properties.transports.stdio.enabled) {
            val stdioTransport = stdioTransportProvider.getIfAvailable()
            if (stdioTransport != null) {
                logger.info("Enabling Stdio transport")
                try {
                    stdioTransport.initialize()
                    activeTransports.add(stdioTransport)
                } catch (exception: IllegalStateException) {
                    logger.error("Error initializing Stdio transport", exception)
                }
            } else {
                logger.warn("Stdio transport is enabled in configuration but no stdio transport bean is available")
            }
        }

        logger.info("MCP transports initialized: {} active", activeTransports.size)

        // Fail-fast: if no transports are active after initialization, consider the
        // server non-functional and abort startup by throwing an exception. This
        // prevents the application from running with no way to receive MCP requests.
        if (activeTransports.isEmpty()) {
            logger.error("No active MCP transports configured; aborting startup")
            throw IllegalStateException("No active MCP transports configured")
        }
    }

    /**
     * Shutdown all active transports.
     */
    fun shutdownTransports() {
        logger.info("Shutting down MCP transports")
        activeTransports.forEach { transport ->
            try {
                transport.shutdown()
            } catch (exception: IllegalStateException) {
                logger.error("Error shutting down {} transport", transport.name, exception)
            }
        }
        activeTransports.clear()
    }

    /**
     * Get all active transports.
     */
    fun getActiveTransports(): List<McpTransport> {
        return activeTransports.toList()
    }

    /**
     * Check if a specific transport is active.
     */
    fun isTransportActive(transportName: String): Boolean {
        return activeTransports.any { it.name == transportName && it.isActive() }
    }
}

