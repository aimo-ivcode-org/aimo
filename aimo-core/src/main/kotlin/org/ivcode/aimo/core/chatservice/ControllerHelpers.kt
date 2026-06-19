package org.ivcode.aimo.core.chatservice

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

/**
 * Wrapper associating a tool callback with its scope restrictions.
 *
 * @property callback The actual tool callback (method invocation logic)
 * @property scopes Set of scope IDs this tool is available in (computed after validation).
 *                  Empty set means available to all scopes.
 */
data class ScopedToolCallback(
    val callback: AimoToolCallback,
    val scopes: Set<String>
)

/**
 * Wrapper associating a system message callback with its scope restrictions and name.
 *
 * @property callback The actual system message callback
 * @property name Stable identifier for this system message (explicit or auto-generated)
 * @property scopes Set of scope IDs this system message is available in (computed after validation).
 *                  Empty set means available to all scopes.
 */
data class ScopedSystemMessageCallbackWithName(
    val callback: SystemMessageCallback,
    val name: String,
    val scopes: Set<String>
)

/**
 * Compute actual scopes for a tool/system message based on parent service scopes and declared scopes.
 *
 * Rules:
 * - If declared scopes is empty: inherit parent scopes
 * - If declared scopes is non-empty: compute intersection with parent
 * - If intersection is empty: throw error (tool cannot be scoped)
 * - If declared scopes contain values outside parent: throw error (invalid scope reference)
 *
 * @param declaredScopes Scopes declared on the annotation
 * @param parentServiceScopes Scopes from parent @ChatService
 * @param componentName Name of component (tool/system message) for error messages
 * @return Actual scopes to use
 */
internal fun computeActualScopes(
    declaredScopes: Set<String>,
    parentServiceScopes: Set<String>,
    componentName: String
): Set<String> {
    if (parentServiceScopes.isEmpty()) {
        // Parent has no scope restrictions, use declared scopes as-is
        return declaredScopes
    }

    if (declaredScopes.isEmpty()) {
        // Empty declared = inherit parent scopes
        return parentServiceScopes
    }

    // Non-empty declared: validate subset and compute intersection
    val invalidScopes = declaredScopes - parentServiceScopes
    require(invalidScopes.isEmpty()) {
        "Component '$componentName' has scopes not in parent service: $invalidScopes. " +
        "Parent service scopes: $parentServiceScopes, component scopes: $declaredScopes"
    }

    // Compute intersection
    val intersection = declaredScopes.intersect(parentServiceScopes)
    require(intersection.isNotEmpty()) {
        "Component '$componentName' scopes $declaredScopes have zero intersection " +
        "with parent service scopes $parentServiceScopes"
    }

    return intersection
}

internal fun toAimoToolCallbacks(
    controller: Any,
    objectMapper: ObjectMapper,
    parentServiceScopes: Set<String> = emptySet()
): List<ScopedToolCallback> {
    val callbacks = mutableListOf<ScopedToolCallback>()
    var type: Class<*>? = controller::class.java

    while (type != null && type != Any::class.java) {
        type.declaredMethods
            .filter { it.isAnnotationPresent(Tool::class.java) }
            .forEach { method ->
                val toolAnnotation = method.getAnnotation(Tool::class.java)
                val declaredScopes = toolAnnotation.scope.toSet()
                val toolName = toolAnnotation.name.takeIf { it.isNotBlank() } ?: method.name

                // Compute actual scopes with validation
                val actualScopes = computeActualScopes(declaredScopes, parentServiceScopes, "tool '$toolName'")

                val callback = toAimoToolCallback(controller, method, objectMapper)
                callbacks += ScopedToolCallback(callback, actualScopes)
            }

        type = type.superclass
    }

    return callbacks
}

internal fun toAimoToolCallback(
    controller: Any,
    method: Method,
    objectMapper: ObjectMapper,
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
                createAimoToolParameterSchema(parameter, objectMapper)
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

private fun createAimoToolParameterSchema(parameter: KParameter, objectMapper: ObjectMapper): JsonNode {
    val type = parameter.type.javaType
    val rawClass = objectMapper.typeFactory.constructType(type).rawClass
    val schema = objectMapper.createObjectNode()
    schema.put("type", rawClass.toAimoJsonSchemaType())

    // Include description from @ToolParam annotation if present
    parameter.findAnnotation<ToolParam>()?.let { toolParam ->
        if (toolParam.description.isNotBlank()) {
            schema.put("description", toolParam.description)
        }
    }

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
 * annotated with @SystemMessage, along with their names and scope restrictions.
 *
 * Rules:
 * - Fields annotated with @SystemMessage will be used as-is (their toString() is returned).
 * - Kotlin properties annotated with @SystemMessage are supported.
 * - Methods annotated with @SystemMessage must return String? and either take no parameters
 *   or a single parameter of type SystemMessageContext.
 * - Names are extracted from @SystemMessage.name, or auto-generated from method/field/property name
 * - Scope restrictions are validated against parent @ChatService scopes
 * - Duplicate names within this controller will throw an error
 */
internal fun toSystemMessageCallbacks(
    controller: Any,
    parentServiceScopes: Set<String> = emptySet()
): List<ScopedSystemMessageCallbackWithName> {
    val callbacks = mutableListOf<ScopedSystemMessageCallbackWithName>()
    val observedNames = mutableSetOf<String>()
    val clazz = controller::class.java

    // Fields
    for (field in clazz.declaredFields) {
        if (field.isAnnotationPresent(SystemMessage::class.java)) {
            val annotation = field.getAnnotation(SystemMessage::class.java)
            
            // Extract or generate name
            val name = annotation.name.takeIf { it.isNotBlank() } ?: field.name
            require(!observedNames.contains(name)) {
                "Duplicate system message name '$name' in ${clazz.name}"
            }
            observedNames.add(name)
            
            // Validate and compute scopes
            val declaredScopes = annotation.scope.toSet()
            val actualScopes = computeActualScopes(declaredScopes, parentServiceScopes, "system message '$name'")
            
            // attempt to make field accessible; ignore failure (may be blocked by module system)
            trySetAccessible(field)
            callbacks += ScopedSystemMessageCallbackWithName(
                FieldSystemMessageCallback(controller, field),
                name,
                actualScopes
            )
        }
    }

    // Kotlin properties
    for (property in controller::class.memberProperties) {
        val annotation = property.findAnnotation<SystemMessage>() ?: continue
        
        // Extract or generate name
        val name = annotation.name.takeIf { it.isNotBlank() } ?: property.name
        require(!observedNames.contains(name)) {
            "Duplicate system message name '$name' in ${clazz.name}"
        }
        observedNames.add(name)
        
        // Validate and compute scopes
        val declaredScopes = annotation.scope.toSet()
        val actualScopes = computeActualScopes(declaredScopes, parentServiceScopes, "system message '$name'")
        
        callbacks += ScopedSystemMessageCallbackWithName(
            PropertySystemMessageCallback(controller, property),
            name,
            actualScopes
        )
    }

    // Methods
    for (method in clazz.declaredMethods) {
        if (!method.isAnnotationPresent(SystemMessage::class.java)) continue

        val annotation = method.getAnnotation(SystemMessage::class.java)
        
        // Extract or generate name
        val name = annotation.name.takeIf { it.isNotBlank() } ?: method.name
        require(!observedNames.contains(name)) {
            "Duplicate system message name '$name' in ${clazz.name}"
        }
        observedNames.add(name)

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

        // Validate and compute scopes
        val declaredScopes = annotation.scope.toSet()
        val actualScopes = computeActualScopes(declaredScopes, parentServiceScopes, "system message '$name'")

        callbacks += ScopedSystemMessageCallbackWithName(
            MethodSystemMessageCallback(controller, method, isContextual),
            name,
            actualScopes
        )
    }

    return callbacks
}

