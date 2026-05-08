package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoSessionClient
import org.ivcode.aimo.core.controller.SystemMessageCallback
import org.ivcode.aimo.core.controller.SystemMessageContext
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.ChatRequestEntity
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.model.AimoToolDefinition
import org.ivcode.aimo.core.toAimoChatMessage
import org.ivcode.aimo.core.toChatMessageEntity
import org.ivcode.aimo.core.util.CONTEXT_KEY__CHAT_ID
import org.ivcode.aimo.core.util.CONTEXT_KEY__REQUEST_ID
import org.ivcode.aimo.core.util.CONTEXT_KEY__SESSION
import java.time.Instant
import java.util.UUID

internal class AimoChatClientImpl (
    override val chatId: UUID,
    private val session: AimoSessionClient,
    private val dao: AimoChatClientDao,
    private val model: AimoChatModel,
    tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
) : AimoChatClient {

    private val initialObservedPromptCharacters: Long = session.getProperty(METADATA_KEY__OBSERVED_PROMPT_CHARACTERS).toNonNegativeLong()
    private val initialObservedPromptTokens: Long = session.getProperty(METADATA_KEY__OBSERVED_PROMPT_TOKENS).toNonNegativeLong()
    private val toolCallbacks: Map<String, AimoToolCallback> = tools.associateBy { it.toolDefinition.name }
    private val toolDefinitions: List<AimoToolDefinition> = toolCallbacks.values.map { it.toolDefinition }
    private val inputTokenBudgeter = ChatInputTokenBudgeter(
        maxInputTokens = model.contextSize,
        initialObservedPromptCharacters = initialObservedPromptCharacters,
        initialObservedPromptTokens = initialObservedPromptTokens,
    )

    override fun chat(request: AimoChatRequest): AimoChatResponse {
        return doChat(request, null, this::call)
    }

    override fun chatStream (
        request: AimoChatRequest,
        callback: (AimoChatResponse) -> Unit
    ): AimoChatResponse {
        return doChat(request, callback, this::stream)
    }

    private fun doChat (
        request: AimoChatRequest,
        callback: ((AimoChatResponse) -> Unit)? = null,
        call: (responseId: UUID, messageId: Int, prompt: AimoPrompt, callback: ((AimoChatResponse) -> Unit)?) -> AimoChatResponse,
    ): AimoChatResponse {
        val responseId = UUID.randomUUID()
        val history = dao.getChatRequests(
            chatId = chatId,
            maxRequestCharacters = inputTokenBudgeter.maxRequestCharactersForLookup(),
        ).flatMap { it.messages.map { m -> m.toAimoChatMessage() } }

        val systemMessages = getSystemMessages(createSystemMessageContext(responseId, request))
        val promptMessage = createUserMessage(messageId = 1, content = request.prompt)
        val taskMessages = mutableListOf<AimoChatMessage>()
        val processedToolCallIds = mutableSetOf<String>()
        var assistantMessage: AimoChatMessage? = null

        while (assistantMessage == null || !assistantMessage.toolCalls.isNullOrEmpty()) {
            val messageId = 2 + taskMessages.size
            val promptHistory = inputTokenBudgeter.historyForPrompt(
                systemMessages = systemMessages,
                history = history,
                prompt = promptMessage,
                taskMessages = taskMessages,
                tools = toolCallbacks.values.toList(),
            )
            val promptMessages = systemMessages + promptHistory + promptMessage + taskMessages
            val prompt = AimoPrompt(
                tools = toolDefinitions,
                systemMessages = this.systemMessages,
                options = null,
                messages = promptMessages.withoutThinking(),
            )

            val engineResponse = call(responseId, messageId, prompt, callback)
            assistantMessage = engineResponse.extractAssistantMessage(messageId)
            callback?.invoke(createDoneMessage(responseId, assistantMessage.copy(done = true)))
            taskMessages.add(assistantMessage)

            if (!assistantMessage.toolCalls.isNullOrEmpty()) {
                val toolContext = createToolContext(requestId = responseId, request = request)

                assistantMessage.toolCalls.forEach { toolCall ->
                    if (!processedToolCallIds.add(toolCall.id)) {
                        return@forEach
                    }

                    val toolCallback = toolCallbacks[toolCall.name] ?: return@forEach
                    val message = try {
                        createToolMessage(
                            messageId = 2 + taskMessages.size,
                            content = toolCallback.call(toolCall.arguments, toolContext),
                            toolName = toolCall.name,
                            toolCallId = toolCall.id,
                        )
                    } catch (e: Exception) {
                        createToolMessage(
                            messageId = 2 + taskMessages.size,
                            content = "Error: ${e.message}",
                            toolName = toolCall.name,
                            toolCallId = toolCall.id,
                        )
                    }

                    taskMessages.add(message)
                    callback?.onMessage(responseId, message)
                }
            }
        }

        persistTokenBudgeterCalibration()

        val allMessages = listOf(promptMessage) + taskMessages
        dao.addChatRequest(ChatRequestEntity(
            chatId = chatId,
            requestId = responseId,
            messages = allMessages.map { it.toChatMessageEntity(responseId) },
            requestCharacters = allMessages.sumOf { it.content?.length ?: 0 },
            createdAt = Instant.now(),
        ))

        return AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = taskMessages,
            createdAt = Instant.now(),
        )
    }

    private fun getSystemMessages(context: SystemMessageContext) : List<AimoChatMessage> {
        return systemMessages.mapNotNull { callback ->
            callback.call(context)
        }.map {
            createSystemMessage (
                messageId = 0,
                content = it
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun call(
        responseId: UUID,
        messageId: Int,
        prompt: AimoPrompt,
        callback: ((AimoChatResponse) -> Unit)?
    ): AimoChatResponse {
        return model.chatEngine.call(prompt).normalizeResponse(responseId, messageId)
    }

    private fun stream(
        responseId: UUID,
        messageId: Int,
        prompt: AimoPrompt,
        callback: ((AimoChatResponse) -> Unit)?
    ): AimoChatResponse {
        val thinkingBuilder = StringBuilder()
        val contentBuilder = StringBuilder()

        val streamCallback: (AimoChatResponse) -> Unit = { streamResponse ->
            val streamMessage = streamResponse.extractAssistantMessage(messageId)
            if (!streamMessage.thinking.isNullOrEmpty()) thinkingBuilder.append(streamMessage.thinking)
            if (!streamMessage.content.isNullOrEmpty()) contentBuilder.append(streamMessage.content)
            callback?.invoke(
                AimoChatResponse(
                    chatId = chatId,
                    responseId = responseId,
                    messages = listOf(streamMessage.copy(
                        done = false,
                        thinking = thinkingBuilder.takeIf { it.isNotEmpty() }?.toString(),
                        content = contentBuilder.takeIf { it.isNotEmpty() }?.toString(),
                    )),
                    createdAt = Instant.now(),
                )
            )
        }

        val finalResponse = model.chatEngine.call(prompt, streamCallback).normalizeResponse(responseId, messageId)
        // Merge aggregated thinking/content into the final response message
        val accThinking = thinkingBuilder.takeIf { it.isNotEmpty() }?.toString()
        val accContent = contentBuilder.takeIf { it.isNotEmpty() }?.toString()
        return if (accThinking == null && accContent == null) {
            finalResponse
        } else {
            finalResponse.copy(
                messages = finalResponse.messages.map { msg ->
                    msg.copy(
                        thinking = accThinking ?: msg.thinking,
                        content = accContent ?: msg.content,
                    )
                }
            )
        }
    }

    private fun createSystemMessageContext(requestId: UUID, request: AimoChatRequest) = SystemMessageContext(
        createContextMap (
            requestId = requestId,
            requestContext = request.context,
        )
    )

    private fun createToolContext(requestId: UUID, request: AimoChatRequest): Map<String, Any> {
        return createContextMap(
            requestId = requestId,
            requestContext = request.context,
        )
    }

    private fun createContextMap(requestId: UUID, requestContext: Map<String, Any>?): Map<String, Any> {
        val context = mutableMapOf (
            CONTEXT_KEY__CHAT_ID to chatId.toString(),
            CONTEXT_KEY__REQUEST_ID to requestId,
            CONTEXT_KEY__SESSION to session,
        )

        requestContext?.let { context.putAll(it) }
        return context
    }

    fun ((AimoChatResponse)->Unit).onMessage(responseId: UUID, message: AimoChatMessage) {
        invoke(AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = listOf(message),
            createdAt = Instant.now(),
        ))
    }

    private fun createDoneMessage(responseId: UUID, message: AimoChatMessage): AimoChatResponse {
        return AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = listOf(message),
            createdAt = Instant.now(),
        )
    }

    private fun List<AimoChatMessage>.withoutThinking(): List<AimoChatMessage> {
        return map { message ->
            if (message.thinking == null) {
                message
            } else {
                message.copy(thinking = null)
            }
        }.filterNot { message ->
            message.type == AimoChatMessageType.ASSISTANT
                && message.content.isNullOrBlank()
                && message.toolCalls.isNullOrEmpty()
        }
    }

    private fun AimoChatResponse.extractAssistantMessage(messageId: Int): AimoChatMessage {
        val assistant = messages.lastOrNull { it.type == AimoChatMessageType.ASSISTANT }
            ?: messages.lastOrNull()
            ?: throw IllegalStateException("Model response did not include any messages")
        return assistant.copy(messageId = messageId)
    }

    private fun AimoChatResponse.normalizeResponse(responseId: UUID, messageId: Int): AimoChatResponse {
        return copy(
            chatId = chatId,
            responseId = responseId,
            messages = listOf(extractAssistantMessage(messageId)),
            createdAt = Instant.now(),
        )
    }


    private fun Any?.toNonNegativeLong(): Long {
        return when (this) {
            is Number -> toLong().coerceAtLeast(0)
            is String -> toLongOrNull()?.coerceAtLeast(0) ?: 0
            else -> 0
        }
    }

    private fun persistTokenBudgeterCalibration() {
        val calibration = inputTokenBudgeter.calibration()
        if (calibration.observedPromptTokens <= 0L) {
            return
        }

        session.writeProperty(METADATA_KEY__OBSERVED_PROMPT_CHARACTERS, calibration.observedPromptCharacters)
        session.writeProperty(METADATA_KEY__OBSERVED_PROMPT_TOKENS, calibration.observedPromptTokens)
    }

    private companion object {
        const val METADATA_KEY__OBSERVED_PROMPT_CHARACTERS = "chat.inputTokenBudgeter.observedPromptCharacters"
        const val METADATA_KEY__OBSERVED_PROMPT_TOKENS = "chat.inputTokenBudgeter.observedPromptTokens"
    }

}