package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.annotation.McpContext
import org.ivcode.aimo.server.mcp.annotation.McpParam
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.kotlinFunction

/**
 * Exception thrown when request parameter binding fails.
 *
 * Owns the parameter name, expected type, and original value so callers can
 * return precise MCP validation errors to clients.
 *
 * @property parameterName bound parameter name or a placeholder when unknown.
 * @property expectedType target JVM type for the parameter.
 * @property providedValue source value from the request payload.
 */
class ParameterBindingException(
    val parameterName: String,
    val expectedType: Class<*>,
    val providedValue: Any?,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Binds request arguments to reflected method parameters.
 *
 * Preserves Kotlin parameter names as a fallback when Java reflection exposes
 * synthetic names and performs the small set of scalar conversions supported by
 * the MCP request layer.
 */
class ParameterBinder(
    private val objectMapper: ObjectMapper
) {
    /**
     * Result of binding request arguments to method parameters.
     *
     * @property values resolved values keyed by parameter name.
     * @property provided names explicitly supplied by the client.
     * @property context request context injected into @McpContext parameters.
     */
    data class BindingResult(
        val values: Map<String, Any?>,
        val provided: Set<String>,
        val context: Map<String, Any?>
    )

    /**
     * Bind request arguments to a reflected method.
     *
     * @param method reflected method whose parameters should be populated.
     * @param arguments client-supplied arguments keyed by parameter name.
     * @param context request context injected into @McpContext parameters.
     * @return binding result containing resolved values and provenance metadata.
     * @throws ParameterBindingException when a required parameter is missing or a conversion fails.
     */
    fun bindParameters(
        method: Method,
        arguments: Map<String, Any?>,
        context: Map<String, Any?>
    ): BindingResult {
        val values = mutableMapOf<String, Any?>()
        val provided = mutableSetOf<String>()
        val kotlinFunction = method.kotlinFunction
        val kotlinParameterNames = kotlinFunction
            ?.parameters
            ?.filter { parameter -> parameter != kotlinFunction.instanceParameter }
            ?.map { parameter -> parameter.name }
            ?: emptyList()

        for ((index, parameter) in method.parameters.withIndex()) {
            // Resolve the best available parameter name before any validation.
            val name = resolveParameterName(parameter, kotlinParameterNames, index)
                ?: throw ParameterBindingException(
                    parameterName = "<unknown>",
                    expectedType = parameter.type,
                    providedValue = null,
                    message = "Unable to bind parameter with no name"
                )

            // Inject request context directly for @McpContext parameters.
            if (parameter.getAnnotation(McpContext::class.java) != null) {
                values[name] = context
                provided += name
                continue
            }

            // Validate required parameters before attempting type conversion.
            val isProvided = arguments.containsKey(name)
            val isOptional = parameter.getAnnotation(McpParam::class.java)?.required == false
            if (!isOptional && !isProvided) {
                throw ParameterBindingException(
                    parameterName = name,
                    expectedType = parameter.type,
                    providedValue = null,
                    message = "Missing required parameter '$name'"
                )
            }

            val convertedValue = convertParameterValue(name, parameter, arguments[name])
            values[name] = convertedValue
            if (isProvided) {
                provided += name
            }
        }

        return BindingResult(values = values, provided = provided, context = context)
    }

    /**
     * Resolve the parameter name that should be used for binding.
     *
     * @param parameter reflected parameter to inspect.
     * @param kotlinParameterNames Kotlin metadata names aligned to JVM parameter order.
     * @param index parameter index in the reflected method.
     * @return stable parameter name when one can be determined.
     */
    private fun resolveParameterName(
        parameter: Parameter,
        kotlinParameterNames: List<String?>,
        index: Int
    ): String? {
        val javaName = parameter.name
        return if (javaName == null || javaName.matches(SYNTHETIC_PARAMETER_NAME)) {
            kotlinParameterNames.getOrNull(index) ?: javaName
        } else {
            javaName
        }
    }

    /**
     * Convert a single argument value to the requested JVM type.
     *
     * @param name resolved parameter name.
     * @param parameter reflected parameter metadata.
     * @param value source value supplied by the client.
     * @return converted value or null when the argument is omitted.
     * @throws ParameterBindingException when conversion fails.
     */
    private fun convertParameterValue(
        name: String,
        parameter: Parameter,
        value: Any?
    ): Any? {
        if (value == null) {
            return null
        }

        val type = parameter.type
        return when {
            type.isAssignableFrom(value.javaClass) -> value
            type == String::class.java -> value.toString()
            type in INTEGER_TYPES -> convertInteger(name, type, value)
            type == Long::class.java || type == java.lang.Long.TYPE -> convertLong(name, type, value)
            type in DECIMAL_TYPES -> convertDecimal(name, type, value)
            type in BOOLEAN_TYPES -> convertBoolean(name, type, value)
            else -> convertWithObjectMapper(name, type, value)
        }
    }

    /**
     * Convert an integer-compatible value.
     *
     * @param name parameter name for diagnostics.
     * @param type target integer type.
     * @param value source value.
     * @return converted integer value.
     */
    private fun convertInteger(name: String, type: Class<*>, value: Any): Any {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
                ?: throw parameterBindingException(
                    name = name,
                    type = type,
                    value = value,
                    message = "Parameter '$name' expects an integer but got invalid value: '$value'"
                )
            else -> throw parameterBindingException(
                name = name,
                type = type,
                value = value,
                message = "Parameter '$name' expects an integer but got ${value.javaClass.simpleName}: $value"
            )
        }
    }

    /**
     * Convert a long-compatible value.
     *
     * @param name parameter name for diagnostics.
     * @param type target long type.
     * @param value source value.
     * @return converted long value.
     */
    private fun convertLong(name: String, type: Class<*>, value: Any): Any {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
                ?: throw parameterBindingException(
                    name = name,
                    type = type,
                    value = value,
                    message = "Parameter '$name' expects a long but got invalid value: '$value'"
                )
            else -> throw parameterBindingException(
                name = name,
                type = type,
                value = value,
                message = "Parameter '$name' expects a long but got ${value.javaClass.simpleName}: $value"
            )
        }
    }

    /**
     * Convert a floating-point-compatible value.
     *
     * @param name parameter name for diagnostics.
     * @param type target floating-point type.
     * @param value source value.
     * @return converted float or double value.
     */
    private fun convertDecimal(name: String, type: Class<*>, value: Any): Any {
        return when (value) {
            is Number -> if (type in FLOAT_TYPES) value.toFloat() else value.toDouble()
            is String -> {
                val converted = if (type in FLOAT_TYPES) value.toFloatOrNull() else value.toDoubleOrNull()
                converted ?: throw parameterBindingException(
                    name = name,
                    type = type,
                    value = value,
                    message = if (type in FLOAT_TYPES) {
                        "Parameter '$name' expects a decimal number (float) but got invalid value: '$value'"
                    } else {
                        "Parameter '$name' expects a decimal number (double) but got invalid value: '$value'"
                    }
                )
            }
            else -> throw parameterBindingException(
                name = name,
                type = type,
                value = value,
                message = "Parameter '$name' expects a decimal number but got ${value.javaClass.simpleName}: $value"
            )
        }
    }

    /**
     * Convert a boolean-compatible value.
     *
     * @param name parameter name for diagnostics.
     * @param type target boolean type.
     * @param value source value.
     * @return converted boolean value.
     */
    private fun convertBoolean(name: String, type: Class<*>, value: Any): Any {
        return when (value) {
            is Boolean -> value
            is String -> BOOLEAN_STRING_VALUES[value.trim().lowercase()]
                ?: throw parameterBindingException(
                    name = name,
                    type = type,
                    value = value,
                    message = (
                        "Parameter '$name' expects a boolean " +
                            "(true/false or yes/no/1/0/on/off) but got: '$value'"
                        )
                )
            else -> throw parameterBindingException(
                name = name,
                type = type,
                value = value,
                message = "Parameter '$name' expects a boolean but got ${value.javaClass.simpleName}: $value"
            )
        }
    }

    /**
     * Convert a value through Jackson for complex target types.
     *
     * @param name parameter name for diagnostics.
     * @param type target JVM type.
     * @param value source value.
     * @return converted object.
     */
    private fun convertWithObjectMapper(name: String, type: Class<*>, value: Any): Any {
        return try {
            objectMapper.convertValue(value, type)
        } catch (exception: IllegalArgumentException) {
            throw parameterBindingException(
                name = name,
                type = type,
                value = value,
                message = "Could not convert parameter '$name' to type ${type.simpleName}: ${exception.message}",
                cause = exception
            )
        }
    }

    /**
     * Create a binding exception with consistent metadata.
     *
     * @param name parameter name.
     * @param type target JVM type.
     * @param value original client value.
     * @param message human-readable validation error.
     * @param cause optional conversion cause.
     * @return parameter binding exception instance.
     */
    private fun parameterBindingException(
        name: String,
        type: Class<*>,
        value: Any?,
        message: String,
        cause: Throwable? = null
    ): ParameterBindingException {
        return ParameterBindingException(
            parameterName = name,
            expectedType = type,
            providedValue = value,
            message = message,
            cause = cause
        )
    }

    private companion object {
        private val SYNTHETIC_PARAMETER_NAME = Regex("arg\\d+")
        private val INTEGER_TYPES = setOf(Int::class.javaObjectType, Int::class.javaPrimitiveType!!)
        private val FLOAT_TYPES = setOf(Float::class.javaObjectType, Float::class.javaPrimitiveType!!)
        private val DECIMAL_TYPES = setOf(
            Double::class.javaObjectType,
            Double::class.javaPrimitiveType!!,
            Float::class.javaObjectType,
            Float::class.javaPrimitiveType!!
        )
        private val BOOLEAN_TYPES = setOf(Boolean::class.javaObjectType, Boolean::class.javaPrimitiveType!!)
        private val BOOLEAN_STRING_VALUES = mapOf(
            "true" to true,
            "false" to false,
            "yes" to true,
            "no" to false,
            "1" to true,
            "0" to false,
            "on" to true,
            "off" to false
        )
    }
}



