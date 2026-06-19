package org.ivcode.aimo.ui.chatcontroller

import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.SystemMessage

/**
 * General system message configurations.
 *
 * Scope: Available to all scopes (empty scope = inherit all).
 * To restrict to specific scopes: @ChatService(scope=["admin", "research"])
 */
@ChatService
class GeneralController {

    /**
     * System message defining formatting guidelines.
     *
     * Scope: Available to all scopes.
     * To restrict: @SystemMessage(scope=["admin"])
     */
    @SystemMessage
    val formatting = "Formatted content as Markdown"
}