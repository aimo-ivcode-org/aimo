package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.NoOpAimoSessionCache
import org.ivcode.aimo.core.cache.SessionCacheStats
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
import org.ivcode.aimo.core.util.CONTEXT_KEY__CONVERSATION
import org.ivcode.aimo.core.util.CONTEXT_KEY__REQUEST_ID
import java.time.Instant
import java.util.UUID

internal class AimoChatClientImpl (
    override val chatId: UUID,
    private val conversation: AimoConversationClient,
    private val dao: AimoChatClientDao,
    private val model: AimoChatModel,
    tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val sessionCache: AimoSessionCache = NoOpAimoSessionCache,
    private val promptBudgeterFactory: PromptBudgeterFactory = DefaultPromptBudgeterFactory,
) : AimoChatClient {

    private val cachedTokenCalibration = sessionCache.getTokenCalibration(chatId)
    private val initialObservedPromptCharacters: Long =
        cachedTokenCalibration?.observedPromptCharacters
            ?: conversation.getChatProperty(METADATA_KEY__OBSERVED_PROMPT_CHARACTERS).toNonNegativeLong()
    private val initialObservedPromptTokens: Long =
        cachedTokenCalibration?.observedPromptTokens
            ?: conversation.getChatProperty(METADATA_KEY__OBSERVED_PROMPT_TOKENS).toNonNegativeLong()
    private val toolCallbacks: Map<String, AimoToolCallback> = tools.associateBy { it.toolDefinition.name }
    private val toolDefinitions: List<AimoToolDefinition> = toolCallbacks.values.map { it.toolDefinition }
    private val promptBudgeter: PromptBudgeter = promptBudgeterFactory.create(
        model = model,
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
        var resolvedHistory: List<AimoChatMessage>? = sessionCache.getMessages(chatId)
        val historyProvider: (Long?) -> List<AimoChatMessage> = { chars ->
            resolvedHistory ?: (if (chars == null) {
                dao.getChatRequests(chatId)
            } else {
                dao.getChatRequests(
                    chatId = chatId,
                    maxRequestCharacters = chars.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
            }).flatMap { it.messages.map { m -> m.toAimoChatMessage() } }
                .also {
                    resolvedHistory = it
                    sessionCache.putMessages(chatId, it)
                }
        }

        val systemMessages = getSystemMessages(createSystemMessageContext(responseId, request))
        val promptMessage = createUserMessage(messageId = 1, content = request.prompt)
        val taskMessages = mutableListOf<AimoChatMessage>()
        var assistantMessage: AimoChatMessage? = null
        var accCacheReadTokens = 0L
        var accCacheWriteTokens = 0L

        while (assistantMessage == null || !assistantMessage.toolCalls.isNullOrEmpty()) {
            val messageId = 2 + taskMessages.size
            val promptMessages = promptBudgeter.promptMessagesForCall(
                systemMessages = systemMessages,
                prompt = promptMessage,
                taskMessages = taskMessages,
                tools = toolCallbacks.values.toList(),
                historyProvider = historyProvider,
            )
            val prompt = AimoPrompt(
                tools = toolDefinitions,
                systemMessages = this.systemMessages,
                options = null,
                messages = promptMessages,
            )

            val engineResponse = call(responseId, messageId, prompt, callback)

            // Accumulate prompt-cache usage stats returned by the model.
            accCacheReadTokens += engineResponse.usage?.promptCache?.cacheReadInputTokens?.toLong() ?: 0L
            accCacheWriteTokens += engineResponse.usage?.promptCache?.cacheWriteInputTokens?.toLong() ?: 0L

            assistantMessage = engineResponse.extractAssistantMessage(messageId)
            if (!assistantMessage.isEmptyPayload()) {
                taskMessages.add(assistantMessage)
            }

            if (!assistantMessage.toolCalls.isNullOrEmpty()) {
                val toolContext = createToolContext(requestId = responseId, request = request)
                val processedToolCallIds = mutableSetOf<String>()

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

        persistCacheStats(accCacheReadTokens, accCacheWriteTokens)

        val persistedTaskMessages = taskMessages.filterNot { it.isEmptyPayload() }
        val allMessages = listOf(promptMessage) + persistedTaskMessages
        dao.addChatRequest(ChatRequestEntity(
            chatId = chatId,
            requestId = responseId,
            messages = allMessages.map { it.toChatMessageEntity(responseId) },
            requestCharacters = allMessages.sumOf { it.content?.length ?: 0 },
            createdAt = Instant.now(),
        ))
        sessionCache.appendMessages(chatId, allMessages)

        return AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = persistedTaskMessages,
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
        var terminalChunkEmitted = false

        val streamCallback: (AimoChatResponse) -> Unit = { streamResponse ->
            val streamMessage = streamResponse.extractAssistantMessage(messageId)
            if (!streamMessage.thinking.isNullOrEmpty()) thinkingBuilder.append(streamMessage.thinking)
            if (!streamMessage.content.isNullOrEmpty()) contentBuilder.append(streamMessage.content)
            if (streamMessage.done == true) {
                terminalChunkEmitted = true
            }
            callback?.invoke(
                AimoChatResponse(
                    chatId = chatId,
                    responseId = responseId,
                    messages = listOf(streamMessage.copy(
                        done = streamMessage.done,
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
        val aggregatedFinalResponse = if (accThinking == null && accContent == null) {
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

        // If no terminal chunk was emitted, explicitly emit one final aggregated done event.
        if (!terminalChunkEmitted) {
            val terminalResponse = aggregatedFinalResponse.copy(
                messages = aggregatedFinalResponse.messages.map { it.copy(done = true) },
                createdAt = Instant.now(),
            )
            callback?.invoke(
                terminalResponse
            )
            return terminalResponse
        }

        return aggregatedFinalResponse
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
            CONTEXT_KEY__CONVERSATION to conversation,
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

    private fun AimoChatMessage.isEmptyPayload(): Boolean {
        return content.isNullOrBlank() &&
            thinking.isNullOrBlank() &&
            toolCalls.isNullOrEmpty() &&
            toolName.isNullOrBlank() &&
            toolCallId.isNullOrBlank()
    }


    private fun Any?.toNonNegativeLong(): Long {
        return when (this) {
            is Number -> toLong().coerceAtLeast(0)
            is String -> toLongOrNull()?.coerceAtLeast(0) ?: 0
            else -> 0
        }
    }

    private fun persistCacheStats(cacheReadTokens: Long, cacheWriteTokens: Long) {
        if (cacheReadTokens <= 0L && cacheWriteTokens <= 0L) return
        val current = sessionCache.getCacheStats(chatId) ?: SessionCacheStats()
        sessionCache.putCacheStats(
            chatId,
            current.copy(
                totalCacheReadTokens = current.totalCacheReadTokens + cacheReadTokens,
                totalCacheWriteTokens = current.totalCacheWriteTokens + cacheWriteTokens,
            )
        )
    }

    private companion object {
        const val METADATA_KEY__OBSERVED_PROMPT_CHARACTERS = "chat.inputTokenBudgeter.observedPromptCharacters"
        const val METADATA_KEY__OBSERVED_PROMPT_TOKENS = "chat.inputTokenBudgeter.observedPromptTokens"
    }
}


