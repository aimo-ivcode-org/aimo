package org.ivcode.aimo.server.mcp.transport

import org.ivcode.aimo.server.mcp.config.McpServerProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Coordinator for transport selection and initialization.
 *
 * Manages which transports are enabled and active based on configuration.
 */
@Component
class TransportCoordinator(
    private val properties: McpServerProperties,
    private val httpTransport: HttpMcpTransport,
    private val sseTransport: SseMcpTransport
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
            logger.info("Enabling HTTP transport")
            httpTransport.initialize()
            activeTransports.add(httpTransport)
        }

        // SSE transport
        if (properties.transports.sse.enabled) {
            logger.info("Enabling SSE transport")
            sseTransport.initialize()
            activeTransports.add(sseTransport)
        }

        logger.info("MCP transports initialized: {} active", activeTransports.size)
    }

    /**
     * Shutdown all active transports.
     */
    fun shutdownTransports() {
        logger.info("Shutting down MCP transports")
        activeTransports.forEach { transport ->
            try {
                transport.shutdown()
            } catch (e: Exception) {
                logger.error("Error shutting down {} transport", transport.name, e)
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

