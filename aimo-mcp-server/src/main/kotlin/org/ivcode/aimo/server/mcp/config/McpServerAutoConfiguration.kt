package org.ivcode.aimo.server.mcp.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.error.McpErrorHandler
import org.ivcode.aimo.server.mcp.handler.McpRequestHandler
import org.ivcode.aimo.server.mcp.handler.ParameterBinder
import org.ivcode.aimo.server.mcp.handler.PromptGetHandler
import org.ivcode.aimo.server.mcp.handler.ToolCallHandler
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.ivcode.aimo.server.mcp.validation.ParameterValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
class McpServerAutoConfiguration {

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
        parameterBinder: ParameterBinder
    ): ToolCallHandler {
        return ToolCallHandler(serviceRegistry, parameterBinder)
    }

    /**
     * Register prompt get handler bean.
     */
    @Bean
    @ConditionalOnMissingBean(PromptGetHandler::class)
    fun promptGetHandler(
        serviceRegistry: McpServiceRegistry,
        parameterBinder: ParameterBinder
    ): PromptGetHandler {
        return PromptGetHandler(serviceRegistry, parameterBinder)
    }

    /**
     * Register request handler bean.
     */
    @Bean
    @ConditionalOnMissingBean(McpRequestHandler::class)
    fun mcpRequestHandler(
        serviceRegistry: McpServiceRegistry,
        toolCallHandler: ToolCallHandler,
        promptGetHandler: PromptGetHandler
    ): McpRequestHandler {
        return McpRequestHandler(serviceRegistry, toolCallHandler, promptGetHandler)
    }
}

/**
 * Annotation to enable MCP server auto-configuration.
 *
 * Add this to any Spring Boot configuration class to enable the MCP server framework.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@org.springframework.context.annotation.Import(
    McpServerAutoConfiguration::class,
    McpTransportConfiguration::class,
    McpServerStartupListener::class
)
annotation class EnableMcpServer

