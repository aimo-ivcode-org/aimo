package org.ivcode.aimo.server.controller

import org.ivcode.aimo.core.security.AimoUserProvider
import org.ivcode.aimo.server.consts.API_CONTROLLER_CONTEXT
import org.ivcode.aimo.server.model.ChatConversationInfo
import org.ivcode.aimo.server.service.ConversationService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/$API_CONTROLLER_CONTEXT/conversation")
class ConversationController (
    private val conversationService: ConversationService,
    private val userProvider: AimoUserProvider,
) {

    @PostMapping("/")
    fun createChatConversation(): ChatConversationInfo {
        val user = userProvider.getCurrentUser()
        return conversationService.createConversation(user.userId)
    }

    @GetMapping("/")
    fun getChatConversations(): List<ChatConversationInfo> {
        val user = userProvider.getCurrentUser()
        return conversationService.getConversations(user.userId)
    }

    @DeleteMapping("/{chatId}")
    fun deleteChatConversation(
        @PathVariable chatId: UUID
    ) {
        val user = userProvider.getCurrentUser()
        conversationService.deleteConversation(chatId, user.userId)
    }
}
