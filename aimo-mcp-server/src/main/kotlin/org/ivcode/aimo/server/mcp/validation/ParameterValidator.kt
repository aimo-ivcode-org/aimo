package org.ivcode.aimo.server.mcp.validation

import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.reflect.Parameter

/**
 * Validates tool/prompt parameters before invocation.
 */
class ParameterValidator {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Validate parameters against method signature and annotations.
     */
    fun validateParameters(
        methodName: String,
        methodParameters: Array<Parameter>,
        providedArguments: Map<String, Any?>
    ): ValidationResult {
        val errors = mutableListOf<String>()

        for (param in methodParameters) {
            // Skip context parameters
            if (param.getAnnotation(org.ivcode.aimo.server.mcp.annotation.McpContext::class.java) != null) {
                continue
            }

            val mcpParam = param.getAnnotation(McpParam::class.java)
            val isRequired = mcpParam?.required != false

            // Check required parameters
            if (isRequired && !providedArguments.containsKey(param.name)) {
                errors.add("Required parameter '${param.name}' is missing")
                continue
            }

            // Skip if not provided and optional
            if (!providedArguments.containsKey(param.name)) {
                continue
            }

            val value = providedArguments[param.name]

            // Validate type if value is provided
            if (value != null) {
                val typeError = validateParameterType(param.name, value, param.type)
                if (typeError != null) {
                    errors.add(typeError)
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate parameter type.
     */
    private fun validateParameterType(paramName: String, value: Any?, paramType: Class<*>): String? {
        if (value == null) {
            return null  // Null is acceptable for optional parameters
        }

        // Check for compatible types
        return when {
            paramType.isAssignableFrom(value.javaClass) -> null

            // String type - accepts anything (will be converted to string)
            paramType == String::class.java -> null

            // Numeric types
            paramType in listOf(Int::class.java, Integer::class.java, Long::class.java,
                               Double::class.java, Float::class.java, Number::class.java) -> {
                when (value) {
                    is Number -> null
                    is String -> {
                        // Try to parse as number
                        try {
                            value.toDouble()
                            null
                        } catch (e: NumberFormatException) {
                            "Parameter '$paramName' must be a number"
                        }
                    }
                    else -> "Parameter '$paramName' must be numeric (got ${value.javaClass.simpleName})"
                }
            }

            // Boolean type
            paramType in listOf(Boolean::class.java, java.lang.Boolean::class.java) -> {
                when (value) {
                    is Boolean -> null
                    is String -> {
                        if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
                            null
                        } else {
                            "Parameter '$paramName' must be a boolean (got '$value')"
                        }
                    }
                    else -> "Parameter '$paramName' must be boolean (got ${value.javaClass.simpleName})"
                }
            }

            // List/Collection types
            paramType.isAssignableFrom(List::class.java) ||
            paramType.isAssignableFrom(Collection::class.java) ||
            paramType.isAssignableFrom(Iterable::class.java) -> {
                if (value is List<*> || value is Collection<*>) null
                else "Parameter '$paramName' must be a list (got ${value.javaClass.simpleName})"
            }

            // Map type
            paramType.isAssignableFrom(Map::class.java) -> {
                if (value is Map<*, *>) null
                else "Parameter '$paramName' must be a map (got ${value.javaClass.simpleName})"
            }

            else -> null  // Unknown types are converted best-effort
        }
    }

    /**
     * Validate that all required fields are present in a parameter map.
     */
    fun validateRequiredFields(
        methodParameters: Array<Parameter>,
        providedArguments: Map<String, Any?>
    ): List<String> {
        val missingFields = mutableListOf<String>()

        for (param in methodParameters) {
            // Skip context parameters
            if (param.getAnnotation(org.ivcode.aimo.server.mcp.annotation.McpContext::class.java) != null) {
                continue
            }

            val mcpParam = param.getAnnotation(McpParam::class.java)
            if (mcpParam?.required != false && !providedArguments.containsKey(param.name)) {
                missingFields.add(param.name)
            }
        }

        return missingFields
    }
}

/**
 * Result of parameter validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)


