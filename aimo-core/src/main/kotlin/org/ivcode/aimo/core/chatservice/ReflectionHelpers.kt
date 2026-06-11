package org.ivcode.aimo.core.chatservice

import java.lang.reflect.AccessibleObject

/**
 * Try to make the given AccessibleObject accessible.
 * Returns true when the object was made accessible, false otherwise.
 *
 * This helper attempts to call AccessibleObject.trySetAccessible() on Java 9+ if available,
 * falling back to setting isAccessible on older runtimes. Any exceptions are caught and
 * false is returned when accessibility cannot be granted.
 */
internal fun trySetAccessible(obj: AccessibleObject): Boolean {
    // Prefer trySetAccessible() on Java 9+ when available
    try {
        val tryMethod = AccessibleObject::class.java.getMethod("trySetAccessible")
        try {
            val invoked = tryMethod.invoke(obj)
            if (invoked is Boolean) {
                return invoked
            }
            // if it's not a Boolean for some reason, continue to fallback
        } catch (_: Throwable) {
            // fallback below
        }
    } catch (_: NoSuchMethodException) {
        // trySetAccessible not available (Java 8)
    } catch (_: Throwable) {
        // other errors - fall back
    }

    // Fallback: try setting isAccessible (works on older runtimes). This may throw
    // InaccessibleObjectException on modular runtimes where access is denied.
    return try {
        obj.isAccessible = true
        true
    } catch (_: Throwable) {
        false
    }
}


