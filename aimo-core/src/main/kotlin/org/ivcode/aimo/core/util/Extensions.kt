package org.ivcode.aimo.core.util

import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.ivcode.aimo.core.conversation.Conversation
import java.util.UUID

internal const val CONTEXT_KEY__CHAT_ID = "chatId"
internal const val CONTEXT_KEY__REQUEST_ID = "requestId"
internal const val CONTEXT_KEY__CONVERSATION = "conversation-client"

fun SystemMessageContext.getChatId(): UUID? = this.context[CONTEXT_KEY__CHAT_ID] as? UUID
fun Map<String, Any>.getChatId(): UUID? = this[CONTEXT_KEY__CHAT_ID] as? UUID

fun SystemMessageContext.getRequestId(): UUID? = this.context[CONTEXT_KEY__REQUEST_ID] as? UUID
fun Map<String, Any>.getRequestId(): UUID? = this[CONTEXT_KEY__REQUEST_ID] as? UUID

fun SystemMessageContext.getConversationClient(): Conversation? =
    this.context[CONTEXT_KEY__CONVERSATION] as? Conversation

fun Map<String, Any>.getConversationClient(): Conversation? =
    this[CONTEXT_KEY__CONVERSATION] as? Conversation
