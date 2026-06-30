package org.ivcode.aimo.server.controller

import org.ivcode.aimo.server.consts.API_CONTROLLER_CONTEXT
import org.ivcode.aimo.server.model.ChatHistoryRequest
import org.ivcode.aimo.server.service.HistoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/$API_CONTROLLER_CONTEXT/history")
class HistoryController(
    private val historyService: HistoryService,
) {

    @GetMapping("/{chatId}")
    fun getHistory(
        @PathVariable chatId: UUID
    ): List<ChatHistoryRequest> {
        return historyService.getHistory(chatId)
    }
}
