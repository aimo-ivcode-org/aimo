package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.chatscope.ChatScope
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.model.AimoChatMessage
import org.ivcode.aimo.core.model.AimoChatMessageType
import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoChatRequest
import org.ivcode.aimo.core.model.AimoChatResponse
import org.ivcode.aimo.core.model.AimoToolCall
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoPromptBudgeterType
import org.ivcode.aimo.core.model.AimoUsage
import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
import org.ivcode.aimo.core.util.CONTEXT_KEY__CHAT_ID
import org.ivcode.aimo.core.util.CONTEXT_KEY__CONVERSATION
import org.ivcode.aimo.core.util.CONTEXT_KEY__REQUEST_ID
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import kotlin.collections.orEmpty

/**
 * Implementation of [AimoChatClient] responsible for orchestrating chat interactions.
 *
 * This class handles:
 * - **Chat execution**: Processing user prompts and generating responses via the chat model
 * - **Tool handling**: Invoking registered tools when the assistant requests them
 * - **System messages**: Retrieving and preparing system-level prompts via callbacks
 * - **Conversation history**: Reading conversation history from cache/session storage
 * - **Prompt budgeting**: Managing context window constraints via the prompt budgeter
 * - **Message persistence**: Delegating all persistence to the conversation client
 *
 * ### Architecture
 * - Delegates cache and history management to [org.ivcode.aimo.core.conversation.Conversation] (single owner)
 * - Reads messages via conversation.getMessages
 * - Persists new messages via conversation.addMessages
 * - Does not directly manage the session cache or DAO
 *
 * ### Tool Handling
 * When the assistant returns tool calls, this class:
 * 1. Deduplicates tool calls by ID
 * 2. Invokes the corresponding [org.ivcode.aimo.core.model.AimoToolCallback]
 * 3. Catches exceptions and wraps them in error messages
 * 4. Streams tool results if a callback is provided
 *
 * ### Message Flow
 * 1. Input: [org.ivcode.aimo.core.AimoChatRequest] with user prompt
 * 2. System messages are prepared via callbacks (from chat scope)
 * 3. History is fetched (from cache or lazy-loaded from DAO)
 * 4. Prompt budgeter filters history to fit context window
 * 5. Model is called with the resulting prompt
 * 6. If tools are requested, they are invoked in a loop
 * 7. Final response (user + tools + assistant) is persisted via conversation.addMessages()
 * 8. Non-empty messages are returned to the caller
 *
 * @property chatId The conversation ID that this chat client serves
 * @property conversation The conversation client (manages cache, persistence, and message fetching)
 * @property model The chat model that generates responses
 * @property chatScope The chat scope defining available tools and system messages
 */
internal class AimoChatClientImpl (
    override val chatId: UUID,
    private val conversation: Conversation,
    private val model: AimoChatModelConfig,
    private val chatScope: ChatScope,
) : AimoChatClient {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val requestContextFactory = ChatRequestContextFactory(chatId, conversation, chatScope.id)

    // Fetch all tools once to ensure consistency between toolCallbacks and toolDefinitions.
    // Uses getAllTools() (not the static `tools` field) so  provider-sourced tools
    // (e.g. MCP server tools) are included alongside statically registered ones.
    // Called once at initialization to avoid concurrent changes creating inconsistencies.
    private val allToolCallbacks: List<ToolCallback> = chatScope.getAllTools().also { tools ->
        logger.info("AimoChatClientImpl initialized with {} tools for chat {}", tools.size, chatId)
        tools.forEach { tool -> logger.debug("  Tool: {}", tool.toolDefinition.name) }
    }

    // Map tool callbacks by name for O(1) lookup during tool invocation.
    private val toolCallbacks: Map<String, ToolCallback> = allToolCallbacks.associateBy { it.toolDefinition.name }

    // Tool definitions sent to the model (extracted from callbacks)
    private val toolDefinitions: List<ToolDefinition> = allToolCallbacks.map { it.toolDefinition }

    // System messages from the scope (includes provider-sourced messages)
    private val systemMessages: List<SystemMessageCallback> = chatScope.getAllSystemMessages()

    // Prompt budgeter selected based on model configuration
    // Responsible for filtering history to fit the model's context window
    private val promptBudgeter: PromptBudgeter = when (model.context.budgeterType) {
        AimoPromptBudgeterType.CONTEXT_WINDOW -> ContextWindowPromptBudgeter(
            maxInputTokens = model.context.size,
            excludeThinking = model.context.excludeThinking,
        )

        AimoPromptBudgeterType.NO_OP -> NoOpPromptBudgeter(
            excludeThinking = model.context.excludeThinking,
        )
    }

    /**
     * Non-streaming chat endpoint.
     * Delegates to [doChat] with a non-streaming call function.
     *
     * @param request The chat request containing the user prompt and optional context
     * @return A response with the assistant's messages (without streaming updates)
     */
    override fun chat(request: AimoChatRequest): AimoChatResponse {
        return doChat(request, null, this::call)
    }

    /**
     * Streaming chat endpoint.
     * Delegates to [doChat] with a streaming call function and a callback for incremental updates.
     *
     * @param request The chat request containing the user prompt and optional context
     * @param callback Invoked for each incremental update as the model streams the response
     * @return The final aggregated response after streaming completes
     */
    override fun chatStream (
        request: AimoChatRequest,
        callback: (AimoChatResponse) -> Unit
    ): AimoChatResponse {
        return doChat(request, callback, this::stream)
    }

     /**
      * Core chat orchestration logic shared by both streaming and non-streaming endpoints.
      *
      * ### Algorithm
      * 1. Initialize a response ID and load conversation history from cache
      *    (seeded on first call to budgeter's maxContextSize)
      * 2. Prepare system messages via registered callbacks
      * 3. Create initial user message from the request prompt
      * 4. Loop while the assistant has not finished or has tool calls:
      *    a. Fetch cached history (or lazy-seed from DAO if empty)
      *    b. Pass history to the prompt budgeter to select messages that fit the context window
      *    c. Call the model with the budgeted prompt
      *    d. If the assistant has tool calls, invoke each tool and add results
      *    e. Accumulate token usage from each model call (for multi-turn tool scenarios)
      * 5. Persist all new messages (prompt + tasks) via the conversation
      * 6. Return non-empty task messages to the caller with accumulated usage
      *
      * @param request The chat request (prompt + optional context)
      * @param callback Optional callback for streaming updates (null for non-streaming)
      * @param call Function reference to either [call] (non-streaming) or [stream] (streaming)
      * @return The final response with assistant messages and accumulated token usage
      */
      private fun doChat (
         request: AimoChatRequest,
         callback: ((AimoChatResponse) -> Unit)? = null,
         call: (
             responseId: UUID,
             messageId: Int,
             prompt: AimoPrompt,
             callback: ((AimoChatResponse) -> Unit)?
         ) -> AimoChatResponse,
      ): AimoChatResponse {
         val responseId = UUID.randomUUID()
         val runContext = ChatRunContext(
             responseId = responseId,
             request = request,
             callback = callback,
             call = call,
             systemPromptMessages = getSystemMessages(
                 requestContextFactory.createSystemMessageContext(responseId, request)
             ),
             promptMessage = createUserMessage(messageId = 1, content = request.prompt),
             history = conversation.getMessages(maxCacheCharacters = promptBudgeter.maxContextSize).orEmpty(),
         )
         val state = ChatExecutionState()

         // Continue model turns until an assistant message arrives without tool calls.
         while (shouldContinueChat(state.assistantMessage)) {
             executeChatTurn(runContext, state)
         }

         val persistedTaskMessages = persistTaskMessages(
             responseId = responseId,
             promptMessage = runContext.promptMessage,
             taskMessages = state.taskMessages,
         )
         return AimoChatResponse(
             chatId = chatId,
             responseId = responseId,
             messages = persistedTaskMessages,
             createdAt = Instant.now(),
             usage = state.accumulatedUsage,
         )
    }

    /** Holds immutable request-scoped inputs used across chat turns. */
    private data class ChatRunContext(
        val responseId: UUID,
        val request: AimoChatRequest,
        val callback: ((AimoChatResponse) -> Unit)?,
        val call: (
            responseId: UUID,
            messageId: Int,
            prompt: AimoPrompt,
            callback: ((AimoChatResponse) -> Unit)?
        ) -> AimoChatResponse,
        val systemPromptMessages: List<AimoChatMessage>,
        val promptMessage: AimoChatMessage,
        val history: List<AimoChatMessage>,
    )

    /** Tracks mutable state for a multi-turn chat exchange. */
    private data class ChatExecutionState(
        val taskMessages: MutableList<AimoChatMessage> = mutableListOf(),
        var assistantMessage: AimoChatMessage? = null,
        var accumulatedUsage: AimoUsage? = null,
    )

    /** Executes one model turn and handles any tool calls requested by the assistant. */
    private fun executeChatTurn(context: ChatRunContext, state: ChatExecutionState) {
        val messageId = 2 + state.taskMessages.size
        val engineResponse = callModelForTurn(context, state.taskMessages, messageId)
        state.accumulatedUsage = mergeUsage(state.accumulatedUsage, engineResponse.usage)

        val assistantMessage = engineResponse.extractAssistantMessage(messageId)
        state.assistantMessage = assistantMessage
        if (!assistantMessage.isEmptyPayload()) {
            state.taskMessages.add(assistantMessage)
        }

        processToolCalls(context, state.taskMessages, assistantMessage)
    }

    /** Runs prompt budgeting and calls either streaming or non-streaming model execution. */
    private fun callModelForTurn(
        context: ChatRunContext,
        taskMessages: List<AimoChatMessage>,
        messageId: Int,
    ): AimoChatResponse {
        return promptBudgeter.withPromptForCall(
            systemMessages = context.systemPromptMessages,
            prompt = context.promptMessage,
            taskMessages = taskMessages,
            tools = toolCallbacks.values.toList(),
            history = context.history,
            execute = { promptMessages ->
                val prompt = AimoPrompt(
                    tools = toolDefinitions,
                    systemMessages = this.systemMessages,
                    options = null,
                    messages = promptMessages,
                )
                context.call(context.responseId, messageId, prompt, context.callback)
            }
        )
    }

    /** Handles assistant-requested tool calls and streams tool messages when a callback exists. */
    private fun processToolCalls(
        context: ChatRunContext,
        taskMessages: MutableList<AimoChatMessage>,
        assistantMessage: AimoChatMessage,
    ) {
        val toolCalls = assistantMessage.toolCalls.orEmpty()
        if (toolCalls.isEmpty()) {
            return
        }

        val toolContext = requestContextFactory.createToolContext(
            requestId = context.responseId,
            request = context.request,
        )
        val processedToolCallIds = mutableSetOf<String>()

        toolCalls.forEach { toolCall ->
            // Deduplicate repeated tool call IDs to avoid executing the same side-effect twice.
            if (!processedToolCallIds.add(toolCall.id)) {
                return@forEach
            }
            val message = buildToolResponseMessage(toolCall, toolContext, taskMessages.size)
            taskMessages.add(message)
            context.callback?.onMessage(chatId, context.responseId, message)
        }
    }

    /** Builds a tool response message from either a successful callback or a structured error. */
    private fun buildToolResponseMessage(
        toolCall: AimoToolCall,
        toolContext: Map<String, Any>,
        taskMessageCount: Int,
    ): AimoChatMessage {
        val messageId = 2 + taskMessageCount
        val toolCallback = toolCallbacks[toolCall.name]
        if (toolCallback == null) {
            return createToolMessage(
                messageId = messageId,
                content = "Error: Tool '${toolCall.name}' is not available",
                toolName = toolCall.name,
                toolCallId = toolCall.id,
            )
        }

        return try {
            createToolMessage(
                messageId = messageId,
                content = toolCallback.call(toolCall.arguments, toolContext),
                toolName = toolCall.name,
                toolCallId = toolCall.id,
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            createToolMessage(
                messageId = messageId,
                content = "Error: ${e.message}",
                toolName = toolCall.name,
                toolCallId = toolCall.id,
            )
        }
    }

    /** Persists prompt plus non-empty task messages through the conversation abstraction. */
    private fun persistTaskMessages(
        responseId: UUID,
        promptMessage: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
    ): List<AimoChatMessage> {
        val persistedTaskMessages = taskMessages.filterNot { it.isEmptyPayload() }
        val allMessages = listOf(promptMessage) + persistedTaskMessages
        conversation.addMessages(responseId, allMessages, maxCacheCharacters = promptBudgeter.maxContextSize)
        return persistedTaskMessages
    }

    /**
     * Retrieves generated system messages by invoking all registered system message callbacks.
     *
     * @param context Context containing request and conversation information
     * @return List of system messages, in order, with messageId = 0
     */
    private fun getSystemMessages(context: SystemMessageContext) : List<AimoChatMessage> {
        return systemMessages.mapNotNull { callback ->
            callback.call(context)
        }.map {
            createSystemMessage(
                messageId = 0,
                content = it
            )
        }
    }

    /**
     * Non-streaming model call: invokes the model without streaming.
     *
     * @param responseId The unique ID for this response
     * @param messageId The message ID to assign to the response
     * @param prompt The prompt to send to the model
     * @param callback Not used for non-streaming calls (ignored)
     * @return Normalized response with the assistant's message
     */
    @Suppress("UNUSED_PARAMETER")
    private fun call(
        responseId: UUID,
        messageId: Int,
        prompt: AimoPrompt,
        callback: ((AimoChatResponse) -> Unit)?
    ): AimoChatResponse {
        return model.chatEngine.call(prompt).normalizeResponse(chatId, responseId, messageId)
    }

    /**
     * Streaming model call: invokes the model with streaming, accumulating the response.
     *
     * Messages are streamed via the callback. After streaming completes, thinking and content
     * are merged from the accumulated chunks into the final response message.
     *
     * ### Behavior
     * - If no terminal chunk was emitted by the model, an explicit done event is emitted
     * - Thinking and content are accumulated separately and merged into the final message
     * - Token usage is accumulated across all stream chunks (for multi-turn scenarios)
     *
     * @param responseId The unique ID for this response
     * @param messageId The message ID to assign to the response
     * @param prompt The prompt to send to the model
     * @param callback Invoked for each chunk as it arrives from the stream
     * @return Aggregated final response with all thinking/content merged and accumulated usage
     */
    private fun stream(
        responseId: UUID,
        messageId: Int,
        prompt: AimoPrompt,
        callback: ((AimoChatResponse) -> Unit)?
    ): AimoChatResponse {
        val streamState = StreamAggregationState()
        val streamCallback = createStreamCallback(chatId, responseId, messageId, callback, streamState)
        val rawResponse = model.chatEngine.call(prompt, streamCallback)
        val normalizedResponse = rawResponse.normalizeResponse(chatId, responseId, messageId)
        val aggregatedFinalResponse = aggregateStreamResponse(normalizedResponse, streamState)

        emitTerminalChunkIfMissing(
            chatId = chatId,
            responseId = responseId,
            messageId = messageId,
            callback = callback,
            streamState = streamState,
            aggregatedFinalResponse = aggregatedFinalResponse,
        )
        return aggregatedFinalResponse
    }

}

/** Captures mutable aggregation state while stream chunks are emitted by the model. */
private class StreamAggregationState {
    val thinkingBuilder: StringBuilder = StringBuilder()
    val contentBuilder: StringBuilder = StringBuilder()
    var terminalChunkEmitted: Boolean = false
    var accumulatedUsage: AimoUsage? = null
}

/** Returns true while the assistant still has pending work (initial turn or tool calls). */
private fun shouldContinueChat(assistantMessage: AimoChatMessage?): Boolean {
    return assistantMessage == null || !assistantMessage.toolCalls.isNullOrEmpty()
}

/** Adds token usage from one model call onto the accumulated usage snapshot. */
private fun mergeUsage(current: AimoUsage?, update: AimoUsage?): AimoUsage? {
    return when {
        update == null -> current
        current == null -> update
        else -> current.copy(
            inputTokens = current.inputTokens.addNullAware(update.inputTokens),
            outputTokens = current.outputTokens.addNullAware(update.outputTokens),
            // promptCache is a point-in-time cache state, so keep the latest value.
            promptCache = update.promptCache ?: current.promptCache,
        )
    }
}

/** Creates a stream callback that accumulates chunk content and forwards progressive updates. */
private fun createStreamCallback(
    chatId: UUID,
    responseId: UUID,
    messageId: Int,
    callback: ((AimoChatResponse) -> Unit)?,
    streamState: StreamAggregationState,
): (AimoChatResponse) -> Unit {
    return { streamResponse ->
        val streamMessage = streamResponse.extractAssistantMessage(messageId)
        streamMessage.thinking?.let(streamState.thinkingBuilder::append)
        streamMessage.content?.let(streamState.contentBuilder::append)
        if (streamMessage.done == true) {
            streamState.terminalChunkEmitted = true
        }
        streamState.accumulatedUsage = mergeUsage(streamState.accumulatedUsage, streamResponse.usage)
        callback?.invoke(createPartialStreamResponse(chatId, responseId, streamMessage, streamState))
    }
}

/** Builds a progressive streaming response containing accumulated thinking/content. */
private fun createPartialStreamResponse(
    chatId: UUID,
    responseId: UUID,
    streamMessage: AimoChatMessage,
    streamState: StreamAggregationState,
): AimoChatResponse {
    return AimoChatResponse(
        chatId = chatId,
        responseId = responseId,
        messages = listOf(
            streamMessage.copy(
                done = streamMessage.done,
                thinking = streamState.thinkingBuilder.takeIf { it.isNotEmpty() }?.toString(),
                content = streamState.contentBuilder.takeIf { it.isNotEmpty() }?.toString(),
            )
        ),
        createdAt = Instant.now(),
        usage = streamState.accumulatedUsage,
    )
}

/** Produces the final aggregated response after stream completion. */
private fun aggregateStreamResponse(
    normalizedResponse: AimoChatResponse,
    streamState: StreamAggregationState,
): AimoChatResponse {
    val finalUsage = streamState.accumulatedUsage ?: normalizedResponse.usage
    val accThinking = streamState.thinkingBuilder.takeIf { it.isNotEmpty() }?.toString()
    val accContent = streamState.contentBuilder.takeIf { it.isNotEmpty() }?.toString()
    return normalizedResponse.copy(
        messages = normalizedResponse.messages.map { msg ->
            msg.copy(
                thinking = accThinking ?: msg.thinking,
                content = accContent ?: msg.content,
                done = true,
            )
        },
        usage = finalUsage,
    )
}

/** Emits a fallback terminal event when the provider stream never emits a done chunk. */
private fun emitTerminalChunkIfMissing(
    chatId: UUID,
    responseId: UUID,
    messageId: Int,
    callback: ((AimoChatResponse) -> Unit)?,
    streamState: StreamAggregationState,
    aggregatedFinalResponse: AimoChatResponse,
) {
    if (streamState.terminalChunkEmitted) {
        return
    }

    val terminalMessage = AimoChatMessage(
        messageId = messageId,
        type = AimoChatMessageType.ASSISTANT,
        content = streamState.contentBuilder.takeIf { it.isNotEmpty() }?.toString(),
        thinking = streamState.thinkingBuilder.takeIf { it.isNotEmpty() }?.toString(),
        toolName = null,
        done = true,
    )
    callback?.invoke(
        AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = listOf(terminalMessage),
            createdAt = Instant.now(),
            usage = aggregatedFinalResponse.usage,
        )
    )
}

/** Emits a single message update wrapped as a response object for streaming clients. */
private fun ((AimoChatResponse)->Unit).onMessage(chatId: UUID, responseId: UUID, message: AimoChatMessage) {
    invoke(
        AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = listOf(message),
            createdAt = Instant.now(),
        )
    )
}

/**
 * Extracts the assistant's message from a model response.
 *
 * Prefers the last ASSISTANT message, falls back to the last message overall.
 * Throws if no messages are present.
 *
 * @receiver The chat response from the model
 * @param messageId The message ID to assign
 * @return The assistant's message with the assigned messageId
 * @throws IllegalStateException If the response contains no messages
 */
private fun AimoChatResponse.extractAssistantMessage(messageId: Int): AimoChatMessage {
    val assistant = messages.lastOrNull { it.type == AimoChatMessageType.ASSISTANT }
        ?: messages.lastOrNull()
        ?: throw IllegalStateException("Model response did not include any messages")
    return assistant.copy(messageId = messageId)
}

/**
 * Normalizes a model response with consistent metadata.
 *
 * Replaces the response ID, message ID, and timestamp while keeping the assistant's message content.
 *
 * @receiver The raw response from the model
 * @param chatId The chat ID to assign to the normalized response
 * @param responseId The response ID to assign
 * @param messageId The message ID to assign
 * @return Normalized response with only the assistant message
 */
private fun AimoChatResponse.normalizeResponse(chatId: UUID, responseId: UUID, messageId: Int): AimoChatResponse {
    return copy(
        chatId = chatId,
        responseId = responseId,
        messages = listOf(extractAssistantMessage(messageId)),
        createdAt = Instant.now(),
    )
}

/**
 * Checks if a message contains any meaningful payload.
 *
 * An empty message has no content, thinking, tool calls, tool name, or tool call ID.
 * Used to filter out placeholder messages that should not be persisted.
 *
 * @receiver The message to check
 * @return true if the message has no meaningful content
 */
private fun AimoChatMessage.isEmptyPayload(): Boolean {
    return content.isNullOrBlank() &&
        thinking.isNullOrBlank() &&
        toolCalls.isNullOrEmpty() &&
        toolName.isNullOrBlank() &&
        toolCallId.isNullOrBlank()
}

/**
 * Aggregates two nullable integer values while preserving null semantics.
 *
 * When aggregating optional token counts, null means "not reported" or "unknown".
 * This function correctly handles:
 * - `null + value` -> `value`
 * - `value + null` -> `value`
 * - `value + value` -> `value + value`
 * - `null + null` -> `null`
 *
 * @param other The value to add to this
 * @return The summed or preserved value while keeping unknown values as null
 */
private fun Int?.addNullAware(other: Int?): Int? = when {
    this != null && other != null -> this + other
    this != null -> this
    other != null -> other
    else -> null
}

private class ChatRequestContextFactory(
    private val chatId: UUID,
    private val conversation: Conversation,
    private val chatScopeId: String,
) {
    fun createSystemMessageContext(requestId: UUID, request: AimoChatRequest) = SystemMessageContext(
        context = createContextMap(
            requestId = requestId,
            requestContext = request.context,
        ),
        chatScopeId = chatScopeId,
    )

    fun createToolContext(requestId: UUID, request: AimoChatRequest): Map<String, Any> {
        return createContextMap(
            requestId = requestId,
            requestContext = request.context,
        )
    }

    private fun createContextMap(requestId: UUID, requestContext: Map<String, Any>?): Map<String, Any> {
        val context = mutableMapOf<String, Any>()

        // Merge caller-provided context first.
        requestContext?.let { context.putAll(it) }

        // Reserved keys are always overwritten to protect request integrity.
        context[CONTEXT_KEY__CHAT_ID] = chatId
        context[CONTEXT_KEY__REQUEST_ID] = requestId
        context[CONTEXT_KEY__CONVERSATION] = conversation

        return context
    }
}
