package org.ivcode.aimo.server.controller

import org.ivcode.aimo.server.consts.API_CONTROLLER_CONTEXT
import org.ivcode.aimo.server.model.ChatConversationInfo
import org.ivcode.aimo.server.model.ConversationMetadataDeleteRequest
import org.ivcode.aimo.server.model.ConversationMetadataUpdateRequest
import org.ivcode.aimo.server.model.CreateConversationRequest
import org.ivcode.aimo.server.model.ScopedConversationRequest
import org.ivcode.aimo.server.service.ConversationService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/$API_CONTROLLER_CONTEXT/conversation")
class ConversationController (
    private val conversationService: ConversationService,
) {

    @PostMapping("/")
    fun createChatConversation(
        @RequestBody(required = false) request: CreateConversationRequest?,
    ): ChatConversationInfo {
        return conversationService.createConversation(request?.metadata ?: emptyMap())
    }

    @GetMapping("/")
    fun getChatConversations(): List<ChatConversationInfo> {
        return conversationService.getConversations()
    }

    @PostMapping("/query")
    fun queryChatConversations(
        @RequestBody request: ScopedConversationRequest,
    ): List<ChatConversationInfo> {
        return conversationService.getConversations(request.scopeMetadata)
    }

    @GetMapping("/{chatId}")
    fun getChatConversation(
        @PathVariable chatId: UUID,
    ): ChatConversationInfo {
        return conversationService.getConversation(chatId)
    }

    @PostMapping("/{chatId}/query")
    fun queryChatConversation(
        @PathVariable chatId: UUID,
        @RequestBody request: ScopedConversationRequest,
    ): ChatConversationInfo {
        return conversationService.getConversation(chatId, request.scopeMetadata)
    }

    @DeleteMapping("/{chatId}")
    fun deleteChatConversation(
        @PathVariable chatId: UUID,
        @RequestBody(required = false) request: ScopedConversationRequest?,
    ) {
        conversationService.deleteConversation(chatId, request?.scopeMetadata ?: emptyMap())
    }

    @GetMapping("/{chatId}/metadata")
    fun getConversationMetadata(
        @PathVariable chatId: UUID,
    ): Map<String, Any> {
        return conversationService.getMetadata(chatId)
    }

    @PostMapping("/{chatId}/metadata/query")
    fun queryConversationMetadata(
        @PathVariable chatId: UUID,
        @RequestBody request: ScopedConversationRequest,
    ): Map<String, Any> {
        return conversationService.getMetadata(chatId, request.scopeMetadata)
    }

    @PutMapping("/{chatId}/metadata")
    fun upsertConversationMetadata(
        @PathVariable chatId: UUID,
        @RequestBody request: ConversationMetadataUpdateRequest,
    ) {
        conversationService.upsertMetadata(chatId, request.metadata, request.scopeMetadata)
    }

    @DeleteMapping("/{chatId}/metadata")
    fun deleteConversationMetadata(
        @PathVariable chatId: UUID,
        @RequestBody request: ConversationMetadataDeleteRequest,
    ) {
        conversationService.deleteMetadata(chatId, request.keys, request.scopeMetadata)
    }
}
