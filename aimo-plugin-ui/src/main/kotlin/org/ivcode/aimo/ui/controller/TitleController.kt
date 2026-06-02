package org.ivcode.aimo.ui.controller

import org.ivcode.aimo.core.Aimo
import org.ivcode.aimo.core.security.AimoUserProvider
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
 *
 * This controller exists for user-driven title management outside of the LLM tool flow.
 * When a title is updated through [setTitle], it is recorded through [TitleChatController]
 * as a user-set title so it can be preserved from later assistant-driven title changes.
 */
@RestController
@RequestMapping("/$API_CONTROLLER_CONTEXT/title")
class TitleController constructor(
    private val aimo: Aimo,
    private val titleChatController: TitleChatController,
    private val userProvider: AimoUserProvider
) {

    /** Returns the current title metadata for a single conversation. */
    @GetMapping("/{chatId}")
    fun getTitle(
        @PathVariable chatId: UUID
    ): ConversationTitle? {
        val user = userProvider.getCurrentUser()
        val conversationClient = aimo.getConversationClient(chatId, user.userId) ?: throw NotFoundException("Conversation with id $chatId not found or not authorized")
        return titleChatController.getTitle(conversationClient)
    }

    /** Returns title metadata for all conversations that currently have a stored title. */
    @GetMapping("/")
    fun getTitles(): List<ConversationTitle> {
        val user = userProvider.getCurrentUser()
        return aimo.getConversations(user.userId).mapNotNull { conversation ->
            titleChatController.getTitle(conversation)
        }
    }

    /**
     * Manually sets the title for a specific chat.
     *
     * This endpoint is intended for user actions from the UI or other external clients.
     * The title is stored as a user-set title, which means assistant-generated title
     * updates should not overwrite it later.
     */
    @PutMapping("/{chatId}/{title}")
    fun setTitle(
        @PathVariable chatId: UUID,
        @PathVariable title: String
    ) {
        val user = userProvider.getCurrentUser()
        val conversationClient = aimo.getConversationClient(chatId, user.userId) ?: throw NotFoundException("Conversation with id $chatId not found or not authorized")
        titleChatController.setTitle(title, conversationClient)
    }
}

