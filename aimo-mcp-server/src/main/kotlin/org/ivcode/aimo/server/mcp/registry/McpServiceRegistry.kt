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

        // Get service name from annotation if provided
        val serviceAnnotation = serviceClass.getAnnotation(McpService::class.java)
        val serviceName = serviceAnnotation?.name?.takeIf { it.isNotBlank() } ?: ""

        // First, validate that no private/protected methods have @McpTool or @McpPrompt annotations
        val declaredMethods = serviceClass.declaredMethods
        for (method in declaredMethods) {
            val isPublic = java.lang.reflect.Modifier.isPublic(method.modifiers)
            if (!isPublic) {
                val toolAnnotation = method.getAnnotation(McpTool::class.java)
                val promptAnnotation = method.getAnnotation(McpPrompt::class.java)

                if (toolAnnotation != null) {
                    throw IllegalArgumentException(
                        "Service '$beanName' has private/protected method '${method.name}' annotated with @McpTool. " +
                        "Only public methods can be exposed as MCP tools."
                    )
                }
                if (promptAnnotation != null) {
                    throw IllegalArgumentException(
                        "Service '$beanName' has private/protected method '${method.name}' annotated with @McpPrompt. " +
                        "Only public methods can be exposed as MCP prompts."
                    )
                }
            }
        }

        // Build tool registry - only scan public methods
        val serviceMethods = serviceClass.methods
        val tools = mutableListOf<ToolInfo>()
        val prompts = mutableListOf<PromptInfo>()

        // Build ID prefix: "beanName" or "beanName:serviceName"
        val idPrefix = if (serviceName.isNotEmpty()) "$beanName:$serviceName" else beanName

        for (method in serviceMethods) {
            // Check for @McpTool
            method.getAnnotation(McpTool::class.java)?.let { toolAnnotation ->
                val schema = schemaGenerator.generateToolSchema(method)
                val errors = schemaGenerator.validateSchema(schema)

                if (errors.isNotEmpty()) {
                    logger.warn("Tool '{}' validation errors: {}", schema.name, errors)
                }

                val toolId = "$idPrefix:${schema.name}"

                // Build client-visible name
                val clientVisibleName = when {
                    serviceName.isNotEmpty() -> "$serviceName:${schema.name}"
                    else -> schema.name
                }

                // Check for conflicts: same client-visible name from different services
                val conflictingTool = this.tools.entries.find { (existingToolId, registry) ->
                    val existingIdParts = existingToolId.split(":")
                    val existingServiceName = if (existingIdParts.size == 3) existingIdParts[1] else ""
                    val existingToolName = if (existingIdParts.size == 3) existingIdParts[2] else if (existingIdParts.size == 2) existingIdParts[1] else ""

                    val existingClientVisibleName = when {
                        existingServiceName.isNotEmpty() -> "$existingServiceName:$existingToolName"
                        else -> existingToolName
                    }

                    // Conflict if client-visible names match AND they're from different beans
                    existingClientVisibleName == clientVisibleName && registry.beanName != beanName
                }

                if (conflictingTool != null) {
                    throw IllegalArgumentException(
                        "Tool name conflict detected: multiple services expose tool with client-visible name '$clientVisibleName'. " +
                        "Conflicting services: '${conflictingTool.value.beanName}' and '$beanName'. " +
                        "Either use different service names or rename one of the tools."
                    )
                }

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

                val promptId = "$idPrefix:${schema.name}"

                // Build client-visible name
                val clientVisibleName = when {
                    serviceName.isNotEmpty() -> "$serviceName:${schema.name}"
                    else -> schema.name
                }

                // Check for conflicts: same client-visible name from different services
                val conflictingPrompt = this.prompts.entries.find { (existingPromptId, registry) ->
                    val existingIdParts = existingPromptId.split(":")
                    val existingServiceName = if (existingIdParts.size == 3) existingIdParts[1] else ""
                    val existingPromptName = if (existingIdParts.size == 3) existingIdParts[2] else if (existingIdParts.size == 2) existingIdParts[1] else ""

                    val existingClientVisibleName = when {
                        existingServiceName.isNotEmpty() -> "$existingServiceName:$existingPromptName"
                        else -> existingPromptName
                    }

                    // Conflict if client-visible names match AND they're from different beans
                    existingClientVisibleName == clientVisibleName && registry.beanName != beanName
                }

                if (conflictingPrompt != null) {
                    throw IllegalArgumentException(
                        "Prompt name conflict detected: multiple services expose prompt with client-visible name '$clientVisibleName'. " +
                        "Conflicting services: '${conflictingPrompt.value.beanName}' and '$beanName'. " +
                        "Either use different service names or rename one of the prompts."
                    )
                }

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
     *
     * Returns tool names visible to clients:
     * - "serviceName:toolName" (when service has explicit @McpService(name="..."))
     * - "toolName" (when service has no explicit name)
     *
     * The bean name is internal-only for Spring-level isolation and not exposed to clients.
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.entries.map { (toolId, registry) ->
            // Extract client-visible name: remove beanName prefix from toolId
            // toolId format: "beanName" or "beanName:serviceName:toolName" or "beanName:toolName"
            val idParts = toolId.split(":")
            val clientVisibleName = when {
                idParts.size == 3 -> "${idParts[1]}:${idParts[2]}"  // "beanName:serviceName:toolName" -> "serviceName:toolName"
                idParts.size == 2 -> idParts[1]                     // "beanName:toolName" -> "toolName"
                else -> toolId                                        // fallback
            }
            registry.schema.copy(name = clientVisibleName)
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
            // Extract client-visible name: remove beanName prefix from promptId
            // promptId format: "beanName" or "beanName:serviceName:promptName" or "beanName:promptName"
            val idParts = promptId.split(":")
            val clientVisibleName = when {
                idParts.size == 3 -> "${idParts[1]}:${idParts[2]}"  // "beanName:serviceName:promptName" -> "serviceName:promptName"
                idParts.size == 2 -> idParts[1]                     // "beanName:promptName" -> "promptName"
                else -> promptId                                      // fallback
            }
            registry.schema.copy(name = clientVisibleName)
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
        // First try direct lookup with full ID
        tools[toolId]?.let { return it }

        // If not found and has colon, try matching as client-visible format
        // Client calls with "serviceName:toolName", need to find matching "beanName:serviceName:toolName"
        if (toolId.contains(":")) {
            val toolIdParts = toolId.split(":")
            if (toolIdParts.size == 2) {
                // Client-visible format "serviceName:toolName" - find matching internal ID
                for ((internalId, registry) in tools) {
                    val internalIdParts = internalId.split(":")
                    // Match: internalId has 3 parts "beanName:serviceName:toolName"
                    // and client format matches last 2 parts
                    if (internalIdParts.size == 3 &&
                        internalIdParts[1] == toolIdParts[0] &&
                        internalIdParts[2] == toolIdParts[1]) {
                        return registry
                    }
                }
            }
        }

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
     * Supports multiple formats:
     * - "beanName:serviceName:promptName" (full internal ID)
     * - "serviceName:promptName" (client-visible format with service name)
     * - "promptName" (simple prompt name, searches across all services)
     */
    fun getPrompt(promptId: String): PromptRegistry? {
        // First try direct lookup with full ID
        prompts[promptId]?.let { return it }

        // If not found and has colon, try matching as client-visible format
        // Client calls with "serviceName:promptName", need to find matching "beanName:serviceName:promptName"
        if (promptId.contains(":")) {
            val promptIdParts = promptId.split(":")
            if (promptIdParts.size == 2) {
                // Client-visible format "serviceName:promptName" - find matching internal ID
                for ((internalId, registry) in prompts) {
                    val internalIdParts = internalId.split(":")
                    // Match: internalId has 3 parts "beanName:serviceName:promptName"
                    // and client format matches last 2 parts
                    if (internalIdParts.size == 3 &&
                        internalIdParts[1] == promptIdParts[0] &&
                        internalIdParts[2] == promptIdParts[1]) {
                        return registry
                    }
                }
            }
        }

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

