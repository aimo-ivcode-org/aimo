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
 *
 *  @property scope List of chat scope IDs this service is available in.
 *                 Empty array means the service has no scope restrictions (available to all scopes).
 *                 Example: scope = ["admin", "research"]
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Component
annotation class ChatService (
    val scope: Array<String> = []
)

/**
 * Marks a field, property, or method as providing a system message for the chat.
 *
 * @property scope List of chat scope IDs this system message is available in.
 *                 Empty array inherits the parent @ChatService scope; if the parent has no scope restrictions, this becomes available to all scopes.
 *                 If the parent @ChatService specifies scopes, this must be a subset (fail-fast validation).
 *                 Example: scope = ["admin", "research"]
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(FUNCTION, FIELD, PROPERTY)
annotation class SystemMessage (
    /**
     * Optional explicit name for stable reference to this system message.
     * If empty, a name will be auto-generated from the method/field/property name.
     * Names must be unique within the application (fail-fast validation at startup).
     * Example: name = "research_guide"
     */
    val name: String = "",

    /**
     * List of chat scope IDs this system message is available in.
     * Empty array inherits the parent @ChatService scope; if the parent has no scope restrictions, this becomes available to all scopes.
     * If the parent @ChatService specifies scopes, this must be a subset (fail-fast validation).
     * Example: scope = ["admin", "research"]
     */
    val scope: Array<String> = []
)

/**
 * Marks a method as an LLM-callable tool.
 *
 * @property name Optional override for the tool name exposed to the model.
 *                Defaults to the method name when blank.
 * @property description Human-readable description sent to the model so it knows
 *                        when and how to call this tool.
 * @property scope List of chat scope IDs this tool is available in.
 *                 Empty array means available to all scopes (default, backwards compatible).
 *                 Example: scope = ["admin", "code-review"]
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(FUNCTION)
annotation class Tool (
    val name: String = "",
    val description: String = "",
    val scope: Array<String> = []
)

/**
 * Describes a single tool-method parameter for the model.
 *
 * @property description Human-readable description of this parameter included in
 *                        the tool's JSON Schema sent to the model.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(VALUE_PARAMETER)
annotation class ToolParam (
    val description: String = "",
)
