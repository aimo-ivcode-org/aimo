package org.ivcode.aimo.core.chatservice

/**
 * Callback contract for generating system messages in chat prompts.
 *
 * Each system message callback carries its scope restrictions as a first-class property.
 * Scopes are computed at callback creation time from @SystemMessage annotations and
 * parent @ChatService scopes. An empty [scopes] set indicates the message is available
 * to all scopes (global message).
 */
interface SystemMessageCallback {
    val name: String
    val scopes: Set<String>
    fun call(context: SystemMessageContext): String?
}

