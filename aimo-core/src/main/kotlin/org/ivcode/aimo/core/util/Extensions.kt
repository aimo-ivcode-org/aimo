package org.ivcode.aimo.core.util

import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.controller.SystemMessageContext
import java.util.UUID

internal const val CONTEXT_KEY__CHAT_ID = "chatId"
internal const val CONTEXT_KEY__REQUEST_ID = "requestId"
internal const val CONTEXT_KEY__CONVERSATION = "conversation-client"

fun SystemMessageContext.getChatId(): UUID? = this.context[CONTEXT_KEY__CHAT_ID] as? UUID
fun Map<String, Any>.getChatId(): UUID? = this[CONTEXT_KEY__CHAT_ID] as? UUID

fun SystemMessageContext.getRequestId(): UUID? = this.context[CONTEXT_KEY__REQUEST_ID] as? UUID
fun Map<String, Any>.getRequestId(): UUID? = this[CONTEXT_KEY__REQUEST_ID] as? UUID

fun SystemMessageContext.getConversationClient(): AimoConversationClient? = this.context[CONTEXT_KEY__CONVERSATION] as? AimoConversationClient
fun Map<String, Any>.getConversationClient(): AimoConversationClient? = this[CONTEXT_KEY__CONVERSATION] as? AimoConversationClient
