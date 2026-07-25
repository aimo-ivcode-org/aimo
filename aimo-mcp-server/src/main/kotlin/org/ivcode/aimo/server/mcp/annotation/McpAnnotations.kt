package org.ivcode.aimo.server.mcp.annotation

import org.springframework.stereotype.Component

/**
 * Marks a Spring bean as an MCP service provider.
 *
 * The annotated class becomes a container for MCP tools and prompts.
 * All public methods annotated with @McpTool or @McpPrompt are discovered
 * and registered as MCP callables.
 *
 * This annotation is itself a @Component, so @McpService beans are automatically
 * registered with Spring and can be discovered at startup for tool/prompt binding.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
public annotation class McpService

/**
 * Marks a method as an MCP tool (callable by LLM).
 *
 * The method signature is inspected to generate the OpenRPC schema.
 * Return type must be String or a serializable object.
 * Parameters annotated with @McpParam are documented; others are inferred from reflection.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class McpTool(
    /**
     * Optional human-readable name for the tool.
     * If omitted, the method name is used.
     */
    val name: String = "",

    /**
     * Optional description of what the tool does.
     */
    val description: String = ""
)

/**
 * Marks a method as an MCP prompt (template/workflow).
 *
 * The method is invoked with optional arguments and returns a prompt text.
 * Signature must be () -> String? or (context: McpPromptContext) -> String?
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class McpPrompt(
    /**
     * Optional human-readable name for the prompt.
     * If omitted, the method name is used.
     */
    val name: String = "",

    /**
     * Optional description of the prompt's purpose.
     */
    val description: String = ""
)

/**
 * Documents a tool parameter for schema generation.
 *
 * Applied to a method parameter to provide description and schema information.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class McpParam(
    /**
     * Description of the parameter's purpose.
     */
    val description: String = "",

    /**
     * Whether this parameter is required.
     * If true, the generated schema marks it as required.
     */
    val required: Boolean = true
)

/**
 * Marks a method parameter for automatic context injection.
 *
 * The framework injects request context (e.g., user input, session state, requestId)
 * into a parameter of type Map<String, Any?> or a custom context object.
 *
 * Only one @McpContext parameter per method is allowed.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class McpContext

