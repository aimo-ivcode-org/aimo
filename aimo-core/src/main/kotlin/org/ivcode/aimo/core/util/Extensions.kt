package org.ivcode.aimo.core.util

import org.ivcode.aimo.core.AimoSessionClient
import org.ivcode.aimo.core.controller.SystemMessageContext
import java.util.UUID

internal const val CONTEXT_KEY__CHAT_ID = "chatId"
internal const val CONTEXT_KEY__REQUEST_ID = "requestId"
internal const val CONTEXT_KEY__SESSION = "session-client"

fun SystemMessageContext.getChatId(): UUID? = this.context[CONTEXT_KEY__CHAT_ID] as UUID?
fun Map<String, Any>.getChatId(): UUID? = this[CONTEXT_KEY__CHAT_ID] as UUID?

fun SystemMessageContext.getRequestId(): UUID? = this.context[CONTEXT_KEY__REQUEST_ID] as UUID?
fun Map<String, Any>.getRequestId(): UUID? = this[CONTEXT_KEY__REQUEST_ID] as UUID?

fun SystemMessageContext.getSessionClient(): AimoSessionClient? = this.context[CONTEXT_KEY__SESSION] as? AimoSessionClient
fun Map<String, Any>.getSessionClient(): AimoSessionClient? = this[CONTEXT_KEY__SESSION] as? AimoSessionClient
