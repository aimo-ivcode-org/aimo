package org.ivcode.aimo.server.mcp.validation

import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.annotation.McpContext
import java.lang.reflect.Parameter

/**
 * Validates tool/prompt parameters before invocation.
 */
class ParameterValidator {
    /**
     * Validate parameters against method signature and annotations.
     *
     * @param methodParameters reflected parameters declared by the method.
     * @param providedArguments request arguments keyed by parameter name.
     * @return validation result describing whether the payload is valid.
     */
    fun validateParameters(
        methodParameters: Array<Parameter>,
        providedArguments: Map<String, Any?>
    ): ValidationResult {
        val errors = mutableListOf<String>()

        for (parameter in methodParameters.filterNot(::isContextParameter)) {
            // Record required-field violations before attempting type checks.
            validateRequiredParameter(parameter, providedArguments)?.let { error ->
                errors += error
            }

            // Validate supplied values only when the client actually sent them.
            validateProvidedParameter(parameter, providedArguments)?.let { error ->
                errors += error
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
            return null
        }

        return when {
            paramType.isAssignableFrom(value.javaClass) -> null
            paramType == String::class.java -> null
            paramType in NUMERIC_TYPES -> validateNumericType(paramName, value)
            paramType in BOOLEAN_TYPES -> validateBooleanType(paramName, value)
            isCollectionType(paramType) -> validateCollectionType(paramName, value)
            paramType.isAssignableFrom(Map::class.java) -> validateMapType(paramName, value)
            else -> null
        }
    }

    /**
     * Validate that a required parameter is present.
     *
     * @param parameter reflected parameter metadata.
     * @param providedArguments request arguments keyed by parameter name.
     * @return validation error when the parameter is required but missing.
     */
    private fun validateRequiredParameter(
        parameter: Parameter,
        providedArguments: Map<String, Any?>
    ): String? {
        val isRequired = parameter.getAnnotation(McpParam::class.java)?.required != false
        return if (isRequired && !providedArguments.containsKey(parameter.name)) {
            "Required parameter '${parameter.name}' is missing"
        } else {
            null
        }
    }

    /**
     * Validate a provided parameter value when one exists.
     *
     * @param parameter reflected parameter metadata.
     * @param providedArguments request arguments keyed by parameter name.
     * @return validation error when the supplied value is incompatible.
     */
    private fun validateProvidedParameter(
        parameter: Parameter,
        providedArguments: Map<String, Any?>
    ): String? {
        val value = providedArguments[parameter.name]
        return if (providedArguments.containsKey(parameter.name) && value != null) {
            validateParameterType(parameter.name, value, parameter.type)
        } else {
            null
        }
    }

    /**
     * Determine whether the parameter is an injected request-context parameter.
     *
     * @param parameter reflected parameter metadata.
     * @return true when the parameter should be skipped by client-side validation.
     */
    private fun isContextParameter(parameter: Parameter): Boolean {
        return parameter.getAnnotation(McpContext::class.java) != null
    }

    /**
     * Validate numeric parameter compatibility.
     *
     * @param paramName parameter name for diagnostics.
     * @param value supplied value.
     * @return validation error when the value is not numeric.
     */
    private fun validateNumericType(paramName: String, value: Any): String? {
        return when (value) {
            is Number -> null
            is String -> if (value.toDoubleOrNull() != null) null else "Parameter '$paramName' must be a number"
            else -> "Parameter '$paramName' must be numeric (got ${value.javaClass.simpleName})"
        }
    }

    /**
     * Validate boolean parameter compatibility.
     *
     * @param paramName parameter name for diagnostics.
     * @param value supplied value.
     * @return validation error when the value is not boolean-compatible.
     */
    private fun validateBooleanType(paramName: String, value: Any): String? {
        return when (value) {
            is Boolean -> null
            is String -> if (value.equals("true", true) || value.equals("false", true)) {
                null
            } else {
                "Parameter '$paramName' must be a boolean (got '$value')"
            }
            else -> "Parameter '$paramName' must be boolean (got ${value.javaClass.simpleName})"
        }
    }

    /**
     * Validate collection parameter compatibility.
     *
     * @param paramName parameter name for diagnostics.
     * @param value supplied value.
     * @return validation error when the value is not collection-compatible.
     */
    private fun validateCollectionType(paramName: String, value: Any): String? {
        return if (value is List<*> || value is Collection<*>) {
            null
        } else {
            "Parameter '$paramName' must be a list (got ${value.javaClass.simpleName})"
        }
    }

    /**
     * Validate map parameter compatibility.
     *
     * @param paramName parameter name for diagnostics.
     * @param value supplied value.
     * @return validation error when the value is not map-compatible.
     */
    private fun validateMapType(paramName: String, value: Any): String? {
        return if (value is Map<*, *>) {
            null
        } else {
            "Parameter '$paramName' must be a map (got ${value.javaClass.simpleName})"
        }
    }

    /**
     * Determine whether the target type expects a collection-like value.
     *
     * @param paramType target parameter type.
     * @return true when the parameter expects a list, collection, or iterable.
     */
    private fun isCollectionType(paramType: Class<*>): Boolean {
        return paramType.isAssignableFrom(List::class.java) ||
            paramType.isAssignableFrom(Collection::class.java) ||
            paramType.isAssignableFrom(Iterable::class.java)
    }

    /**
     * Validate that all required fields are present in a parameter map.
     */
    fun validateRequiredFields(
        methodParameters: Array<Parameter>,
        providedArguments: Map<String, Any?>
    ): List<String> {
        val missingFields = mutableListOf<String>()

        for (parameter in methodParameters.filterNot(::isContextParameter)) {
            if (parameter.getAnnotation(McpParam::class.java)?.required != false &&
                !providedArguments.containsKey(parameter.name)
            ) {
                missingFields.add(parameter.name)
            }
        }

        return missingFields
    }

    private companion object {
        private val NUMERIC_TYPES = setOf(
            Int::class.javaObjectType,
            Int::class.javaPrimitiveType!!,
            Long::class.javaObjectType,
            Long::class.javaPrimitiveType!!,
            Double::class.javaObjectType,
            Double::class.javaPrimitiveType!!,
            Float::class.javaObjectType,
            Float::class.javaPrimitiveType!!,
            Number::class.java
        )
        private val BOOLEAN_TYPES = setOf(
            Boolean::class.javaObjectType,
            Boolean::class.javaPrimitiveType!!
        )
    }
}

/**
 * Result of parameter validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)


