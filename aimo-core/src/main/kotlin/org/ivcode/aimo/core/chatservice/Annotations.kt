package org.ivcode.aimo.core.chatservice

import org.springframework.stereotype.Component
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.annotation.Target

/**
 * Marks a Spring bean as a chat service that provides tools and system messages
 * to the chat client.
 *
 * Chat services are discovered at startup via reflection and their annotated
 * methods/fields are registered as LLM-callable tools and system messages.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Component
annotation class ChatService

@Retention(AnnotationRetention.RUNTIME)
@Target(FUNCTION, FIELD, PROPERTY)
annotation class SystemMessage

/**
 * Marks a method as an LLM-callable tool.
 *
 * @property name Optional override for the tool name exposed to the model.
 *                Defaults to the method name when blank.
 * @property description Human-readable description sent to the model so it knows
 *                        when and how to call this tool.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(FUNCTION)
annotation class Tool(
    val name: String = "",
    val description: String = "",
)

/**
 * Describes a single tool-method parameter for the model.
 *
 * @property description Human-readable description of this parameter included in
 *                        the tool's JSON Schema sent to the model.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(VALUE_PARAMETER)
annotation class ToolParam(
    val description: String = "",
)


