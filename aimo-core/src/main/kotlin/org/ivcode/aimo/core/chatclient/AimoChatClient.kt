package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatRequest
import org.ivcode.aimo.core.model.AimoChatResponse
import java.util.UUID

interface AimoChatClient {
    val chatId: UUID
    fun chat(request: AimoChatRequest): AimoChatResponse
    fun chatStream(request: AimoChatRequest, callback: (AimoChatResponse) -> Unit): AimoChatResponse
}