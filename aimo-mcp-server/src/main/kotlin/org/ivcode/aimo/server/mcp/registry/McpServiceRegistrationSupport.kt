package org.ivcode.aimo.server.mcp.registry

import org.ivcode.aimo.server.mcp.annotation.McpPrompt
import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.protocol.ToolDefinition
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.slf4j.Logger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Build immutable metadata for the service being registered.
 *
 * @param beanName Spring bean name for the service.
 * @param bean service instance.
 * @return descriptor with naming information used during registration.
 */
internal fun buildServiceDescriptor(beanName: String, bean: Any): ServiceDescriptor {
    val serviceName = bean.javaClass
        .getAnnotation(McpService::class.java)
        ?.name
        ?.takeIf { name -> name.isNotBlank() }
    val idPrefix = listOfNotNull(beanName, serviceName).joinToString(ID_SEPARATOR)
    return ServiceDescriptor(beanName = beanName, bean = bean, serviceName = serviceName, idPrefix = idPrefix)
}

/**
 * Ensure only public methods are annotated as MCP tools or prompts.
 *
 * @param descriptor service being validated.
 */
internal fun validateAnnotatedMethodVisibility(descriptor: ServiceDescriptor) {
    descriptor.bean.javaClass.declaredMethods.forEach { method ->
        if (!Modifier.isPublic(method.modifiers)) {
            validateNonPublicMethod(descriptor.beanName, method)
        }
    }
}

/**
 * Validate one non-public method for disallowed MCP annotations.
 *
 * @param beanName Spring bean name for the service.
 * @param method method to validate.
 */
internal fun validateNonPublicMethod(beanName: String, method: Method) {
    when {
        method.getAnnotation(McpTool::class.java) != null ->
            throw annotatedNonPublicMethodException(beanName, method, "@McpTool", "tools")

        method.getAnnotation(McpPrompt::class.java) != null ->
            throw annotatedNonPublicMethodException(beanName, method, "@McpPrompt", "prompts")
    }
}

/**
 * Create a consistent exception for non-public annotated methods.
 *
 * @param beanName Spring bean name for the service.
 * @param method offending method.
 * @param annotationName annotation found on the method.
 * @param memberLabel human-readable member category.
 * @return configured illegal-argument exception.
 */
internal fun annotatedNonPublicMethodException(
    beanName: String,
    method: Method,
    annotationName: String,
    memberLabel: String
): IllegalArgumentException {
    return IllegalArgumentException(
        "Service '$beanName' has private/protected method '${method.name}' " +
            "annotated with $annotationName. Only public methods can be " +
            "exposed as MCP $memberLabel."
    )
}

/**
 * Register all public tool methods for a service.
 *
 * @param descriptor service being registered.
 * @param schemaGenerator schema generator used for tool metadata.
 * @param logger logger for validation and registration messages.
 * @param toolIndex mutable global tool registry.
 * @return tool metadata owned by that service.
 */
internal fun registerToolMethods(
    descriptor: ServiceDescriptor,
    schemaGenerator: McpSchemaGenerator,
    logger: Logger,
    toolIndex: MutableMap<String, ToolRegistry>
): List<ToolInfo> {
    val registeredTools = mutableListOf<ToolInfo>()

    descriptor.bean.javaClass.methods.forEach { method ->
        if (method.getAnnotation(McpTool::class.java) != null) {
            registerToolMethod(descriptor, method, schemaGenerator, logger, toolIndex, registeredTools)
        }
    }

    return registeredTools
}

/**
 * Register all public prompt methods for a service.
 *
 * @param descriptor service being registered.
 * @param schemaGenerator schema generator used for prompt metadata.
 * @param logger logger for registration messages.
 * @param promptIndex mutable global prompt registry.
 * @return prompt metadata owned by that service.
 */
internal fun registerPromptMethods(
    descriptor: ServiceDescriptor,
    schemaGenerator: McpSchemaGenerator,
    logger: Logger,
    promptIndex: MutableMap<String, PromptRegistry>
): List<PromptInfo> {
    val registeredPrompts = mutableListOf<PromptInfo>()

    descriptor.bean.javaClass.methods.forEach { method ->
        if (method.getAnnotation(McpPrompt::class.java) != null) {
            registerPromptMethod(descriptor, method, schemaGenerator, logger, promptIndex, registeredPrompts)
        }
    }

    return registeredPrompts
}

/**
 * Register one tool method and publish it to the tool indexes.
 *
 * @param descriptor owning service metadata.
 * @param method public method annotated with @McpTool.
 * @param schemaGenerator schema generator used for tool metadata.
 * @param logger logger for validation and registration messages.
 * @param toolIndex mutable global tool registry.
 * @param registeredTools mutable accumulator for service-local tool metadata.
 */
internal fun registerToolMethod(
    descriptor: ServiceDescriptor,
    method: Method,
    schemaGenerator: McpSchemaGenerator,
    logger: Logger,
    toolIndex: MutableMap<String, ToolRegistry>,
    registeredTools: MutableList<ToolInfo>
) {
    val schema = schemaGenerator.generateToolSchema(method)
    logSchemaValidationErrors(schemaGenerator, schema, logger)

    val toolId = descriptor.toInternalMemberId(schema.name)
    val clientVisibleName = descriptor.toClientVisibleName(schema.name)
    findConflictingBeanName(clientVisibleName, descriptor.beanName, toolIndex) { internalId, registry ->
        registryClientVisibleName(registry.beanName, registry.schema.name, internalId)
    }?.let { existingBean ->
        throw nameConflictException("Tool", clientVisibleName, existingBean, descriptor.beanName)
    }

    registeredTools += ToolInfo(
        id = toolId,
        beanName = descriptor.beanName,
        method = method,
        schema = schema
    )
    toolIndex[toolId] = ToolRegistry(
        beanName = descriptor.beanName,
        bean = descriptor.bean,
        method = method,
        schema = schema
    )
    logger.debug("Registered tool: {}", toolId)
}

/**
 * Register one prompt method and publish it to the prompt indexes.
 *
 * @param descriptor owning service metadata.
 * @param method public method annotated with @McpPrompt.
 * @param schemaGenerator schema generator used for prompt metadata.
 * @param logger logger for registration messages.
 * @param promptIndex mutable global prompt registry.
 * @param registeredPrompts mutable accumulator for service-local prompt metadata.
 */
internal fun registerPromptMethod(
    descriptor: ServiceDescriptor,
    method: Method,
    schemaGenerator: McpSchemaGenerator,
    logger: Logger,
    promptIndex: MutableMap<String, PromptRegistry>,
    registeredPrompts: MutableList<PromptInfo>
) {
    val schema = schemaGenerator.generatePromptSchema(method)
    val promptId = descriptor.toInternalMemberId(schema.name)
    val clientVisibleName = descriptor.toClientVisibleName(schema.name)
    findConflictingBeanName(clientVisibleName, descriptor.beanName, promptIndex) { internalId, registry ->
        registryClientVisibleName(registry.beanName, registry.schema.name, internalId)
    }?.let { existingBean ->
        throw nameConflictException("Prompt", clientVisibleName, existingBean, descriptor.beanName)
    }

    registeredPrompts += PromptInfo(
        id = promptId,
        beanName = descriptor.beanName,
        method = method,
        schema = schema
    )
    promptIndex[promptId] = PromptRegistry(
        beanName = descriptor.beanName,
        bean = descriptor.bean,
        method = method,
        schema = schema
    )
    logger.debug("Registered prompt: {}", promptId)
}

/**
 * Log any schema validation issues discovered for a tool definition.
 *
 * @param schemaGenerator schema generator used for validation.
 * @param schema generated tool schema.
 * @param logger logger for validation warnings.
 */
internal fun logSchemaValidationErrors(
    schemaGenerator: McpSchemaGenerator,
    schema: ToolDefinition,
    logger: Logger
) {
    val errors = schemaGenerator.validateSchema(schema)
    if (errors.isNotEmpty()) {
        logger.warn("Tool '{}' validation errors: {}", schema.name, errors)
    }
}

/**
 * Find a conflicting bean name for a client-visible tool or prompt name.
 *
 * @param clientVisibleName name visible to MCP clients.
 * @param beanName bean currently being registered.
 * @param index existing registry entries to check.
 * @param visibleNameSelector callback that derives the visible name for an existing entry.
 * @return conflicting bean name, if any.
 */
internal fun <T> findConflictingBeanName(
    clientVisibleName: String,
    beanName: String,
    index: Map<String, T>,
    visibleNameSelector: (String, T) -> String
): String? {
    return index.entries.firstOrNull { (internalId, registry) ->
        visibleNameSelector(internalId, registry) == clientVisibleName && extractBeanName(registry) != beanName
    }?.let { (_, registry) -> extractBeanName(registry) }
}

/**
 * Build a tool or prompt name conflict exception.
 *
 * @param memberLabel human-readable member type.
 * @param clientVisibleName conflicting client-visible name.
 * @param existingBean existing bean exposing the name.
 * @param newBean bean being registered.
 * @return configured illegal-argument exception.
 */
internal fun nameConflictException(
    memberLabel: String,
    clientVisibleName: String,
    existingBean: String,
    newBean: String
): IllegalArgumentException {
    return IllegalArgumentException(
        "$memberLabel name conflict detected: multiple services expose ${memberLabel.lowercase()} " +
            "with client-visible name '$clientVisibleName'. Conflicting services: " +
            "'$existingBean' and '$newBean'. Either use different service names " +
            "or rename one of the ${memberLabel.lowercase()}s."
    )
}

private fun <T> extractBeanName(registry: T): String {
    return when (registry) {
        is ToolRegistry -> registry.beanName
        is PromptRegistry -> registry.beanName
        else -> error("Unsupported registry type: ${registry!!::class.java.name}")
    }
}

