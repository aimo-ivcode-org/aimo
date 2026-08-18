package org.ivcode.aimo.server.service

import org.ivcode.aimo.core.chatclient.AimoChatClient
import org.ivcode.aimo.core.chatclient.ChatClientProvider
import org.ivcode.aimo.core.model.AimoChatModelProviderFactory
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
    private val chatModelFactories: Map<String, AimoChatModelProviderFactory>,
    private val mapper: ObjectMapper,
) {
    fun chat (chatId: UUID, request: ChatRequest, context: Map<String, Any>, output: OutputStream) {
         val conversation = conversationFactory.getConversation(chatId)
             ?: throw NotFoundException("Conversation not found: chatId=$chatId")

         // Resolve primary model with strict invariant: exactly one global primary if multiple models exist
         val primaryModel = selectPrimaryModel()
             ?: throw IllegalStateException("No primary model configured")

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

     /**
      * Select the primary model with strict validation:
      * - If exactly one model exists globally, use it (primary flag not required)
      * - If multiple models exist, exactly one must have isPrimary=true
      * - Fails with clear error if requirements not met
      */
     private fun selectPrimaryModel(): AimoChatModelConfig? {
         val factories: List<AimoChatModelProviderFactory> = chatModelFactories.values.toList()

         // Check for models marked as primary (via getPrimaryName)
         val primaryModels: List<AimoChatModelConfig> = factories.mapNotNull { factory ->
             factory.getPrimaryName()?.let { primaryName ->
                 factory.getModel(primaryName) ?: throw IllegalStateException(
                     "Factory '${factory.provider}' reported primary model '$primaryName' but could not load it"
                 )
             }
         }

         // Enforce at most one global primary
         require(primaryModels.size <= 1) {
             "Only one model can be marked primary=true. Found: ${primaryModels.map { it.name }}"
         }

         primaryModels.firstOrNull()?.let { return it }

         // No explicit primary; collect all models
         val allModels: List<AimoChatModelConfig> = factories.flatMap { factory ->
             factory.getNames().map { name ->
                 factory.getModel(name) ?: throw IllegalStateException(
                     "Factory '${factory.provider}' reported model '$name' but could not load it"
                 )
             }
         }

         if (allModels.isEmpty()) return null
         if (allModels.size == 1) return allModels.first()

         // Multiple models without explicit primary: require one to be marked
         throw IllegalStateException(
             "Multiple models configured (${allModels.map { it.name }}) but none marked primary=true"
         )
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
