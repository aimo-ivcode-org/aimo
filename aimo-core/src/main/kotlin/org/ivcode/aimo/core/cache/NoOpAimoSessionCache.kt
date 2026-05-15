package org.ivcode.aimo.core.cache

import java.util.UUID

class NoOpAimoSessionCache(override val chatId: UUID) : AimoSessionCache {

    override fun getRuntimeProperty(key: String): Any? = null
    override fun getRuntimeProperties(): Map<String, Any> = emptyMap()
    override fun writeRuntimeProperty(key: String, value: Any) = Unit
    override fun deleteRuntimeProperty(key: String): Boolean = false


    override fun evict() = Unit
}

