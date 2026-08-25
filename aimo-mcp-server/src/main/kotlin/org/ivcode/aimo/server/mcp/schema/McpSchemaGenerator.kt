package org.ivcode.aimo.server.mcp.schema

import org.ivcode.aimo.server.mcp.annotation.McpContext
import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.annotation.McpPrompt
import org.ivcode.aimo.server.mcp.protocol.PromptArgument
import org.ivcode.aimo.server.mcp.protocol.PromptDefinition
import org.ivcode.aimo.server.mcp.protocol.PropertySchema
import org.ivcode.aimo.server.mcp.protocol.ToolDefinition
import org.ivcode.aimo.server.mcp.protocol.ToolInputSchema
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.KClass

/**
 * Generates OpenRPC-compliant schemas from annotated method signatures.
 */
class McpSchemaGenerator {

    /**
     * Generate a tool definition from a method.
     */
    fun generateToolSchema(method: Method): ToolDefinition {
        // Get tool name from @McpTool annotation, or fall back to method name
        val toolAnnotation = method.getAnnotation(McpTool::class.java)
        val name = if (toolAnnotation != null && toolAnnotation.name.isNotEmpty()) {
            toolAnnotation.name
        } else {
            method.name
        }
        val description = toolAnnotation?.description?.takeIf { it.isNotBlank() }

        // Extract parameters, excluding @McpContext
        val parameters = method.parameters.filter { !hasAnnotation(it, McpContext::class) }.toTypedArray()

        // Build input schema
        val inputSchema = if (parameters.isNotEmpty()) {
            buildToolInputSchema(parameters)
        } else {
            null
        }

        return ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema
        )
    }

    /**
     * Generate a prompt definition from a method.
     */
    fun generatePromptSchema(method: Method): PromptDefinition {
        // Get prompt name from @McpPrompt annotation, or fall back to method name
        val promptAnnotation = method.getAnnotation(McpPrompt::class.java)
        val name = if (promptAnnotation != null && promptAnnotation.name.isNotEmpty()) {
            promptAnnotation.name
        } else {
            method.name
        }
        val description = promptAnnotation?.description?.takeIf { it.isNotBlank() }

        // Extract arguments (excluding context)
        val arguments = method.parameters
            .filter { !hasAnnotation(it, McpContext::class) }
            .map { param ->
                val mcpParam = getAnnotation<McpParam>(param)
                PromptArgument(
                    name = param.name,
                    description = mcpParam?.description,
                    required = mcpParam?.required ?: false
                )
            }

        return PromptDefinition(
            name = name,
            description = description,
            arguments = if (arguments.isNotEmpty()) arguments else null
        )
    }

    /**
     * Build input schema for tool parameters.
     */
    private fun buildToolInputSchema(parameters: Array<Parameter>): ToolInputSchema {
        val properties = mutableMapOf<String, PropertySchema>()
        val required = mutableListOf<String>()

        for (param in parameters.toList()) {
            val paramName = param.name
            val mcpParam = getAnnotation<McpParam>(param)

            val paramType = getPropertyType(param.type)
            val description = mcpParam?.description

            properties[paramName] = PropertySchema(
                type = paramType,
                description = description
            )

            if (mcpParam?.required != false) {
                required.add(paramName)
            }
        }

        return ToolInputSchema(
            type = "object",
            properties = properties,
            required = if (required.isNotEmpty()) required else null
        )
    }

    /**
     * Map Java type to OpenRPC type string.
     */
    private fun getPropertyType(type: Class<*>): String {
        return when {
            type == String::class.java -> "string"
            type in INTEGER_TYPES -> "integer"
            type in NUMBER_TYPES -> "number"
            type in BOOLEAN_TYPES -> "boolean"
            type.isArray -> "array"
            type in ARRAY_LIKE_TYPES -> "array"
            type == Map::class.java -> "object"
            else -> "string"
        }
    }

    /**
     * Check if parameter has a specific annotation.
     */
    private fun hasAnnotation(param: Parameter, annotationClass: KClass<out Annotation>): Boolean {
        return param.getAnnotation(annotationClass.java) != null
    }

    /**
     * Get annotation from parameter if present.
     */
    private inline fun <reified T : Annotation> getAnnotation(param: Parameter): T? {
        return param.getAnnotation(T::class.java)
    }

    /**
     * Validate tool schema for consistency.
     */
    fun validateSchema(schema: ToolDefinition): List<String> {
        val errors = mutableListOf<String>()

        if (schema.name.isBlank()) {
            errors.add("Tool name cannot be empty")
        }

        // Validate inputSchema if present
        schema.inputSchema?.let { inputSchema ->
            if (inputSchema.type != "object") {
                errors.add("Tool input schema type must be 'object'")
            }

            // Check required fields are defined in properties
            inputSchema.required?.forEach { requiredField ->
                if (!inputSchema.properties.containsKey(requiredField)) {
                    errors.add("Required field '$requiredField' not found in properties")
                }
            }
        }

        return errors
    }

    private companion object {
        private val INTEGER_TYPES = setOf(
            Int::class.javaObjectType,
            Int::class.javaPrimitiveType!!,
            Long::class.javaObjectType,
            Long::class.javaPrimitiveType!!
        )
        private val NUMBER_TYPES = setOf(
            Double::class.javaObjectType,
            Double::class.javaPrimitiveType!!,
            Float::class.javaObjectType,
            Float::class.javaPrimitiveType!!
        )
        private val BOOLEAN_TYPES = setOf(
            Boolean::class.javaObjectType,
            Boolean::class.javaPrimitiveType!!
        )
        private val ARRAY_LIKE_TYPES = setOf(List::class.java, Collection::class.java, Iterable::class.java)
    }
}




