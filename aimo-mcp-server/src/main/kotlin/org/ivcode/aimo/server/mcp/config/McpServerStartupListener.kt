package org.ivcode.aimo.server.mcp.config

import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.ivcode.aimo.server.mcp.transport.TransportCoordinator
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Coordinates MCP service discovery and transport startup after the Spring context refreshes.
 *
 * Owns runtime bootstrapping concerns so configuration classes can stay focused on
 * bean registration and wiring.
 */
@Component
class McpServerStartupListener {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Initialize the MCP server after the application context is ready.
     *
     * @param event Spring context refresh event.
     * @throws IllegalStateException when required services or transports are unavailable.
     */
    @EventListener(ContextRefreshedEvent::class)
    fun onContextRefreshed(event: ContextRefreshedEvent) {
        val applicationContext = event.applicationContext
        val serviceRegistry = applicationContext.getBean(McpServiceRegistry::class.java)
        val coordinator = applicationContext.getBean(TransportCoordinator::class.java)
        val properties = applicationContext.getBean(McpServerProperties::class.java)

        logger.info("Initializing MCP server framework (v{})", properties.version)

        // Discover annotated MCP services before starting any transport endpoints.
        serviceRegistry.discoverServices()
        logSyntheticParameterWarnings(serviceRegistry)

        // Fail fast when the server is configured to require at least one service.
        requireServicesWhenConfigured(properties, serviceRegistry)

        // Start transports only after discovery and validation succeed.
        coordinator.initializeTransports()
        logger.info("MCP server initialized successfully")
    }

    /**
     * Log warnings for synthetic Java parameter names discovered during service scanning.
     *
     * @param serviceRegistry service registry containing discovered tools and prompts.
     */
    private fun logSyntheticParameterWarnings(serviceRegistry: McpServiceRegistry) {
        val syntheticParameters = serviceRegistry.detectSyntheticParameterNames()
        if (syntheticParameters.isEmpty()) {
            return
        }

        logger.warn(
            "Detected {} parameter(s) with synthetic Java names. " +
                "This may break schema generation or runtime parameter binding.",
            syntheticParameters.size
        )
        syntheticParameters.take(SYNTHETIC_PARAMETER_WARNING_LIMIT).forEach { warning ->
            logger.warn(warning)
        }
        if (syntheticParameters.size > SYNTHETIC_PARAMETER_WARNING_LIMIT) {
            logger.warn(
                "...and {} more",
                syntheticParameters.size - SYNTHETIC_PARAMETER_WARNING_LIMIT
            )
        }
        logger.warn(
            "Recommend compiling Kotlin/JVM code with -java-parameters or annotating " +
                "parameters with @McpParam to provide stable names."
        )
    }

    /**
     * Enforce the fail-if-empty discovery setting.
     *
     * @param properties server properties controlling startup behavior.
     * @param serviceRegistry service registry containing discovered services.
     * @throws IllegalStateException when no services are found and fail-if-empty is enabled.
     */
    private fun requireServicesWhenConfigured(
        properties: McpServerProperties,
        serviceRegistry: McpServiceRegistry
    ) {
        if (properties.discovery.failIfEmpty && serviceRegistry.getServices().isEmpty()) {
            logger.error("No MCP services found and failIfEmpty is true")
            throw IllegalStateException("No MCP services found")
        }
    }

    private companion object {
        private const val SYNTHETIC_PARAMETER_WARNING_LIMIT = 10
    }
}

