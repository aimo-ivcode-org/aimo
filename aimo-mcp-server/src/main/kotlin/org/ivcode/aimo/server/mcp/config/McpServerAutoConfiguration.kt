package org.ivcode.aimo.server.mcp.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.error.McpErrorHandler
import org.ivcode.aimo.server.mcp.handler.McpRequestHandler
import org.ivcode.aimo.server.mcp.handler.ParameterBinder
import org.ivcode.aimo.server.mcp.handler.PromptGetHandler
import org.ivcode.aimo.server.mcp.handler.ToolCallHandler
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.ivcode.aimo.server.mcp.transport.HttpMcpTransport
import org.ivcode.aimo.server.mcp.transport.SseMcpTransport
import org.ivcode.aimo.server.mcp.transport.TransportCoordinator
import org.ivcode.aimo.server.mcp.validation.ParameterValidator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.ObjectProvider
import org.ivcode.aimo.server.mcp.transport.StdioMcpTransport
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener

/**
 * Spring Boot auto-configuration for MCP server framework.
 *
 * Registers all required beans for the MCP server to operate.
 * Enable via @EnableMcpServer annotation.
 *
 * Transport activation is controlled by individual transport enabled flags
 * in application.yml (aimo.mcp-server.transports.http.enabled, etc.).
 */
@Configuration
@EnableConfigurationProperties(McpServerProperties::class)
@ConditionalOnProperty(prefix = "aimo.mcp-server", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class McpServerAutoConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Configure ObjectMapper for JSON serialization.
     * Creates a basic ObjectMapper that works with JSON-RPC requests/responses.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper::class)
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
    }

    /**
     * Register schema generator bean.
     */
    @Bean
    @ConditionalOnMissingBean(McpSchemaGenerator::class)
    fun mcpSchemaGenerator(): McpSchemaGenerator {
        return McpSchemaGenerator()
    }

    /**
     * Register service registry bean.
     */
    @Bean
    @ConditionalOnMissingBean(McpServiceRegistry::class)
    fun mcpServiceRegistry(
        applicationContext: ApplicationContext,
        schemaGenerator: McpSchemaGenerator
    ): McpServiceRegistry {
        return McpServiceRegistry(applicationContext, schemaGenerator)
    }

    /**
     * Register parameter validator bean.
     */
    @Bean
    @ConditionalOnMissingBean(ParameterValidator::class)
    fun parameterValidator(): ParameterValidator {
        return ParameterValidator()
    }

    /**
     * Register error handler bean.
     */
    @Bean
    @ConditionalOnMissingBean(McpErrorHandler::class)
    fun mcpErrorHandler(): McpErrorHandler {
        return McpErrorHandler()
    }

    /**
     * Register parameter binder bean.
     */
    @Bean
    @ConditionalOnMissingBean(ParameterBinder::class)
    fun parameterBinder(objectMapper: ObjectMapper): ParameterBinder {
        return ParameterBinder(objectMapper)
    }

    /**
     * Register tool call handler bean.
     */
    @Bean
    @ConditionalOnMissingBean(ToolCallHandler::class)
    fun toolCallHandler(
        serviceRegistry: McpServiceRegistry,
        parameterBinder: ParameterBinder,
        objectMapper: ObjectMapper
    ): ToolCallHandler {
        return ToolCallHandler(serviceRegistry, parameterBinder, objectMapper)
    }

    /**
     * Register prompt get handler bean.
     */
    @Bean
    @ConditionalOnMissingBean(PromptGetHandler::class)
    fun promptGetHandler(
        serviceRegistry: McpServiceRegistry,
        parameterBinder: ParameterBinder,
        objectMapper: ObjectMapper
    ): PromptGetHandler {
        return PromptGetHandler(serviceRegistry, parameterBinder, objectMapper)
    }

    /**
     * Register request handler bean.
     */
    @Bean
    @ConditionalOnMissingBean(McpRequestHandler::class)
    fun mcpRequestHandler(
        serviceRegistry: McpServiceRegistry,
        toolCallHandler: ToolCallHandler,
        promptGetHandler: PromptGetHandler,
        objectMapper: ObjectMapper
    ): McpRequestHandler {
        return McpRequestHandler(serviceRegistry, toolCallHandler, promptGetHandler, objectMapper)
    }

    /**
     * Register HTTP transport bean.
     */
    @Bean
    @ConditionalOnMissingBean(HttpMcpTransport::class)
    @ConditionalOnProperty(prefix = "aimo.mcp-server.transports.http", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun httpMcpTransport(
        requestHandler: McpRequestHandler,
        objectMapper: ObjectMapper
    ): HttpMcpTransport {
        return HttpMcpTransport(requestHandler, objectMapper)
    }

    /**
     * Register SSE transport bean.
     */
    @Bean
    @ConditionalOnMissingBean(SseMcpTransport::class)
    @ConditionalOnProperty(prefix = "aimo.mcp-server.transports.sse", name = ["enabled"], havingValue = "true", matchIfMissing = false)
    fun sseMcpTransport(
        requestHandler: McpRequestHandler,
        objectMapper: ObjectMapper
    ): SseMcpTransport {
        return SseMcpTransport(requestHandler, objectMapper)
    }

    /**
     * Register stdio transport as a bean when enabled. Stdio transport is implemented as a Lifecycle
     * and must be created explicitly from auto-configuration to avoid accidental creation via
     * component scanning (which may not happen when only @EnableMcpServer is used).
     */
    @Bean
    @ConditionalOnMissingBean(StdioMcpTransport::class)
    @ConditionalOnProperty(prefix = "aimo.mcp-server.transports.stdio", name = ["enabled"], havingValue = "true", matchIfMissing = false)
    fun stdioMcpTransport(requestHandler: McpRequestHandler, objectMapper: ObjectMapper): StdioMcpTransport {
        return StdioMcpTransport(requestHandler, objectMapper)
    }

    /**
     * Register transport coordinator bean.
     */
    @Bean
    @ConditionalOnMissingBean(TransportCoordinator::class)
    fun transportCoordinator(
        properties: McpServerProperties,
        httpTransportProvider: ObjectProvider<HttpMcpTransport>,
        sseTransportProvider: ObjectProvider<SseMcpTransport>,
        stdioTransportProvider: ObjectProvider<org.ivcode.aimo.server.mcp.transport.McpTransport>
    ): TransportCoordinator {
        return TransportCoordinator(properties, httpTransportProvider, sseTransportProvider, stdioTransportProvider)
    }

    /**
     * Event listener for Spring context initialization.
     *
     * Discovers services and initializes transports when the context is ready.
     */
    @EventListener(ContextRefreshedEvent::class)
    fun onContextRefreshed(event: ContextRefreshedEvent) {
        val serviceRegistry = event.applicationContext.getBean(McpServiceRegistry::class.java)
        val coordinator = event.applicationContext.getBean(TransportCoordinator::class.java)
        val properties = event.applicationContext.getBean(McpServerProperties::class.java)


        logger.info("Initializing MCP server framework (v{})", properties.version)

        try {
            // Discover services
            serviceRegistry.discoverServices()

            // Check if discovery found any services
            if (properties.discovery.failIfEmpty && serviceRegistry.getServices().isEmpty()) {
                logger.error("No MCP services found and failIfEmpty is true")
                throw IllegalStateException("No MCP services found")
            }

            // Initialize transports
            coordinator.initializeTransports()

            logger.info("MCP server initialized successfully")
        } catch (e: Exception) {
            logger.error("Error initializing MCP server", e)
            throw e
        }
    }
}

/**
 * Annotation to enable MCP server auto-configuration.
 *
 * Add this to any Spring Boot configuration class to enable the MCP server framework.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@org.springframework.context.annotation.Import(McpServerAutoConfiguration::class)
annotation class EnableMcpServer

