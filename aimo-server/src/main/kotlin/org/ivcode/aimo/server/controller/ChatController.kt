package org.ivcode.aimo.server.controller

import jakarta.servlet.http.HttpServletRequest
import org.ivcode.aimo.core.security.AimoUserProvider
import org.ivcode.aimo.server.consts.API_CONTROLLER_CONTEXT
import org.ivcode.aimo.server.model.ChatRequest
import org.ivcode.aimo.server.model.RequestMetadata
import org.ivcode.aimo.server.service.ChatService
import org.ivcode.aimo.server.util.PROPERTY_NAME_REQUEST_METADATA
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.util.Collections
import java.util.UUID

@RestController
@RequestMapping("/$API_CONTROLLER_CONTEXT/chat")
class ChatController (
    private val chatClientService: ChatService,
    private val userProvider: AimoUserProvider,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{chatId}", produces = [MediaType.APPLICATION_JSON_VALUE, "application/x-ndjson"])
    fun chat (
        @PathVariable chatId: UUID,
        @RequestBody request: ChatRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<StreamingResponseBody> {
        val requestMetadata = RequestMetadata.from(servletRequest)
        val user = userProvider.getCurrentUser()

        val stream = StreamingResponseBody { output ->
            try {
                // Capture request metadata once and pass a read-only map into chat context.
                chatClientService.chat(chatId, request, mapOf(
                    PROPERTY_NAME_REQUEST_METADATA to requestMetadata
                ), output, user.userId)
            } catch (ex: Exception) {
                log.error("Error while streaming chat for chatId=$chatId", ex)
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(stream)
    }
}
