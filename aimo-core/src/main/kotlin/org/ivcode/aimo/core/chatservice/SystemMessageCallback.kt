package org.ivcode.aimo.core.chatservice

interface SystemMessageCallback {
    fun call(context: SystemMessageContext): String?
}

