package org.ivcode.aimo.ui.chatcontroller

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoConversationInfo
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.SystemMessage
import org.ivcode.aimo.core.chatservice.Tool
import org.ivcode.aimo.core.chatservice.ToolParam
import org.ivcode.aimo.core.util.getConversationClient
import org.ivcode.aimo.ui.extentions.getTitle
import org.ivcode.aimo.ui.extentions.setTitle
import org.ivcode.aimo.ui.model.ConversationTitle
import org.ivcode.aimo.ui.model.TitleResponse
import tools.jackson.databind.ObjectMapper
import java.util.UUID

private const val TITLE_TOOL_NAME = "set_title"

/**
 * Handles chat title read/write behavior for both tool-driven (LLM) and user-driven updates.
 *
 * Source semantics:
 * - `USER`: title was set by a user action outside the model tool call.
 * - `ASSISTANT`: title was set by the LLM through the `setTitle` tool.
 *
 * Scope: Available to all scopes (empty scope = inherit all).
 * To restrict to specific scopes, use: @ChatService(scope=["admin", "research"])
 */
@ChatService
class TitleChatController(
    private val objectMapper: ObjectMapper,
) {

    /**
     * System instructions that guide when and how the model should update a chat title.
     *
     * Scope: Available to all scopes.
     * To restrict to specific scopes: @SystemMessage(scope=["admin"])
     */
    @SystemMessage
    fun titleUpdateInstructions(): String  =
        """         
        Use the "$TITLE_TOOL_NAME" tool to update the chat title when it is missing or no longer descriptive.
        Keep titles concise (ideally under 5 words), representative of the conversation, and neither too generic nor
        overly specific to one message. Update the title as the conversation evolves.
        
        A user can set the title externally. Use source "USER" for user-set titles and "ASSISTANT" for LLM-set titles.
        Do not set or overwrite the title if it was already set by the user. The user can set the title using the UI.
        """.trimIndent()


    /**
     * Tool entrypoint used by the LLM to set a conversation title.
     * Returns a [TitleResponse] where `source` is `ASSISTANT`.
     *
     * Scope: Available to all scopes.
     * To restrict to specific scopes: @Tool(..., scope=["admin"])
     */
    @Tool(name = TITLE_TOOL_NAME, description = "Set the chat title with source=ASSISTANT. Returns TitleResponse JSON: { title: string, source: \"USER\" | \"ASSISTANT\" } (USER = user-set, ASSISTANT = LLM-set).")
    fun setTitle(
        @ToolParam(description = "The new title") title: String,
        context: Map<String, Any>
    ): TitleResponse {
        val conversation = context.getConversationClient() ?: throw IllegalStateException("Title cannot be set. No conversation found in context")
        return setTitle(title, conversation, AimoChatMessageType.ASSISTANT.name)
    }

    /**
     * Tool helper that reads the title from the current tool execution context.
     */
    @Tool(name = "getTitle", description = "Gets the title of the conversation.")
    fun getTitle(context: Map<String, Any>): ConversationTitle? {
        return context.getConversationClient()?.getTitle()
    }

    /** Sets title for a Conversation and records a TOOL message for model context. */
    fun setTitle(title: String, conversation: Conversation, source: String = AimoChatMessageType.USER.name): TitleResponse {
        val currentTitle = conversation.getTitle()
        if (currentTitle?.source == AimoChatMessageType.USER.name && source == AimoChatMessageType.ASSISTANT.name) {
            throw IllegalStateException("Cannot overwrite a USER-set title with source ASSISTANT")
        }

        conversation.setTitle(title, source)
        val response = TitleResponse(
            title = title,
            source = source
        )

        // If set by the user, tell the LLM that the title was set
        if (source == AimoChatMessageType.USER.name) {
             conversation.addMessages(
                 requestId = java.util.UUID.randomUUID(),
                 messages = listOf(
                 AimoChatMessage (
                     messageId = 1,
                     type = AimoChatMessageType.TOOL,
                     content = objectMapper.writeValueAsString(response),
                     thinking = null,
                     toolName = TITLE_TOOL_NAME,
                     done = true
                 )
             ))
        }

        return response
    }

    /** Reads the title from a Conversation. */
    fun getTitle(conversation: Conversation): ConversationTitle? {
        return conversation.getTitle()
    }

    /** Reads the title from conversation info. */
    fun getTitle(conversation: AimoConversationInfo): ConversationTitle? {
        return conversation.getTitle()
    }

    /** Reads the title from chatId and metadata map. */
    fun getTitle(chatId: UUID, metadata: Map<String, Any>): ConversationTitle? {
        return org.ivcode.aimo.ui.extentions.getTitle(chatId, metadata)
    }
}
