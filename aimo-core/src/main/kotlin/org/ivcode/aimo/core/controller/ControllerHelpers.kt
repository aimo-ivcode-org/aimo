package org.ivcode.aimo.core.controller

import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.model.AimoToolDefinition
import org.ivcode.aimo.core.model.DEFAULT_JSON_SCHEMA_DIALECT
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.lang.reflect.Method
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction


internal fun toAimoToolCallbacks(
    controller: Any,
    objectMapper: ObjectMapper = ObjectMapper(),
): List<AimoToolCallback> {
    val callbacks = mutableListOf<AimoToolCallback>()
    var type: Class<*>? = controller::class.java

    while (type != null && type != Any::class.java) {
        type.declaredMethods
            .filter { it.isAnnotationPresent(Tool::class.java) }
            .forEach { method ->
                callbacks += toAimoToolCallback(controller, method, objectMapper)
            }

        type = type.superclass
    }

    return callbacks
}

internal fun toAimoToolCallback(
    controller: Any,
    method: Method,
    objectMapper: ObjectMapper = ObjectMapper(),
): MethodAimoToolCallback {
    val tool = method.getAnnotation(Tool::class.java)
        ?: throw IllegalArgumentException(
            "Method ${method.name} on ${method.declaringClass.name} must be annotated with @Tool"
        )

    return MethodAimoToolCallback(
        target = controller,
        method = method,
        toolDefinition = AimoToolDefinition(
            name = tool.name.takeIf { it.isNotBlank() } ?: method.name,
            description = tool.description.takeIf { it.isNotBlank() },
            inputSchema = createAimoToolInputSchema(method, objectMapper),
            schemaDialect = DEFAULT_JSON_SCHEMA_DIALECT,
        ),
        objectMapper = objectMapper,
    )
}

private fun createAimoToolInputSchema(method: Method, objectMapper: ObjectMapper): JsonNode {
    val function = method.kotlinFunction
        ?: throw IllegalArgumentException(
            "Method ${method.name} on ${method.declaringClass.name} is not a Kotlin function"
        )

    val schema = objectMapper.createObjectNode()
    val properties = objectMapper.createObjectNode()
    val required = objectMapper.createArrayNode()

    function.parameters
        .filter { it.kind == KParameter.Kind.VALUE }
        .filterNot { isAimoToolContextParameter(it, objectMapper) }
        .forEach { parameter ->
            val parameterName = parameter.name
                ?: throw IllegalStateException(
                    "Tool parameter names are required for method ${method.name} on ${method.declaringClass.name}"
                )

            properties.set(
                parameterName,
                createAimoToolParameterSchema(parameter.type.javaType, objectMapper)
            )

            if (!parameter.isOptional && !parameter.type.isMarkedNullable) {
                required.add(parameterName)
            }
        }

    schema.put("type", "object")
    schema.set("properties", properties)
    if (required.size() > 0) {
        schema.set("required", required)
    }
    return schema
}

private fun isAimoToolContextParameter(parameter: KParameter, objectMapper: ObjectMapper): Boolean {
    val rawClass = objectMapper.typeFactory.constructType(parameter.type.javaType).rawClass
    return parameter.name == "context" && Map::class.java.isAssignableFrom(rawClass)
}

private fun createAimoToolParameterSchema(type: java.lang.reflect.Type, objectMapper: ObjectMapper): JsonNode {
    val rawClass = objectMapper.typeFactory.constructType(type).rawClass
    val schema = objectMapper.createObjectNode()
    schema.put("type", rawClass.toAimoJsonSchemaType())
    return schema
}

private fun Class<*>.toAimoJsonSchemaType(): String {
    return when {
        this == String::class.java || this == Char::class.java || this.isEnum -> "string"
        this == Boolean::class.java || this == java.lang.Boolean::class.java -> "boolean"
        this == Int::class.java || this == java.lang.Integer::class.java ||
            this == Long::class.java || this == java.lang.Long::class.java ||
            this == Short::class.java || this == java.lang.Short::class.java ||
            this == Byte::class.java || this == java.lang.Byte::class.java -> "integer"
        this == Float::class.java || this == java.lang.Float::class.java ||
            this == Double::class.java || this == java.lang.Double::class.java -> "number"
        this.isArray || Iterable::class.java.isAssignableFrom(this) -> "array"
        Map::class.java.isAssignableFrom(this) -> "object"
        else -> "object"
    }
}

/**
 * Scan the given controller instance and return callbacks for fields and methods
 * annotated with @SystemMessage.
 *
 * Rules:
 * - Fields annotated with @SystemMessage will be used as-is (their toString() is returned).
 * - Kotlin properties annotated with @SystemMessage are supported.
 * - Methods annotated with @SystemMessage must return String? and either take no parameters
 *   or a single parameter of type SystemMessageContext.
 */
internal fun toSystemMessageCallbacks(controller: Any): List<SystemMessageCallback> {
    val callbacks = mutableListOf<SystemMessageCallback>()
    val clazz = controller::class.java

    // Fields
    for (field in clazz.declaredFields) {
        if (field.isAnnotationPresent(SystemMessage::class.java)) {
            // attempt to make field accessible; ignore failure (may be blocked by module system)
            trySetAccessible(field)
            callbacks += FieldSystemMessageCallback(controller, field)
        }
    }

    // Kotlin properties
    for (property in controller::class.memberProperties) {
        if (property.findAnnotation<SystemMessage>() == null) continue
        callbacks += PropertySystemMessageCallback(controller, property)
    }

    // Methods
    for (method in clazz.declaredMethods) {
        if (!method.isAnnotationPresent(SystemMessage::class.java)) continue

        // Validate return type: allow java.lang.String or kotlin.String (both are java.lang.String), and allow nullable
        val returnType = method.returnType
        if (returnType != String::class.java) {
            // not a String return type - skip
            continue
        }

        val params = method.parameterTypes
        val isContextual = when (params.size) {
            0 -> false
            1 -> {
                if (params[0] != SystemMessageContext::class.java) {
                    throw IllegalStateException("Method ${method.name} in ${clazz.name} annotated with @SystemMessage has invalid parameters. Must have either no parameters or a single parameter of type SystemMessageContext.")
                }
                true
            }
            else -> {
                // invalid signature
                throw IllegalStateException("Method ${method.name} in ${clazz.name} annotated with @SystemMessage has invalid parameters. Must have either no parameters or a single parameter of type SystemMessageContext.")
            }
        }

        // attempt to make method accessible; if it fails we will surface a clearer error when invoking
        trySetAccessible(method)

        callbacks += MethodSystemMessageCallback(controller, method, isContextual)
    }

    return callbacks
}
