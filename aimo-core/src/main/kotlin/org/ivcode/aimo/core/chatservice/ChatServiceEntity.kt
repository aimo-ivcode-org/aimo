package org.ivcode.aimo.core.chatservice

/**
 * Internal wrapper that holds tool callbacks and system message callbacks
 * discovered from @ChatService beans, along with their scope restrictions.
 *
 * @property tools List of scoped tool callbacks with their scope restrictions
 * @property systemMessages List of scoped system message callbacks with names and scope restrictions
 */
data class ChatServiceEntity (
    val name: String,
    val clazz: Class<out Any>,
    val instance: Any,
    val tools: List<ScopedToolCallback>,
    val systemMessages: List<ScopedSystemMessageCallbackWithName>
)

