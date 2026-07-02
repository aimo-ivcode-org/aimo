package org.ivcode.aimo.ui.extentions

import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoConversationInfo
import org.ivcode.aimo.ui.model.ConversationTitle
import java.util.UUID

internal const val PROPERTY_NAME__TITLE: String = "title"

private const val DEFAULT_TITLE_SOURCE = "USER"

private fun toConversationTitle(raw: Any?, chatId: UUID): ConversationTitle? {
    return when (raw) {
        null -> null
        is ConversationTitle -> raw
        is String -> ConversationTitle(chatId = chatId, source = DEFAULT_TITLE_SOURCE, title = raw)
        is Map<*, *> -> {
            val source = (raw["source"] as? String) ?: DEFAULT_TITLE_SOURCE
            val title = raw["title"] as? String
            val parsedChatId = when (val value = raw["chatId"]) {
                is UUID -> value
                is String -> runCatching { UUID.fromString(value) }.getOrNull()
                else -> null
            } ?: chatId

            ConversationTitle(chatId = parsedChatId, source = source, title = title)
        }
        else -> null
    }
}

fun AimoConversationInfo.getTitle(): ConversationTitle? {
    return toConversationTitle(this.metadata[PROPERTY_NAME__TITLE], this.chatId)
}

fun Conversation.getTitle(): ConversationTitle? {
    return toConversationTitle(this.getChatProperty(PROPERTY_NAME__TITLE), this.chatId)
}

fun Conversation.setTitle(title: String, source: String = DEFAULT_TITLE_SOURCE): ConversationTitle {
    val conversationTitle = ConversationTitle(
        chatId = this.chatId,
        source = source,
        title = title
    )

    this.writeChatProperty(PROPERTY_NAME__TITLE, conversationTitle)
    return conversationTitle
}

// Helper to get title from chatId and metadata map
fun getTitle(chatId: UUID, metadata: Map<String, Any>): ConversationTitle? {
    return toConversationTitle(metadata[PROPERTY_NAME__TITLE], chatId)
}

