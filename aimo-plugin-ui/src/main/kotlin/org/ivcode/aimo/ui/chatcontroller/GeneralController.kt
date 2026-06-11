package org.ivcode.aimo.ui.chatcontroller

import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.SystemMessage

@ChatService
class GeneralController {

    @SystemMessage
    val formatting = "Formatted content as Markdown"
}