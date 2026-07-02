package org.ivcode.aimo.core.chatservice

/**
 * Internal wrapper that holds tool callbacks and system message callbacks
 * discovered from @ChatService beans. Each callback carries its scope restrictions
 * as an embedded property.
 *
 * @property tools List of tool callbacks with embedded scope restrictions
 * @property systemMessages List of system message callbacks with embedded scope restrictions
 */
data class ChatServiceEntity (
    val name: String,
    val clazz: Class<out Any>,
    val instance: Any,
    val tools: List<org.ivcode.aimo.core.model.ToolCallback>,
    val systemMessages: List<SystemMessageCallback>
)

