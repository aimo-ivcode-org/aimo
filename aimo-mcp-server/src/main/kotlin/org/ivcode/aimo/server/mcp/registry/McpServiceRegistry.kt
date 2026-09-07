package org.ivcode.aimo.server.mcp.registry

import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.protocol.PromptDefinition
import org.ivcode.aimo.server.mcp.protocol.ToolDefinition
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.lang.reflect.Method

/**
 * Service registry that discovers and catalogs MCP services, tools, and prompts.
 *
 * Scans Spring application context for @McpService beans and builds a schema catalog
 * of all available tools and prompts.
 */
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
        val descriptor = buildServiceDescriptor(beanName, bean)

        // Validate declared methods before exposing any public members.
        validateAnnotatedMethodVisibility(descriptor)

        // Register all public tool and prompt methods for the service.
        val serviceTools = registerToolMethods(descriptor, schemaGenerator, logger, tools)
        val servicePrompts = registerPromptMethods(descriptor, schemaGenerator, logger, prompts)

        services[beanName] = ManagedMcpService(
            beanName = beanName,
            bean = bean,
            tools = serviceTools,
            prompts = servicePrompts
        )
    }

    /**
     * Get all registered tool definitions.
     *
     * Returns tool names visible to clients:
     * - "serviceName:toolName" (when service has explicit @McpService(name="..."))
     * - "toolName" (when service has no explicit name)
     *
     * The bean name is internal-only for Spring-level isolation and not exposed to clients.
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.entries.map { (toolId, registry) ->
            registry.schema.copy(name = registryClientVisibleName(toolId))
        }
    }

    /**
     * Get all registered prompt definitions.
     *
     * Returns prompt names visible to clients:
     * - "serviceName:promptName" (when service has explicit @McpService(name="..."))
     * - "promptName" (when service has no explicit name)
     *
     * The bean name is internal-only for Spring-level isolation and not exposed to clients.
     */
    fun getPromptDefinitions(): List<PromptDefinition> {
        return prompts.entries.map { (promptId, registry) ->
            registry.schema.copy(name = registryClientVisibleName(promptId))
        }
    }

    /**
     * Look up a tool by ID.
     * Supports multiple formats:
     * - "beanName:serviceName:toolName" (full internal ID)
     * - "serviceName:toolName" (client-visible format with service name)
     * - "toolName" (simple tool name, searches across all services)
     */
    fun getTool(toolId: String): ToolRegistry? {
        val directMatch = tools[toolId]
        val resolvedMatch = directMatch
            ?: resolveToolByClientVisibleId(toolId, tools)
            ?: resolveToolBySimpleName(toolId, tools)
        return resolvedMatch
    }

    /**
     * Look up a prompt by ID.
     * Supports multiple formats:
     * - "beanName:serviceName:promptName" (full internal ID)
     * - "serviceName:promptName" (client-visible format with service name)
     * - "promptName" (simple prompt name, searches across all services)
     */
    fun getPrompt(promptId: String): PromptRegistry? {
        val directMatch = prompts[promptId]
        val resolvedMatch = directMatch
            ?: resolvePromptByClientVisibleId(promptId, prompts)
            ?: resolvePromptBySimpleName(promptId, prompts)
        return resolvedMatch
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
     * Check for methods that expose synthetic Java parameter names (e.g., "arg0").
     * Returns a list of description strings for offending methods.
     */
    fun detectSyntheticParameterNames(): List<String> {
        val problems = mutableListOf<String>()

        for ((beanName, managed) in services) {
            for (tool in managed.tools) {
                val method = tool.method
                for ((i, param) in method.parameters.withIndex()) {
                    val pname = param.name
                    if (pname == null || pname.matches(Regex("arg\\d+"))) {
                        problems.add("${beanName}.${method.name} parameter at index ${i} has synthetic name '${pname}'")
                    }
                }
            }

            for (prompt in managed.prompts) {
                val method = prompt.method
                for ((i, param) in method.parameters.withIndex()) {
                    val pname = param.name
                    if (pname == null || pname.matches(Regex("arg\\d+"))) {
                        problems.add("${beanName}.${method.name} parameter at index ${i} has synthetic name '${pname}'")
                    }
                }
            }
        }

        return problems
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

