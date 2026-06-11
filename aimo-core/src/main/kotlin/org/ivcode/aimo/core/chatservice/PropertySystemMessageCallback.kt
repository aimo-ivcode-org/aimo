package org.ivcode.aimo.core.chatservice

import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

class PropertySystemMessageCallback(
    private val instance: Any,
    private val property: KProperty1<out Any, *>,
) : SystemMessageCallback {
    override fun call(context: SystemMessageContext): String? {
        // context is ignored for property-defined system messages
        property.isAccessible = true
        return property.getter.call(instance)?.toString()
    }
}


