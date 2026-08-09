package org.ivcode.aimo.core.chatservice

import java.lang.reflect.Field

internal class FieldSystemMessageCallback(
    private val instance: Any?,
    private val field: Field,
    override val name: String,
    override val scopes: Set<String> = emptySet()
): SystemMessageCallback {
    override fun call(context: SystemMessageContext): String? {
        // context is ignored for field defined system messages
        return field.get(instance)?.toString()
    }
}

