@file:Suppress("TooManyFunctions") // Utility module for controller discovery with multiple related helpers

package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
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
): Set<String> = when {
    parentServiceScopes.isEmpty() -> declaredScopes
    declaredScopes.isEmpty() -> parentServiceScopes
    else -> {
        // Non-empty declared: validate subset and compute intersection
        val invalidScopes = declaredScopes - parentServiceScopes
        require(invalidScopes.isEmpty()) {
            "Component '$componentName' has scopes not in parent service: $invalidScopes. " +
            "Parent service scopes: $parentServiceScopes, component scopes: $declaredScopes"
        }

        val intersection = declaredScopes.intersect(parentServiceScopes)
        require(intersection.isNotEmpty()) {
            "Component '$componentName' scopes $declaredScopes have zero intersection " +
            "with parent service scopes $parentServiceScopes"
        }

        intersection
    }
}

internal fun toToolCallbacks(
    controller: Any,
    objectMapper: ObjectMapper,
    parentServiceScopes: Set<String> = emptySet()
): List<ToolCallback> {
    val callbacks = mutableListOf<ToolCallback>()
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

                val callback = toToolCallback(controller, method, objectMapper, actualScopes)
                callbacks += callback
            }

        type = type.superclass
    }

    return callbacks
}

internal fun toToolCallback(
    controller: Any,
    method: Method,
    objectMapper: ObjectMapper,
    scopes: Set<String> = emptySet(),
): MethodToolCallback {
    val tool = method.getAnnotation(Tool::class.java)
        ?: throw IllegalArgumentException(
            "Method ${method.name} on ${method.declaringClass.name} must be annotated with @Tool"
        )

    return MethodToolCallback(
        target = controller,
        method = method,
        toolDefinition = ToolDefinition(
            name = tool.name.takeIf { it.isNotBlank() } ?: method.name,
            description = tool.description.takeIf { it.isNotBlank() },
            inputSchema = createToolInputSchema(method, objectMapper),
            schemaDialect = DEFAULT_JSON_SCHEMA_DIALECT,
        ),
        scopes = scopes,
        objectMapper = objectMapper,
    )
}

private fun createToolInputSchema(method: Method, objectMapper: ObjectMapper): JsonNode {
    val function = method.kotlinFunction
        ?: throw IllegalArgumentException(
            "Method ${method.name} on ${method.declaringClass.name} is not a Kotlin function"
        )

    val schema = objectMapper.createObjectNode()
    val properties = objectMapper.createObjectNode()
    val required = objectMapper.createArrayNode()

    function.parameters
        .filter { it.kind == KParameter.Kind.VALUE }
        .filterNot { isToolContextParameter(it, objectMapper) }
        .forEach { parameter ->
            val parameterName = parameter.name
                ?: throw IllegalStateException(
                    "Tool parameter names are required for method ${method.name} on ${method.declaringClass.name}"
                )

            properties.set(
                parameterName,
                createToolParameterSchema(parameter, objectMapper)
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

private fun isToolContextParameter(parameter: KParameter, objectMapper: ObjectMapper): Boolean {
    val rawClass = objectMapper.typeFactory.constructType(parameter.type.javaType).rawClass
    return parameter.name == "context" && Map::class.java.isAssignableFrom(rawClass)
}

private fun createToolParameterSchema(parameter: KParameter, objectMapper: ObjectMapper): JsonNode {
    val type = parameter.type.javaType
    val rawClass = objectMapper.typeFactory.constructType(type).rawClass
    val schema = objectMapper.createObjectNode()
    schema.put("type", rawClass.toJsonSchemaType())

    // Include description from @ToolParam annotation if present
    parameter.findAnnotation<ToolParam>()?.let { toolParam ->
        if (toolParam.description.isNotBlank()) {
            schema.put("description", toolParam.description)
        }
    }

    return schema
}

private fun Class<*>.toJsonSchemaType(): String {
    return when {
        isStringType() -> "string"
        isBooleanType() -> "boolean"
        isIntegerType() -> "integer"
        isNumberType() -> "number"
        isArrayType() -> "array"
        Map::class.java.isAssignableFrom(this) -> "object"
        else -> "object"
    }
}

private fun Class<*>.isStringType(): Boolean =
    this == String::class.java || this == Char::class.java || this.isEnum

private fun Class<*>.isBooleanType(): Boolean =
    this == Boolean::class.java || this == java.lang.Boolean::class.java

private fun Class<*>.isIntegerType(): Boolean =
    this == Int::class.java || this == java.lang.Integer::class.java ||
    this == Long::class.java || this == java.lang.Long::class.java ||
    this == Short::class.java || this == java.lang.Short::class.java ||
    this == Byte::class.java || this == java.lang.Byte::class.java

private fun Class<*>.isNumberType(): Boolean =
    this == Float::class.java || this == java.lang.Float::class.java ||
    this == Double::class.java || this == java.lang.Double::class.java

private fun Class<*>.isArrayType(): Boolean =
    this.isArray || Iterable::class.java.isAssignableFrom(this)

/**
 * Scan the given controller instance and return callbacks for fields and methods
 * annotated with @SystemMessage, with embedded scope restrictions.
 *
 * Rules:
 * - Fields annotated with @SystemMessage will be used as-is (their toString() is returned).
 * - Kotlin properties annotated with @SystemMessage are supported.
 * - Methods annotated with @SystemMessage must return String? and either take no parameters
 *   or a single parameter of type SystemMessageContext.
 * - Names are extracted from @SystemMessage.name, or auto-generated from method/field/property name
 * - Scope restrictions are validated against parent @ChatService scopes and embedded in the callback
 * - Duplicate names within this controller will throw an error
 */
 internal fun toSystemMessageCallbacks(
     controller: Any,
     parentServiceScopes: Set<String> = emptySet()
 ): List<SystemMessageCallback> {
     val callbacks = mutableListOf<SystemMessageCallback>()
     val observedNames = mutableSetOf<String>()
     val clazz = controller::class.java

     // Fields
     for (field in clazz.declaredFields) {
         if (field.isAnnotationPresent(SystemMessage::class.java)) {
             val annotation = field.getAnnotation(SystemMessage::class.java)
             processFieldSystemMessage(
                 annotation, field, controller, callbacks, observedNames, clazz, parentServiceScopes
             )
         }
     }

     // Kotlin properties
     for (property in controller::class.memberProperties) {
         val annotation = property.findAnnotation<SystemMessage>() ?: continue
         processPropertySystemMessage(
             annotation, property, controller, callbacks, observedNames, clazz, parentServiceScopes
         )
     }

     // Methods
     for (method in clazz.declaredMethods) {
         if (!method.isAnnotationPresent(SystemMessage::class.java)) continue
         processMethodSystemMessage(
             method, controller, callbacks, observedNames, clazz, parentServiceScopes
         )
     }

     return callbacks
 }

 private fun processFieldSystemMessage(
     annotation: SystemMessage,
     field: java.lang.reflect.Field,
     controller: Any,
     callbacks: MutableList<SystemMessageCallback>,
     observedNames: MutableSet<String>,
     clazz: Class<*>,
     parentServiceScopes: Set<String>
 ) {
     val name = annotation.name.takeIf { it.isNotBlank() } ?: field.name
     require(!observedNames.contains(name)) {
         "Duplicate system message name '$name' in ${clazz.name}"
     }
     observedNames.add(name)

     val declaredScopes = annotation.scope.toSet()
     val actualScopes = computeActualScopes(declaredScopes, parentServiceScopes, "system message '$name'")

     trySetAccessible(field)
     callbacks += FieldSystemMessageCallback(controller, field, name, actualScopes)
 }

  private fun processPropertySystemMessage(
      annotation: SystemMessage,
      property: kotlin.reflect.KProperty<*>,
      controller: Any,
      callbacks: MutableList<SystemMessageCallback>,
      observedNames: MutableSet<String>,
      clazz: Class<*>,
      parentServiceScopes: Set<String>
  ) {
      val name = annotation.name.takeIf { it.isNotBlank() } ?: property.name
      require(!observedNames.contains(name)) {
          "Duplicate system message name '$name' in ${clazz.name}"
      }
      observedNames.add(name)

      val declaredScopes = annotation.scope.toSet()
      val actualScopes = computeActualScopes(declaredScopes, parentServiceScopes, "system message '$name'")

      @Suppress("UNCHECKED_CAST")
      val typedProperty = property as kotlin.reflect.KProperty1<Any, *>
      callbacks += PropertySystemMessageCallback(controller, typedProperty, name, actualScopes)
  }

 private fun processMethodSystemMessage(
     method: java.lang.reflect.Method,
     controller: Any,
     callbacks: MutableList<SystemMessageCallback>,
     observedNames: MutableSet<String>,
     clazz: Class<*>,
     parentServiceScopes: Set<String>
 ) {
     val annotation = method.getAnnotation(SystemMessage::class.java)

     val name = annotation.name.takeIf { it.isNotBlank() } ?: method.name
     require(!observedNames.contains(name)) {
         "Duplicate system message name '$name' in ${clazz.name}"
     }
     observedNames.add(name)

     val returnType = method.returnType
     if (returnType != String::class.java) {
         return
     }

     val isContextual = validateSystemMessageMethodParameters(method, clazz)

     trySetAccessible(method)

     val declaredScopes = annotation.scope.toSet()
     val actualScopes = computeActualScopes(declaredScopes, parentServiceScopes, "system message '$name'")

     callbacks += MethodSystemMessageCallback(controller, method, isContextual, name, actualScopes)
 }

 private fun validateSystemMessageMethodParameters(method: java.lang.reflect.Method, clazz: Class<*>): Boolean {
     val params = method.parameterTypes
     return when (params.size) {
         0 -> false
         1 -> {
             if (params[0] != SystemMessageContext::class.java) {
                 val msg = "Method ${method.name} in ${clazz.name} annotated with @SystemMessage " +
                     "has invalid parameters. Must have either no parameters or a single parameter " +
                     "of type SystemMessageContext."
                 throw IllegalStateException(msg)
             }
             true
         }
         else -> {
             val msg = "Method ${method.name} in ${clazz.name} annotated with @SystemMessage " +
                 "has invalid parameters. Must have either no parameters or a single parameter " +
                 "of type SystemMessageContext."
             throw IllegalStateException(msg)
         }
     }
 }

