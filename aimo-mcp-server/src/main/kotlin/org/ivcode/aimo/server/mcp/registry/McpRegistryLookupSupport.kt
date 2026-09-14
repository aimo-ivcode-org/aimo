package org.ivcode.aimo.server.mcp.registry

/**
 * Resolve a registry ID to the client-visible tool or prompt name.
 *
 * @param registryId internal registry identifier.
 * @return client-visible name.
 */
internal fun registryClientVisibleName(registryId: String): String {
    return ParsedRegistryId.from(registryId)?.clientVisibleName ?: registryId
}

/**
 * Compute the client-visible name for an already-registered tool or prompt.
 *
 * @param beanName owning bean name.
 * @param memberName tool or prompt schema name.
 * @param registryId internal registry identifier.
 * @return client-visible name.
 */
internal fun registryClientVisibleName(
    beanName: String,
    memberName: String,
    registryId: String
): String {
    return ParsedRegistryId.from(registryId)?.clientVisibleName ?: "$beanName$ID_SEPARATOR$memberName"
}

/**
 * Resolve a tool by its client-visible service-prefixed name.
 *
 * @param toolId client-visible tool ID.
 * @param toolIndex existing tool registry.
 * @return matching tool registry, if found.
 */
internal fun resolveToolByClientVisibleId(
    toolId: String,
    toolIndex: Map<String, ToolRegistry>
): ToolRegistry? {
    val clientVisibleId = ClientVisibleId.from(toolId)
    return if (clientVisibleId.serviceName != null) {
        toolIndex.entries.firstOrNull { (internalId, _) ->
            ParsedRegistryId.from(internalId)?.matches(clientVisibleId) == true
        }?.value
    } else {
        null
    }
}

/**
 * Resolve a tool by simple tool name across all services.
 *
 * @param toolId simple tool name without service prefix.
 * @param toolIndex existing tool registry.
 * @return matching tool registry, if found.
 */
internal fun resolveToolBySimpleName(
    toolId: String,
    toolIndex: Map<String, ToolRegistry>
): ToolRegistry? {
    return if (toolId.contains(ID_SEPARATOR)) {
        null
    } else {
        toolIndex.values.firstOrNull { registry -> registry.schema.name == toolId }
    }
}

/**
 * Resolve a prompt by its client-visible service-prefixed name.
 *
 * @param promptId client-visible prompt ID.
 * @param promptIndex existing prompt registry.
 * @return matching prompt registry, if found.
 */
internal fun resolvePromptByClientVisibleId(
    promptId: String,
    promptIndex: Map<String, PromptRegistry>
): PromptRegistry? {
    val clientVisibleId = ClientVisibleId.from(promptId)
    return if (clientVisibleId.serviceName != null) {
        promptIndex.entries.firstOrNull { (internalId, _) ->
            ParsedRegistryId.from(internalId)?.matches(clientVisibleId) == true
        }?.value
    } else {
        null
    }
}

/**
 * Resolve a prompt by simple prompt name across all services.
 *
 * @param promptId simple prompt name without service prefix.
 * @param promptIndex existing prompt registry.
 * @return matching prompt registry, if found.
 */
internal fun resolvePromptBySimpleName(
    promptId: String,
    promptIndex: Map<String, PromptRegistry>
): PromptRegistry? {
    return if (promptId.contains(ID_SEPARATOR)) {
        null
    } else {
        promptIndex.values.firstOrNull { registry -> registry.schema.name == promptId }
    }
}

/**
 * Immutable metadata describing one MCP service registration.
 *
 * @property beanName Spring bean name.
 * @property bean service instance.
 * @property serviceName optional client-visible service name.
 * @property idPrefix internal ID prefix used for tools and prompts.
 */
internal data class ServiceDescriptor(
    val beanName: String,
    val bean: Any,
    val serviceName: String?,
    val idPrefix: String
) {
    /**
     * Build the client-visible name for a tool or prompt.
     *
     * @param memberName tool or prompt name.
     * @return name visible to MCP clients.
     */
    fun toClientVisibleName(memberName: String): String {
        return if (serviceName != null) {
            "$serviceName$ID_SEPARATOR$memberName"
        } else {
            memberName
        }
    }

    /**
     * Build the internal registry ID for a tool or prompt.
     *
     * @param memberName tool or prompt name.
     * @return internal registry ID.
     */
    fun toInternalMemberId(memberName: String): String {
        return "$idPrefix$ID_SEPARATOR$memberName"
    }
}

/**
 * Parsed representation of an internal registry identifier.
 *
 * @property beanName owning Spring bean name.
 * @property serviceName optional service prefix visible to clients.
 * @property memberName tool or prompt name.
 */
internal data class ParsedRegistryId(
    val beanName: String,
    val serviceName: String?,
    val memberName: String
) {
    val clientVisibleName: String
        get() = if (serviceName != null) {
            "$serviceName$ID_SEPARATOR$memberName"
        } else {
            memberName
        }

    /**
     * Check whether the parsed internal ID matches a client-visible ID.
     *
     * @param clientVisibleId client-visible identifier.
     * @return true when the service and member names match.
     */
    fun matches(clientVisibleId: ClientVisibleId): Boolean {
        return serviceName == clientVisibleId.serviceName && memberName == clientVisibleId.memberName
    }

    companion object {
        /**
         * Parse an internal registry ID.
         *
         * @param rawId raw internal identifier.
         * @return parsed ID when the format is recognized.
         */
        fun from(rawId: String): ParsedRegistryId? {
            val parts = rawId.split(ID_SEPARATOR)
            return when (parts.size) {
                INTERNAL_ID_PARTS_WITH_SERVICE -> ParsedRegistryId(
                    beanName = parts[BEAN_NAME_INDEX],
                    serviceName = parts[SERVICE_NAME_INDEX],
                    memberName = parts[MEMBER_NAME_INDEX]
                )

                INTERNAL_ID_PARTS_SIMPLE -> ParsedRegistryId(
                    beanName = parts[BEAN_NAME_INDEX],
                    serviceName = null,
                    memberName = parts[SIMPLE_MEMBER_NAME_INDEX]
                )

                else -> null
            }
        }
    }
}

/**
 * Parsed representation of a client-visible tool or prompt identifier.
 *
 * @property serviceName optional service name supplied by the client.
 * @property memberName tool or prompt name.
 */
internal data class ClientVisibleId(
    val serviceName: String?,
    val memberName: String
) {
    companion object {
        /**
         * Parse a client-visible ID.
         *
         * @param rawId raw client-visible identifier.
         * @return parsed client-visible ID.
         */
        fun from(rawId: String): ClientVisibleId {
            val parts = rawId.split(ID_SEPARATOR)
            return if (parts.size == CLIENT_VISIBLE_ID_PARTS_WITH_SERVICE) {
                ClientVisibleId(serviceName = parts[0], memberName = parts[1])
            } else {
                ClientVisibleId(serviceName = null, memberName = rawId)
            }
        }
    }
}

internal const val ID_SEPARATOR = ":"
internal const val INTERNAL_ID_PARTS_WITH_SERVICE = 3
internal const val INTERNAL_ID_PARTS_SIMPLE = 2
internal const val CLIENT_VISIBLE_ID_PARTS_WITH_SERVICE = 2
internal const val BEAN_NAME_INDEX = 0
internal const val SERVICE_NAME_INDEX = 1
internal const val SIMPLE_MEMBER_NAME_INDEX = 1
internal const val MEMBER_NAME_INDEX = 2

