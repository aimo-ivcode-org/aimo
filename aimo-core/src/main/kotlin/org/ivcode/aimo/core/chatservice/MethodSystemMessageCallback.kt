package org.ivcode.aimo.core.chatservice

import java.lang.reflect.Method
import java.lang.reflect.InaccessibleObjectException

class MethodSystemMessageCallback (
    val instance: Any?,
    val method: Method,
    val isContextual: Boolean,
    override val name: String,
    override val scopes: Set<String> = emptySet()
): SystemMessageCallback {
    override fun call(context: SystemMessageContext): String? {
        // system messages take an optional SystemMessageContext parameter,
        // so we need to check if the method is contextual or not
        // Ensure method is accessible before invoking. If we cannot make it accessible, throw with guidance.
        val accessible = trySetAccessible(method)
        if (!accessible) {
            // Provide a helpful message referencing module/Java version which often cause reflective access failures
            val declaring = method.declaringClass
            throw IllegalAccessException(
                "Cannot access method ${method.name} on ${declaring.name}. " +
                    "Ensure the method is public or open the module/package to reflection (Java ${System.getProperty("java.version")}, module=${declaring.module})."
            )
        }

        return try {
            if (isContextual) {
                method.invoke(instance, context) as String?
            } else {
                method.invoke(instance) as String?
            }
        } catch (e: InaccessibleObjectException) {
            throw IllegalAccessException("Failed to invoke method ${method.name}: ${e.message}").initCause(e)
        }
    }
}

