package org.ivcode.aimo.core.controller

import org.ivcode.aimo.core.model.AimoToolCallback

data class ChatControllerEntity (
    val name: String,
    val clazz: Class<out Any>,
    val instance: Any,
    val tools: List<AimoToolCallback>,
    val systemMessages: List<SystemMessageCallback>
)
