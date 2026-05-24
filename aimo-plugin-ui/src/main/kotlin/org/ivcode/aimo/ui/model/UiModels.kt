package org.ivcode.aimo.ui.model

import java.util.UUID

// TODO move this into the chat controller
data class TitleResponse (
    val title: String,
    val source: String,
)

data class ConversationTitle (
    val chatId: UUID,
    val source: String,
    val title: String?,
)

data class TimeResponse (
    val system: String,
    val user: String?,
)