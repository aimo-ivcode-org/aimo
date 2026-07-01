package org.ivcode.aimo.server.model

import java.util.UUID

data class ChatConversationInfo(
    val chatId: UUID,
    val metadata: Map<String, Any> = emptyMap(),
)

data class CreateConversationRequest(
    val metadata: Map<String, Any> = emptyMap(),
)

data class ScopedConversationRequest(
    val scopeMetadata: Map<String, Any> = emptyMap(),
)

data class ConversationMetadataUpdateRequest(
    val metadata: Map<String, Any>,
    val scopeMetadata: Map<String, Any> = emptyMap(),
)

data class ConversationMetadataDeleteRequest(
    val keys: List<String>,
    val scopeMetadata: Map<String, Any> = emptyMap(),
)
