package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatClient
import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatMessageType
import org.ivcode.aimo.core.AimoChatRequest
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoUsage
import org.ivcode.aimo.core.conversation.Conversation
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.SystemMessageContext
 import org.ivcode.aimo.core.model.AimoChatModelConfig
import org.ivcode.aimo.core.model.AimoPrompt
import org.ivcode.aimo.core.model.AimoToolCallback
import org.ivcode.aimo.core.model.AimoToolDefinition
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
 * - **Conversation history**: Reading conversation history from cache/session storage
 * - **Prompt budgeting**: Managing context window constraints via the prompt budgeter
 * - **Message persistence**: Delegating all persistence to the conversation client
 *
 * ### Architecture
 * - Delegates cache and history management to [Conversation] (single owner)
 * - Reads messages via conversation.getMessages
 * - Persists new messages via conversation.addMessages
 * - Does not directly manage the session cache or DAO
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
 * @property conversation The conversation client (manages cache, persistence, and message fetching)
 * @property model The chat model that generates responses
 * @property systemMessages Callbacks that generate system-level prompts
 * @property chatScopeId The chat scope ID for this client (affects which system messages apply)
 */
internal class AimoChatClientImpl (
    override val chatId: UUID,
    private val conversation: Conversation,
    private val model: AimoChatModelConfig,
    tools: List<AimoToolCallback>,
    private val systemMessages: List<SystemMessageCallback>,
    private val chatScopeId: String? = null,
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
     * 1. Initialize a response ID and load conversation history from cache (seeded on first call to budgeter's maxContextSize)
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
         call: (responseId: UUID, messageId: Int, prompt: AimoPrompt, callback: ((AimoChatResponse) -> Unit)?) -> AimoChatResponse,
     ): AimoChatResponse {
         val responseId = UUID.randomUUID()

         // Prepare system messages from registered callbacks
         val systemMessages = getSystemMessages(createSystemMessageContext(responseId, request))

         // Create the initial user message from the request prompt
         val promptMessage = createUserMessage(messageId = 1, content = request.prompt)

         // Accumulate all task messages (assistant responses, tool calls, tool results)
         val taskMessages = mutableListOf<AimoChatMessage>()
         var assistantMessage: AimoChatMessage? = null
         var accumulatedUsage: AimoUsage? = null

         // Fetch conversation history once
         val history = conversation.getMessages(maxCacheCharacters = promptBudgeter.maxContextSize)

         // Main chat loop: continue until the assistant finishes (no tool calls)
         while (assistantMessage == null || !assistantMessage.toolCalls.isNullOrEmpty()) {
             val messageId = 2 + taskMessages.size


             // Use the prompt budgeter to fit history into the context window
             val engineResponse = promptBudgeter.withPromptForCall(
                 systemMessages = systemMessages,
                 prompt = promptMessage,
                 taskMessages = taskMessages,
                 tools = toolCallbacks.values.toList(),
                 history = history.orEmpty(),
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
                        inputTokens = accumulatedUsage.inputTokens.addNullAware(engineResponse.usage.inputTokens),
                        outputTokens = accumulatedUsage.outputTokens.addNullAware(engineResponse.usage.outputTokens),
                        // promptCache is not accumulated; use the latest value which represents current cache state
                        promptCache = engineResponse.usage.promptCache ?: accumulatedUsage.promptCache,
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

                    // Look up the tool callback by name; if not found, send error to model
                    val toolCallback = toolCallbacks[toolCall.name]
                    val message = if (toolCallback == null) {
                        // Tool not found: create an error message so the model knows this tool is unavailable
                        // and can continue deterministically instead of requesting the same unknown tool again
                        createToolMessage(
                            messageId = 2 + taskMessages.size,
                            content = "Error: Tool '${toolCall.name}' is not available",
                            toolName = toolCall.name,
                            toolCallId = toolCall.id,
                        )
                    } else {
                        // Invoke the tool and capture its result (or error)
                        try {
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
                    }

                    taskMessages.add(message)
                    // Stream the tool result if a callback is provided
                    callback?.onMessage(responseId, message)
                }
            }
        }

        // Persist the new messages (user prompt + all task responses) to durable storage and cache
        // Use responseId as requestId to maintain correlation between live response and history
        val persistedTaskMessages = taskMessages.filterNot { it.isEmptyPayload() }
        val allMessages = listOf(promptMessage) + persistedTaskMessages
        conversation.addMessages(responseId, allMessages, maxCacheCharacters = promptBudgeter.maxContextSize)

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
                        inputTokens = current.inputTokens.addNullAware(streamResponse.usage.inputTokens),
                        outputTokens = current.outputTokens.addNullAware(streamResponse.usage.outputTokens),
                        // promptCache is not accumulated; use the latest value which represents current cache state
                        promptCache = streamResponse.usage.promptCache ?: current.promptCache,
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
         // Usage: prefer accumulated stream usage (from chunks); fall back to engine's final response usage
         // if chunks did not include usage information. If both are present, use accumulated (which may
         // include multi-turn aggregation); if both are absent, result is null.
         val finalUsage = accumulatedStreamUsage ?: normalizedResponse.usage
         val accThinking = thinkingBuilder.takeIf { it.isNotEmpty() }?.toString()
         val accContent = contentBuilder.takeIf { it.isNotEmpty() }?.toString()
         val aggregatedFinalResponse = normalizedResponse.copy(
             messages = normalizedResponse.messages.map { msg ->
                 msg.copy(
                     thinking = accThinking ?: msg.thinking,
                     content = accContent ?: msg.content,
                     done = true, // Ensure final aggregated response is marked done
                 )
             },
             usage = finalUsage,
         )

         // Ensure the client always receives a terminal signal (done=true).
         // Some LLM providers may not emit a terminal chunk in the stream, leaving the client
         // hanging. When this happens, emit an explicit terminal signal with complete usage metrics.
         // If the provider already sent a terminal chunk, do not emit anything else.
         if (!terminalChunkEmitted) {
             val terminalMessage = AimoChatMessage(
                 messageId = messageId,
                 type = AimoChatMessageType.ASSISTANT,
                 content = accContent,
                 thinking = accThinking,
                 toolName = null,
                 done = true,
             )
             callback?.invoke(
                 AimoChatResponse(
                     chatId = chatId,
                     responseId = responseId,
                     messages = listOf(terminalMessage),
                     createdAt = Instant.now(),
                     usage = finalUsage,
                 )
             )
             return aggregatedFinalResponse
         }

         return aggregatedFinalResponse
    }

    /**
     * Creates a context object for system message callbacks.
     *
     * @param requestId The unique ID for this request
     * @param request The user's chat request
     * @return SystemMessageContext with merged request context and chat scope ID
     */
    private fun createSystemMessageContext(requestId: UUID, request: AimoChatRequest) = SystemMessageContext(
        context = createContextMap (
            requestId = requestId,
            requestContext = request.context,
        ),
        chatScopeId = chatScopeId
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

    /**
     * Aggregates two nullable integer values while preserving null semantics.
     *
     * When aggregating optional token counts, null means "not reported" or "unknown".
     * This function correctly handles:
     * - `null + value` → `value` (only one side reported, preserve it)
     * - `value + null` → `value` (only one side reported, preserve it)
     * - `value + value` → `value + value` (both sides reported, sum them)
     * - `null + null` → `null` (neither side reported, stay unknown)
     *
     * This prevents misreporting where treating null as 0 would fabricate data
     * (e.g., multi-turn aggregation incorrectly reporting 0 input tokens when
     * one provider didn't report input tokens at all).
     *
     * @param other The value to add to this
     * @return The sum if both are non-null; the non-null value if only one is present; null if both are absent
     */
    private fun Int?.addNullAware(other: Int?): Int? = when {
        this != null && other != null -> this + other
        this != null -> this
        other != null -> other
        else -> null
    }
}
