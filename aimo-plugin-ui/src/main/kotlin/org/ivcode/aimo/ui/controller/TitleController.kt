package org.ivcode.aimo.ui.controller

import org.ivcode.aimo.core.conversation.ConversationFactory
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.server.consts.API_CONTROLLER_CONTEXT
import org.ivcode.aimo.server.exceptions.NotFoundException
import org.ivcode.aimo.ui.chatcontroller.TitleChatController
import org.ivcode.aimo.ui.model.ConversationTitle
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST endpoints for reading and manually updating chat titles.
 */
@RestController
@RequestMapping("/$API_CONTROLLER_CONTEXT/title")
class TitleController constructor(
    private val conversationFactory: ConversationFactory,
    private val conversationStore: AimoChatClientDao,
    private val titleChatController: TitleChatController,
) {

    @GetMapping("/{chatId}")
    fun getTitle(
        @PathVariable chatId: UUID
    ): ConversationTitle? {
        val conversation = conversationFactory.getConversation(chatId)
            ?: throw NotFoundException("Conversation not found: chatId=$chatId")
        return titleChatController.getTitle(conversation)
    }

    @GetMapping("/")
    fun getTitles(): List<ConversationTitle> {
        return conversationStore.getChatConversations().mapNotNull { entity ->
            titleChatController.getTitle(entity.chatId, entity.metadata)
        }
    }

    @PutMapping("/{chatId}/{title}")
    fun setTitle(
        @PathVariable chatId: UUID,
        @PathVariable title: String
    ) {
        val conversation = conversationFactory.getConversation(chatId)
            ?: throw NotFoundException("Conversation not found: chatId=$chatId")
        titleChatController.setTitle(title, conversation, "USER")
    }
}
