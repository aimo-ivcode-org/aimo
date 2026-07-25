package org.ivcode.aimo.server.mcp.registry

import org.ivcode.aimo.server.mcp.annotation.McpPrompt
import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.protocol.PromptDefinition
import org.ivcode.aimo.server.mcp.protocol.ToolDefinition
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.reflect.Method

/**
 * Service registry that discovers and catalogs MCP services, tools, and prompts.
 *
 * Scans Spring application context for @McpService beans and builds a schema catalog
 * of all available tools and prompts.
 */
@Component
class McpServiceRegistry(
    private val applicationContext: ApplicationContext,
    private val schemaGenerator: McpSchemaGenerator
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val services = mutableMapOf<String, ManagedMcpService>()
    private val tools = mutableMapOf<String, ToolRegistry>()
    private val prompts = mutableMapOf<String, PromptRegistry>()

    /**
     * Discover all @McpService beans and build schema catalog.
     * Called automatically by Spring after bean initialization.
     */
    fun discoverServices() {
        logger.info("Starting MCP service discovery...")

        // Find all @McpService beans
        val mcpServiceBeans = applicationContext.getBeansWithAnnotation(McpService::class.java)

        for ((beanName, bean) in mcpServiceBeans) {
            logger.debug("Discovering @McpService bean: $beanName (${bean.javaClass.simpleName})")
            registerService(beanName, bean)
        }

        logger.info(
            "MCP discovery complete: {} services, {} tools, {} prompts",
            services.size, tools.size, prompts.size
        )
    }

    /**
     * Register a service bean and scan for tools/prompts.
     */
    private fun registerService(beanName: String, bean: Any) {
        val serviceClass = bean.javaClass

        // Build tool registry
        val serviceMethods = serviceClass.declaredMethods
        val tools = mutableListOf<ToolInfo>()
        val prompts = mutableListOf<PromptInfo>()

        for (method in serviceMethods) {
            // Check for @McpTool
            method.getAnnotation(McpTool::class.java)?.let { toolAnnotation ->
                val schema = schemaGenerator.generateToolSchema(method)
                val errors = schemaGenerator.validateSchema(schema)

                if (errors.isNotEmpty()) {
                    logger.warn("Tool '{}' validation errors: {}", schema.name, errors)
                }

                val toolId = "${beanName}:${schema.name}"
                tools.add(
                    ToolInfo(
                        id = toolId,
                        beanName = beanName,
                        method = method,
                        schema = schema
                    )
                )

                this.tools[toolId] = ToolRegistry(
                    beanName = beanName,
                    bean = bean,
                    method = method,
                    schema = schema
                )

                logger.debug("Registered tool: {}", toolId)
            }

            // Check for @McpPrompt
            method.getAnnotation(McpPrompt::class.java)?.let { promptAnnotation ->
                val schema = schemaGenerator.generatePromptSchema(method)

                val promptId = "${beanName}:${schema.name}"
                prompts.add(
                    PromptInfo(
                        id = promptId,
                        beanName = beanName,
                        method = method,
                        schema = schema
                    )
                )

                this.prompts[promptId] = PromptRegistry(
                    beanName = beanName,
                    bean = bean,
                    method = method,
                    schema = schema
                )

                logger.debug("Registered prompt: {}", promptId)
            }
        }

        services[beanName] = ManagedMcpService(
            beanName = beanName,
            bean = bean,
            tools = tools,
            prompts = prompts
        )
    }

    /**
     * Get all registered tool definitions.
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.schema }
    }

    /**
     * Get all registered prompt definitions.
     */
    fun getPromptDefinitions(): List<PromptDefinition> {
        return prompts.values.map { it.schema }
    }

    /**
     * Look up a tool by ID.
     * Supports both "beanName:toolName" format and simple "toolName" format
     * (in which case it searches by tool name across all services).
     */
    fun getTool(toolId: String): ToolRegistry? {
        // First try direct lookup with full ID (e.g., "weatherService:get-weather")
        tools[toolId]?.let { return it }

        // If not found and no colon, search by tool name across all services
        if (!toolId.contains(":")) {
            for ((_, toolRegistry) in tools) {
                if (toolRegistry.schema.name == toolId) {
                    return toolRegistry
                }
            }
        }

        return null
    }

    /**
     * Look up a prompt by ID.
     * Supports both "beanName:promptName" format and simple "promptName" format
     * (in which case it searches by prompt name across all services).
     */
    fun getPrompt(promptId: String): PromptRegistry? {
        // First try direct lookup with full ID (e.g., "weatherService:weather-help")
        prompts[promptId]?.let { return it }

        // If not found and no colon, search by prompt name across all services
        if (!promptId.contains(":")) {
            for ((_, promptRegistry) in prompts) {
                if (promptRegistry.schema.name == promptId) {
                    return promptRegistry
                }
            }
        }

        return null
    }

    /**
     * Get all registered services.
     */
    fun getServices(): Map<String, ManagedMcpService> {
        return services.toMap()
    }

    /**
     * Get service by bean name.
     */
    fun getService(beanName: String): ManagedMcpService? {
        return services[beanName]
    }

    /**
     * Get all tool IDs.
     */
    fun getToolIds(): List<String> {
        return tools.keys.toList()
    }

    /**
     * Get all prompt IDs.
     */
    fun getPromptIds(): List<String> {
        return prompts.keys.toList()
    }
}

/**
 * Managed service container.
 */
data class ManagedMcpService(
    val beanName: String,
    val bean: Any,
    val tools: List<ToolInfo>,
    val prompts: List<PromptInfo>
)

/**
 * Tool metadata and references.
 */
data class ToolInfo(
    val id: String,
    val beanName: String,
    val method: Method,
    val schema: ToolDefinition
)

/**
 * Prompt metadata and references.
 */
data class PromptInfo(
    val id: String,
    val beanName: String,
    val method: Method,
    val schema: PromptDefinition
)

/**
 * Invokable tool registry entry.
 */
data class ToolRegistry(
    val beanName: String,
    val bean: Any,
    val method: Method,
    val schema: ToolDefinition
)

/**
 * Invokable prompt registry entry.
 */
data class PromptRegistry(
    val beanName: String,
    val bean: Any,
    val method: Method,
    val schema: PromptDefinition
)

