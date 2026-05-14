package org.ivcode.aimo.core.cache

import org.ivcode.aimo.core.AimoChatMessage
import java.util.UUID

/**
 * Cache abstraction for session-scoped data used during chat orchestration.
 *
 * DAO storage remains the source of truth. Implementations of this cache are an
 * acceleration layer for frequently accessed data like message history and token
 * calibration state.
 */
interface AimoSessionCache {
    fun getMetadata(chatId: UUID): Map<String, Any>?
    fun putMetadata(chatId: UUID, metadata: Map<String, Any>)
    fun upsertMetadata(chatId: UUID, metadata: Map<String, Any>)
    fun removeMetadata(chatId: UUID, keys: List<String>)

    fun getMessages(chatId: UUID): List<AimoChatMessage>?
    fun putMessages(chatId: UUID, messages: List<AimoChatMessage>)
    fun appendMessages(chatId: UUID, messages: List<AimoChatMessage>)

    fun getTokenCalibration(chatId: UUID): SessionTokenCalibration?
    fun putTokenCalibration(chatId: UUID, calibration: SessionTokenCalibration)

    fun evict(chatId: UUID)
}

