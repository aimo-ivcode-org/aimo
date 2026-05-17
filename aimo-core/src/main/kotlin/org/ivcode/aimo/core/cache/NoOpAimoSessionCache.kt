package org.ivcode.aimo.core.cache

import java.util.UUID

class NoOpAimoSessionCache(override val chatId: UUID) : AimoSessionCache {

    override fun getSessionProperty(key: String): Any? = null
    override fun getSessionProperties(): Map<String, Any> = emptyMap()
    override fun writeSessionProperty(key: String, value: Any) = Unit
    override fun deleteSessionProperty(key: String): Boolean = false


    override fun evict() = Unit
}

