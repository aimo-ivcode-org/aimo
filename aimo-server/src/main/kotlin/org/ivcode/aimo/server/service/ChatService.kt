package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.chatclient.AimoChatClient
import org.ivcode.aimo.core.chatclient.ChatClientProvider
import org.ivcode.aimo.core.model.AimoChatModelFactory
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.conversation.ConversationFactory
import org.ivcode.aimo.core.model.AimoChatRequest
import org.ivcode.aimo.server.exceptions.NotFoundException
import org.ivcode.aimo.server.model.ChatRequest
import org.ivcode.aimo.server.model.ChatResponse
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.io.OutputStream
import java.util.UUID

@Service
class ChatService (
    private val conversationFactory: ConversationFactory,
    private val chatClientFactory: ChatClientProvider,
    private val chatModelFactory: AimoChatModelFactory,
    private val mapper: ObjectMapper,
) {
    fun chat (chatId: UUID, request: ChatRequest, context: Map<String, Any>, output: OutputStream) {
         val conversation = conversationFactory.getConversation(chatId)
             ?: throw NotFoundException("Conversation not found: chatId=$chatId")

         // Resolve primary model (cached singleton bean; no per-request iteration)
         val primaryModel = try {
             chatModelFactory.getPrimaryModel()
         } catch (e: IllegalStateException) {
             throw IllegalStateException("No primary model configured", e)
         }

         val client = chatClientFactory.createClient(
             model = primaryModel,
             conversation = conversation
         )

         val mergedContext: MutableMap<String, Any> = HashMap(context)
         mergedContext.putAll(conversation.getChatMetadata())

         if (request.stream) {
             chatStream(client, request.toAimoChatRequest(mergedContext.toMap()), output)
         } else {
             val response = client.chat(request.toAimoChatRequest(mergedContext.toMap()))
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
