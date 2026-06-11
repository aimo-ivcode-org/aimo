package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.builder.ChatClientBuilderFactory
import org.ivcode.aimo.core.builder.ConversationFactory
import org.ivcode.aimo.server.model.ChatRequest
import org.ivcode.aimo.server.model.ChatResponse
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.io.OutputStream
import java.util.UUID

@Service
class ChatService (
    private val conversationFactory: ConversationFactory,
    private val chatClientFactory: ChatClientBuilderFactory,
    private val mapper: ObjectMapper,
) {
    fun chat (chatId: UUID, request: ChatRequest, context: Map<String, Any>, output: OutputStream, userId: String) {
        // Build conversation with security/audit interceptors (from factory)
        // The factory internally creates ConversationImpl and wraps it with interceptors
        val conversation = conversationFactory.getConversation(chatId, userId)

        // Build chat client with the secure conversation
        val client = chatClientFactory
            .builder(conversation)
            .build()

        // Merge request context with conversation metadata
        val context: MutableMap<String, Any> = HashMap(context)
        context.putAll(conversation.getChatMetadata())

        if (request.stream) {
            chatStream(client, request.toAimoChatRequest(context.toMap()), output)
        } else {
            // Non-streaming: perform a blocking chat call and write the single response
            val response = client.chat(request.toAimoChatRequest(context.toMap()))
            response.toChatResponse().write(output, isNewlineDelimited = true)
        }
    }

    private fun chatStream(client: AimoChatClient, request: AimoChatRequest, output: OutputStream) {
        client.chatStream(request) { response ->
            response.toChatResponse().write(output, isNewlineDelimited = true)
        }
    }

    fun ChatResponse.write(output: OutputStream, isNewlineDelimited: Boolean) {
        val json = mapper.writeValueAsBytes(this)
        output.write(json)
        if (isNewlineDelimited) {
            output.write('\n'.code)
        }
        output.flush()
    }
}