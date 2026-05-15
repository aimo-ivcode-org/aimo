package org.ivcode.aimo.core.cache

import org.ivcode.aimo.core.AimoChatMessage
import java.util.UUID

/**
 * Cache abstraction for cache-lifetime data used during chat orchestration.
 *
 * DAO storage remains the source of truth for durable conversation metadata.
 * Implementations of this cache accelerate access to runtime metadata,
 * message history, and token calibration.
 */
interface AimoSessionCache {

    /**
     * Runtime-only metadata that should live only for the cache lifetime.
     */
    fun getRuntimeMetadata(chatId: UUID): Map<String, Any>?
    fun putRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>)
    fun upsertRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>)
    fun removeRuntimeMetadata(chatId: UUID, keys: List<String>)

    fun getMessages(chatId: UUID): List<AimoChatMessage>?
    fun putMessages(chatId: UUID, messages: List<AimoChatMessage>)
    fun appendMessages(chatId: UUID, messages: List<AimoChatMessage>)

    fun getTokenCalibration(chatId: UUID): SessionTokenCalibration?
    fun putTokenCalibration(chatId: UUID, calibration: SessionTokenCalibration)

    fun getCacheStats(chatId: UUID): SessionCacheStats?
    fun putCacheStats(chatId: UUID, stats: SessionCacheStats)

    fun evict(chatId: UUID)
}

