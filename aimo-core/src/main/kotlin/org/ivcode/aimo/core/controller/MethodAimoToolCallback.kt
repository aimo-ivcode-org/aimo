package org.ivcode.aimo.core.controller

import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.model.AimoToolDefinition
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.lang.reflect.Method
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

/**
 * Reflection-backed [AimoToolCallback] implementation for a single Kotlin tool method.
 *
 * This class is intentionally isolated from current controller discovery/integration code.
 * It only knows how to:
 * - invoke one annotated tool method on a target instance,
 * - bind JSON object fields onto method parameters by parameter name,
 * - inject the supplied runtime [context] map into a `context: Map<String, Any>` parameter,
 * - return raw `String` results unchanged,
 * - serialize any non-string result back to JSON using Jackson.
 *
 * The internal [ObjectMapper] is configured with the Kotlin module, enabling reliable
 * deserialization of Kotlin types including data classes, non-null properties, and default values.
 *
 * Notes for future integration work:
 * - Argument binding currently expects a JSON object payload for named parameters.
 * - A context parameter is recognized only when the parameter name is `context` and its raw type is [Map].
 */
class MethodAimoToolCallback(
    private val target: Any,
    private val method: Method,
    override val toolDefinition: AimoToolDefinition,
    private val objectMapper: ObjectMapper,
) : AimoToolCallback {

    private val function = method.kotlinFunction
        ?: throw IllegalArgumentException(
            "Method ${method.name} on ${method.declaringClass.name} is not a Kotlin function"
        )

    init {
        require(method.isAnnotationPresent(Tool::class.java)) {
            "Method ${method.name} on ${method.declaringClass.name} must be annotated with @Tool"
        }
        trySetAccessible(method)
        function.isAccessible = true
    }

    /**
     * Executes the backing tool method for the given JSON arguments and runtime context.
     *
     * Behavior:
     * - Parses [argumentsJson] into a JSON tree (blank input is treated as `{}`).
     * - Binds named JSON fields to method parameters.
     * - Injects [context] into the recognized context parameter.
     * - Returns string results directly; serializes any non-string result to JSON.
     */
    override fun call(argumentsJson: String, context: Map<String, Any>): String {
        val argumentsNode = parseArguments(argumentsJson)
        val invocationArguments = buildInvocationArguments(argumentsNode, context)
        val result = function.callBy(invocationArguments)

        return when (result) {
            is String -> result
            else -> objectMapper.writeValueAsString(result)
        }
    }

    /**
     * Parses the raw arguments payload into a JSON node.
     *
     * A blank payload is normalized to an empty JSON object so parameter binding can
     * proceed with standard "missing argument" rules.
     */
    private fun parseArguments(argumentsJson: String): JsonNode {
        if (argumentsJson.isBlank()) {
            return objectMapper.createObjectNode()
        }
        return objectMapper.readTree(argumentsJson)
    }

    /**
     * Builds reflective invocation arguments from parsed JSON and context.
     *
     * Rules:
     * - The instance parameter is always bound to [target].
     * - A recognized context parameter receives [context].
     * - Remaining parameters are bound by parameter name from [argumentsNode].
     * - Missing required arguments and invalid nulls fail fast with
     *   [IllegalArgumentException].
     */
    private fun buildInvocationArguments(
        argumentsNode: JsonNode,
        context: Map<String, Any>,
    ): Map<KParameter, Any?> {
        val invocationArguments = linkedMapOf<KParameter, Any?>()

        function.instanceParameter?.let { invocationArguments[it] = target }

        val valueParameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
        val expectsJsonObject = valueParameters.any { !isContextParameter(it) }
        if (expectsJsonObject && !argumentsNode.isObject) {
            throw IllegalArgumentException(
                "Tool '${toolDefinition.name}' expects JSON object arguments, but received ${argumentsNode.nodeType}"
            )
        }

        valueParameters.forEach { parameter ->
            if (isContextParameter(parameter)) {
                invocationArguments[parameter] = context
                return@forEach
            }

            val parameterName = parameter.name
                ?: throw IllegalStateException(
                    "Tool parameter names are required for method ${method.name} on ${method.declaringClass.name}"
                )

            val jsonValue = argumentsNode.get(parameterName)
            if (jsonValue == null || jsonValue.isMissingNode) {
                when {
                    parameter.isOptional -> return@forEach
                    parameter.type.isMarkedNullable -> {
                        invocationArguments[parameter] = null
                        return@forEach
                    }
                    else -> throw IllegalArgumentException(
                        "Missing required tool argument '$parameterName' for tool '${toolDefinition.name}'"
                    )
                }
            }

            if (jsonValue.isNull) {
                if (!parameter.type.isMarkedNullable) {
                    throw IllegalArgumentException(
                        "Tool argument '$parameterName' for tool '${toolDefinition.name}' cannot be null"
                    )
                }
                invocationArguments[parameter] = null
                return@forEach
            }

            invocationArguments[parameter] = objectMapper.convertValue(
                jsonValue,
                objectMapper.typeFactory.constructType(parameter.type.javaType)
            )
        }

        return invocationArguments
    }

    /**
     * Returns true when this parameter is the runtime tool context parameter.
     *
     * The parameter must be named `context` and have a raw type assignable to [Map].
     */
    private fun isContextParameter(parameter: KParameter): Boolean {
        val rawClass = objectMapper.typeFactory.constructType(parameter.type.javaType).rawClass
        return parameter.name == CONTEXT_PARAMETER_NAME && Map::class.java.isAssignableFrom(rawClass)
    }

    companion object {
        private const val CONTEXT_PARAMETER_NAME = "context"

        /**
         * Creates a [MethodAimoToolCallback] from an annotated tool method.
         *
         * The factory derives [AimoToolDefinition] metadata from the method's [Tool]
         * annotation and builds a basic JSON Schema object for the non-context
         * parameters expected by the tool.
         */
        fun create (
            target: Any,
            method: Method,
            objectMapper: ObjectMapper,
        ): MethodAimoToolCallback = toAimoToolCallback(target, method, objectMapper)
    }
}

