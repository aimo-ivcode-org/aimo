package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.AimoUsage
import org.ivcode.aimo.core.controller.SystemMessageCallback
import org.ivcode.aimo.core.controller.SystemMessageContext
import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.model.AimoToolDefinition
import org.ivcode.aimo.core.toAimoChatMessage
import org.ivcode.aimo.core.util.CONTEXT_KEY__CHAT_ID
import org.ivcode.aimo.core.util.CONTEXT_KEY__CONVERSATION
import org.ivcode.aimo.core.util.CONTEXT_KEY__REQUEST_ID
import java.time.Instant
import java.util.UUID

/**
 * Implementation of [AimoChatClient] responsible for orchestrating chat interactions.
 *
 * This class handles:
 * - **Chat execution**: Processing user prompts and generating responses via the chat model
 * - **Tool handling**: Invoking registered tools when the assistant requests them
 * - **System messages**: Retrieving and preparing system-level prompts via callbacks
 * - **Conversation history**: Reading cached messages and lazy-loading from the DAO
 * - **Prompt budgeting**: Managing context window constraints via the prompt budgeter
 * - **Message persistence**: Delegating all persistence to the conversation client
 *
 * ### Architecture
 * - Delegates cache management to [AimoConversationClient] (single owner)
 * - Reads messages via [conversation.getCachedMessages] and [AimoChatClientDao]
 * - Persists new messages via [conversation.addMessages]
 * - Does not directly manage the session cache
 *
 * ### Tool Handling
 * When the assistant returns tool calls, this class:
 * 1. Deduplicates tool calls by ID
 * 2. Invokes the corresponding [AimoToolCallback]
 * 3. Catches exceptions and wraps them in error messages
 * 4. Streams tool results if a callback is provided
 *
 * ### Message Flow
 * 1. Input: [AimoChatRequest] with user prompt
 * 2. System messages are prepared via callbacks
 * 3. History is fetched (from cache or lazy-loaded from DAO)
 * 4. Prompt budgeter filters history to fit context window
 * 5. Model is called with the resulting prompt
 * 6. If tools are requested, they are invoked in a loop
 * 7. Final response (user + tools + assistant) is persisted via conversation.addMessages()
 * 8. Non-empty messages are returned to the caller
 *
 * @property chatId The conversation ID that this chat client serves
 * @property conversation The conversation client (manages cache and persistence)
 * @property dao Data access layer for durable storage
 * @property model The chat model that generates responses
 * @property systemMessages Callbacks that generate system-level prompts
 */
internal class AimoChatClientImpl (
    override val chatId: UUID,
    private val conversation: AimoConversationClient,
    private val dao: AimoChatClientDao,
    private val model: AimoChatModel,
    tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
) : AimoChatClient {

    // Map tool callbacks by name for O(1) lookup during tool invocation
    private val toolCallbacks: Map<String, AimoToolCallback> = tools.associateBy { it.toolDefinition.name }

    // Tool definitions sent to the model (extracted from callbacks)
    private val toolDefinitions: List<AimoToolDefinition> = toolCallbacks.values.map { it.toolDefinition }

    // Prompt budgeter selected based on model configuration
    // Responsible for filtering history to fit the model's context window
    private val promptBudgeter: PromptBudgeter = when (model.context.budgeterType) {
        org.ivcode.aimo.core.model.AimoPromptBudgeterType.CONTEXT_WINDOW -> ContextWindowPromptBudgeter(
            maxInputTokens = model.context.size,
            excludeThinking = model.context.excludeThinking,
        )

        org.ivcode.aimo.core.model.AimoPromptBudgeterType.NO_OP -> NoOpPromptBudgeter(
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
     * 1. Initialize a response ID and lazy-loading history provider
     * 2. Prepare system messages via registered callbacks
     * 3. Create initial user message from the request prompt
     * 4. Loop while the assistant has not finished or has tool calls:
     *    a. Use the prompt budgeter to select history that fits the context window
     *    b. Call the model with the budgeted prompt
     *    c. If the assistant has tool calls, invoke each tool and add results
     *    d. Accumulate token usage from each model call (for multi-turn tool scenarios)
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
        call: (responseId: UUID, messageId: Int, prompt: AimoPrompt, callback: ((AimoChatResponse) -> Unit)?) -> AimoChatResponse,
    ): AimoChatResponse {
        val responseId = UUID.randomUUID()

        // Try to read cached messages; lazy-load from DAO on cache miss
        var resolvedHistory: List<AimoChatMessage>? = conversation.getCachedMessages()

        // History provider: returns cached history if available, otherwise lazy-loads from DAO
        // The DAO respects character limits strictly. The prompt budgeter uses this to fetch
        // history on-demand as needed for context fitting.
        val historyProvider: (Long?) -> List<AimoChatMessage> = { chars ->
            resolvedHistory ?: (if (chars == null) {
                // Fetch all history
                dao.getChatRequests(chatId)
            } else {
                // Fetch history up to a character limit; DAO respects budget strictly
                dao.getChatRequests(
                    chatId = chatId,
                    maxRequestCharacters = chars.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
            }).flatMap { it.messages.map { m -> m.toAimoChatMessage() } }
                .also {
                    // Memoize loaded history for this request without re-persisting
                    // previously stored messages through the conversation API.
                    resolvedHistory = it
                }
        }

        // Prepare system messages from registered callbacks
        val systemMessages = getSystemMessages(createSystemMessageContext(responseId, request))

        // Create the initial user message from the request prompt
        val promptMessage = createUserMessage(messageId = 1, content = request.prompt)

        // Accumulate all task messages (assistant responses, tool calls, tool results)
        val taskMessages = mutableListOf<AimoChatMessage>()
        var assistantMessage: AimoChatMessage? = null
        var accumulatedUsage: AimoUsage? = null

        // Main chat loop: continue until the assistant finishes (no tool calls)
        while (assistantMessage == null || !assistantMessage.toolCalls.isNullOrEmpty()) {
            val messageId = 2 + taskMessages.size

            // Use the prompt budgeter to select and fit history into the context window
            val engineResponse = promptBudgeter.withPromptForCall(
                systemMessages = systemMessages,
                prompt = promptMessage,
                taskMessages = taskMessages,
                tools = toolCallbacks.values.toList(),
                historyProvider = historyProvider,
                execute = { promptMessages ->
                    // Build the final prompt with budgeted history
                    val prompt = AimoPrompt(
                        tools = toolDefinitions,
                        systemMessages = this.systemMessages,
                        options = null,
                        messages = promptMessages,
                    )
                    // Call the model (either streaming or non-streaming)
                    call(responseId, messageId, prompt, callback)
                }
            )

            // Accumulate usage from this model call
            if (engineResponse.usage != null) {
                accumulatedUsage = if (accumulatedUsage == null) {
                    engineResponse.usage
                } else {
                    accumulatedUsage.copy(
                        inputTokens = (accumulatedUsage.inputTokens ?: 0) + (engineResponse.usage.inputTokens ?: 0),
                        outputTokens = (accumulatedUsage.outputTokens ?: 0) + (engineResponse.usage.outputTokens ?: 0),
                    )
                }
            }

            // Extract the assistant's message from the engine response
            assistantMessage = engineResponse.extractAssistantMessage(messageId)

            // Only add non-empty assistant messages (skip placeholder responses)
            if (!assistantMessage.isEmptyPayload()) {
                taskMessages.add(assistantMessage)
            }

            // If the assistant requested tools, invoke them
            if (!assistantMessage.toolCalls.isNullOrEmpty()) {
                val toolContext = createToolContext(requestId = responseId, request = request)
                val processedToolCallIds = mutableSetOf<String>()

                // Process each tool call (deduplicating by ID)
                assistantMessage.toolCalls.forEach { toolCall ->
                    // Skip duplicate tool calls (same ID invoked twice)
                    if (!processedToolCallIds.add(toolCall.id)) {
                        return@forEach
                    }

                    // Look up the tool callback by name
                    val toolCallback = toolCallbacks[toolCall.name] ?: return@forEach

                    // Invoke the tool and capture its result (or error)
                    val message = try {
                        createToolMessage(
                            messageId = 2 + taskMessages.size,
                            content = toolCallback.call(toolCall.arguments, toolContext),
                            toolName = toolCall.name,
                            toolCallId = toolCall.id,
                        )
                    } catch (e: Exception) {
                        // Wrap exceptions in an error message
                        createToolMessage(
                            messageId = 2 + taskMessages.size,
                            content = "Error: ${e.message}",
                            toolName = toolCall.name,
                            toolCallId = toolCall.id,
                        )
                    }

                    taskMessages.add(message)
                    // Stream the tool result if a callback is provided
                    callback?.onMessage(responseId, message)
                }
            }
        }

        // Persist the new messages (user prompt + all task responses) to durable storage and cache
        val persistedTaskMessages = taskMessages.filterNot { it.isEmptyPayload() }
        val allMessages = listOf(promptMessage) + persistedTaskMessages
        conversation.addMessages(allMessages)

        return AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = persistedTaskMessages,
            createdAt = Instant.now(),
            usage = accumulatedUsage,
        )
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
            createSystemMessage (
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
        return model.chatEngine.call(prompt).normalizeResponse(responseId, messageId)
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
        // Accumulate thinking and content chunks
        val thinkingBuilder = StringBuilder()
        val contentBuilder = StringBuilder()
        var terminalChunkEmitted = false
        // Use a mutable holder to work around Kotlin smart cast limitations in closures
        val accumulatedStreamUsageHolder = mutableMapOf<String, AimoUsage?>("value" to null)

        // Callback invoked for each chunk from the stream
        val streamCallback: (AimoChatResponse) -> Unit = { streamResponse ->
            val streamMessage = streamResponse.extractAssistantMessage(messageId)
            // Accumulate thinking chunks
            if (!streamMessage.thinking.isNullOrEmpty()) thinkingBuilder.append(streamMessage.thinking)
            // Accumulate content chunks
            if (!streamMessage.content.isNullOrEmpty()) contentBuilder.append(streamMessage.content)
            // Track if a terminal chunk (done=true) was emitted
            if (streamMessage.done == true) {
                terminalChunkEmitted = true
            }
            // Accumulate usage from each chunk
            if (streamResponse.usage != null) {
                val current = accumulatedStreamUsageHolder["value"]
                accumulatedStreamUsageHolder["value"] = if (current == null) {
                    streamResponse.usage
                } else {
                    current.copy(
                        inputTokens = (current.inputTokens ?: 0) + (streamResponse.usage.inputTokens ?: 0),
                        outputTokens = (current.outputTokens ?: 0) + (streamResponse.usage.outputTokens ?: 0),
                    )
                }
            }
            // Emit current state to the caller
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
                    usage = accumulatedStreamUsageHolder["value"],
                )
            )
        }

        // Call the model with the stream callback
        val rawResponse = model.chatEngine.call(prompt, streamCallback)
        val accumulatedStreamUsage = accumulatedStreamUsageHolder["value"]
        val normalizedResponse = rawResponse.normalizeResponse(responseId, messageId)

        // Merge accumulated thinking/content into the final response
        val accThinking = thinkingBuilder.takeIf { it.isNotEmpty() }?.toString()
        val accContent = contentBuilder.takeIf { it.isNotEmpty() }?.toString()
        val aggregatedFinalResponse = if (accThinking == null && accContent == null) {
            normalizedResponse.copy(usage = accumulatedStreamUsage)
        } else {
            normalizedResponse.copy(
                messages = normalizedResponse.messages.map { msg ->
                    msg.copy(
                        thinking = accThinking ?: msg.thinking,
                        content = accContent ?: msg.content,
                    )
                },
                usage = accumulatedStreamUsage,
            )
        }

         // If the model did not emit a terminal chunk, emit one explicitly
         if (!terminalChunkEmitted) {
             val terminalResponse = aggregatedFinalResponse.copy(
                 messages = aggregatedFinalResponse.messages.map { it.copy(done = true) },
                 createdAt = Instant.now(),
             )
             callback?.invoke(terminalResponse)
             return terminalResponse
         }

         return aggregatedFinalResponse
    }

    /**
     * Creates a context object for system message callbacks.
     *
     * @param requestId The unique ID for this request
     * @param request The user's chat request
     * @return SystemMessageContext with merged request context
     */
    private fun createSystemMessageContext(requestId: UUID, request: AimoChatRequest) = SystemMessageContext(
        createContextMap (
            requestId = requestId,
            requestContext = request.context,
        )
    )

    /**
     * Creates a context object for tool callbacks.
     *
     * @param requestId The unique ID for this request
     * @param request The user's chat request
     * @return Context map with request-scoped information
     */
    private fun createToolContext(requestId: UUID, request: AimoChatRequest): Map<String, Any> {
        return createContextMap(
            requestId = requestId,
            requestContext = request.context,
        )
    }

    /**
     * Builds a unified context map for both system and tool callbacks.
     *
     * Combines core AIMO context (chatId, requestId, conversation) with user-provided request context.
     * Reserved internal keys are always set to their correct values, preventing caller-provided context
     * from tampering with critical system state.
     *
     * @param requestId The unique ID for this request
     * @param requestContext Optional caller-provided context (merged into result, but cannot override reserved keys)
     * @return Map with all context variables
     */
    private fun createContextMap(requestId: UUID, requestContext: Map<String, Any>?): Map<String, Any> {
        val context = mutableMapOf<String, Any>()

        // Merge caller-provided context first
        requestContext?.let { context.putAll(it) }

        // Override with reserved internal keys to prevent caller from tampering
        context[CONTEXT_KEY__CHAT_ID] = chatId
        context[CONTEXT_KEY__REQUEST_ID] = requestId
        context[CONTEXT_KEY__CONVERSATION] = conversation

        return context
    }

    /**
     * Extension function to invoke a streaming callback with a new message.
     *
     * Wraps a single message in an [AimoChatResponse].
     *
     * @param responseId The response ID for this update
     * @param message The message to stream
     */
    fun ((AimoChatResponse)->Unit).onMessage(responseId: UUID, message: AimoChatMessage) {
        invoke(AimoChatResponse(
            chatId = chatId,
            responseId = responseId,
            messages = listOf(message),
            createdAt = Instant.now(),
        ))
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
     * @param responseId The response ID to assign
     * @param messageId The message ID to assign
     * @return Normalized response with only the assistant message
     */
    private fun AimoChatResponse.normalizeResponse(responseId: UUID, messageId: Int): AimoChatResponse {
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
}
