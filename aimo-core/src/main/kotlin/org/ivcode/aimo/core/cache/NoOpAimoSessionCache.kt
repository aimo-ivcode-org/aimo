package org.ivcode.aimo.core.cache

import org.ivcode.aimo.core.AimoChatMessage
import java.util.UUID

object NoOpAimoSessionCache : AimoSessionCache {

    override fun getRuntimeMetadata(chatId: UUID): Map<String, Any>? = null
    override fun putRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>) = Unit
    override fun upsertRuntimeMetadata(chatId: UUID, metadata: Map<String, Any>) = Unit
    override fun removeRuntimeMetadata(chatId: UUID, keys: List<String>) = Unit

    override fun getMessages(chatId: UUID): List<AimoChatMessage>? = null
    override fun putMessages(chatId: UUID, messages: List<AimoChatMessage>) = Unit
    override fun appendMessages(chatId: UUID, messages: List<AimoChatMessage>) = Unit

    override fun getTokenCalibration(chatId: UUID): SessionTokenCalibration? = null
    override fun putTokenCalibration(chatId: UUID, calibration: SessionTokenCalibration) = Unit

    override fun getCacheStats(chatId: UUID): SessionCacheStats? = null
    override fun putCacheStats(chatId: UUID, stats: SessionCacheStats) = Unit

    override fun evict(chatId: UUID) = Unit
}

