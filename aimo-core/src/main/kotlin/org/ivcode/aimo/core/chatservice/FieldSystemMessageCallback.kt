package org.ivcode.aimo.core.chatservice

import java.lang.reflect.Field

class FieldSystemMessageCallback(
    private val instance: Any?,
    private val field: Field
): SystemMessageCallback {
    override fun call(context: SystemMessageContext): String? {
        // context is ignored for field defined system messages
        return field.get(instance)?.toString()
    }
}

