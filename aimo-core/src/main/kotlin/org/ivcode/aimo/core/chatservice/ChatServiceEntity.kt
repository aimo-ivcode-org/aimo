package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.AimoToolCallback

/**
 * Internal wrapper that holds tool callbacks and system message callbacks
 * discovered from @ChatService beans.
 */
data class ChatServiceEntity (
    val name: String,
    val clazz: Class<out Any>,
    val instance: Any,
    val tools: List<AimoToolCallback>,
    val systemMessages: List<SystemMessageCallback>
)

