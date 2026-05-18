package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.model.AimoToolCallback
import org.slf4j.LoggerFactory
import kotlin.math.ceil

/**
 * The `ContextWindowPromptBudgeter` is responsible for managing the token budget of a chat‑based
 * prompt.  It ensures that the final prompt sent to the language model never exceeds
 * `maxInputTokens`, while preserving as much recent conversational context as possible.
 *
 * The budgeting works by:
 *  1. Estimating the token cost of fixed messages (system, user, task, and tool metadata).
 *  2. Truncating the historical conversation from oldest to newest until the remaining
 *     token budget is satisfied.
 *
 * @property maxInputTokens The maximum number of tokens allowed in the input.
 * @property charsPerToken  Average number of characters that map to one token
 *                          (used for a lightweight token estimation heuristic).
 * @property excludeThinking Whether to ignore the `thinking` field when estimating
 *                          token usage.  When `true` only the actual message
 *                          content is counted.
 */
internal class ContextWindowPromptBudgeter(
    private val maxInputTokens: Int,
    private val charsPerToken: Double = DEFAULT_CHARACTERS_PER_TOKEN,
    private val excludeThinking: Boolean = false,
) : PromptBudgeter {

    private companion object {
        const val DEFAULT_CHARACTERS_PER_TOKEN = 4.0
        private val logger = LoggerFactory.getLogger(ContextWindowPromptBudgeter::class.java)
    }

    /**
     * Internal representation of a budgeting plan.
     *
     * @property history          The subset of historical messages that will be sent.
     * @property promptMessages   The full list of messages that will be included in the model call.
     */
    private data class PromptPlan(
        val history: List<AimoChatMessage>,
        val promptMessages: List<AimoChatMessage>,
    )

    /**
     * Returns the subset of [history] that fits in the remaining token budget after reserving
     * tokens for [systemMessages], [prompt], [taskMessages], and [tools].
     *
     * History is truncated from oldest to newest by scanning newest-first and keeping as much
     * recent context as possible.
     *
     * @param systemMessages System messages included on this model call.
     * @param history Persisted prior conversation messages for this chat.
     * @param prompt Current user prompt message.
     * @param taskMessages Messages generated during the current request loop (assistant/tool).
     * @param tools Tool callbacks available to this model call.
     * @return Chronological subset of [history] that fits within the remaining input budget.
     */
    fun historyForPrompt(
        systemMessages: List<AimoChatMessage>,
        history: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
    ): List<AimoChatMessage> {
        return createPromptPlan(
            systemMessages = systemMessages,
            history = history,
            prompt = prompt,
            taskMessages = taskMessages,
            tools = tools,
        ).history
    }

    /**
     * Builds the list of messages that will be sent to the model.
     *
     * The method obtains a history slice that respects the token budget and then
     * concatenates system messages, the chosen history, the current prompt, and
     * any task messages.  It also removes empty payloads and optionally strips
     * the `thinking` field when `excludeThinking` is `true`.
     *
     * @param systemMessages System messages included on this model call.
     * @param prompt          Current user prompt message.
     * @param taskMessages    Messages generated during the current request loop (assistant/tool).
     * @param tools           Tool callbacks available to this model call.
     * @param historyProvider Function that supplies historical messages based on a
     *                        character limit (used for incremental look‑ups).
     * @return List of messages to be included in the prompt.
     */
    override fun promptMessagesForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
        historyProvider: (Long?) -> List<AimoChatMessage>,
    ): List<AimoChatMessage> {
        val history = historyProvider(maxRequestCharactersForLookup().toLong())
        return createPromptPlan(
            systemMessages = systemMessages,
            history = history,
            prompt = prompt,
            taskMessages = taskMessages,
            tools = tools,
        ).promptMessages
    }

    /**
     * Executes a model call with the constructed prompt.
     *
     * This method constructs a prompt plan by combining system messages, user prompts, task messages, and historical
     * context. It then invokes the provided `execute` lambda with the finalized list of messages and returns the
     * resulting `AimoChatResponse`.
     *
     * @param systemMessages System messages to include in the model call.
     * @param prompt          The current user prompt message.
     * @param taskMessages    Messages generated during the current request loop (e.g., assistant or tool messages).
     * @param tools           Tool callbacks available for this model call.
     * @param historyProvider A function that supplies historical messages based on a character limit for incremental lookups.
     * @param execute         A lambda function that performs the actual model call with the constructed prompt messages.
     * @return The response from the model call as an `AimoChatResponse` object.
     */
    override fun withPromptForCall(
        systemMessages: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
        historyProvider: (chars: Long?) -> List<AimoChatMessage>,
        execute: (promptMessages: List<AimoChatMessage>) -> AimoChatResponse,
    ): AimoChatResponse {
        val history = historyProvider(maxRequestCharactersForLookup().toLong())
        val plan = createPromptPlan(
            systemMessages = systemMessages,
            history = history,
            prompt = prompt,
            taskMessages = taskMessages,
            tools = tools,
        )

        val response = execute(plan.promptMessages)

        return response
    }

    /**
     * Computes the maximum number of characters that can be safely requested
     * from the history lookup service.
     *
     * The calculation is a simple ceiling division of the remaining token budget
     * by the average characters per token.  It is used to limit the size of the
     * history returned by `historyProvider`.
     *
     * @return The maximum number of characters that can be requested.
     */
    private fun maxRequestCharactersForLookup(): Int {
        return ceil(maxInputTokens * charsPerToken).toInt().coerceAtLeast(0)
    }

    /**
     * Keeps the most recent history messages that fit within [tokenBudget].
     *
     * @param history Candidate historical messages.
     * @param tokenBudget Remaining token budget after non-history reservations.
     * @return Chronological history subset constrained by [tokenBudget].
     */
    private fun truncateHistoryByTokens(history: List<AimoChatMessage>, tokenBudget: Int): List<AimoChatMessage> {
        if (tokenBudget <= 0) {
            return emptyList()
        }

        var tokenCount = 0
        val result = mutableListOf<AimoChatMessage>()

        for (message in history.asReversed()) {
            val messageTokens = estimateTokens(messagePayloadForBudgeting(message))
            if (tokenCount + messageTokens > tokenBudget) {
                return result.asReversed() // Return what fits; don't add this message
            }
            result.add(message)
            tokenCount += messageTokens
        }

        return result.asReversed()
    }

    /**
     * Estimates total tokens from textual message content.
     *
     * @param messages Messages to estimate.
     * @return Estimated aggregate token count.
     */
    private fun estimateMessagesTokens(messages: List<AimoChatMessage>): Int {
        return messages.sumOf { estimateTokens(messagePayloadForBudgeting(it)) }
    }

    /**
     * Builds a deterministic string representation of an [AimoChatMessage] used for
     * prompt token budgeting.
     *
     * This method is NOT used for model input directly. Instead, it is used to estimate
     * the approximate token cost of a message before constructing the final prompt.
     *
     * The output includes all fields that may contribute to the serialized request payload:
     *
     * - Message content (`content`)
     * - Optional reasoning content (`thinking`) when enabled
     * - Tool metadata (`toolName`, `toolCallId`)
     * - Tool calls (`toolCalls`) including id, name, and arguments
     *
     * Fields are concatenated without separators. While this is a simpler approach,
     * it may reduce estimation accuracy for cases where fields would naturally merge
     * character sequences.
     *
     * Note: This is a heuristic estimator. Actual tokenization will vary depending on the
     * target model tokenizer, especially for JSON-heavy tool arguments or multilingual text.
     *
     * @param message The chat message to estimate token cost for.
     * @return A structured string used solely for token/character budgeting.
     */
    private fun messagePayloadForBudgeting(message: AimoChatMessage): String {
        return buildString {
            append(message.content.orEmpty())

            if (!excludeThinking) {
                append(message.thinking.orEmpty())
            }

            append(message.toolName.orEmpty())
            append(message.toolCallId.orEmpty())

            message.toolCalls?.forEach { toolCall ->
                append(toolCall.id)
                append(toolCall.name)
                append(toolCall.arguments)
            }
        }
    }

    /**
     * Estimates token count for a collection of tool definitions.
     *
     * Tool schemas can consume a substantial portion of the context window,
     * especially when JSON schemas are verbose.  This method approximates
     * the cost of serializing tool metadata into the request payload.
     *
     * The estimate includes:
     *  - Tool name
     *  - Tool description
     *  - Serialized input schema
     *
     * @param tools Tool callbacks available to the model call.
     * @return Estimated aggregate token count for all tool definitions.
     */
    private fun estimateToolTokens(tools: List<AimoToolCallback>): Int {
        return tools.sumOf { toolCallback ->
            val def = toolCallback.toolDefinition

            // Count explicit fields serialized for tool context.
            estimateTokens(def.name) +
                estimateTokens(def.description ?: "") +
                estimateTokens(def.inputSchema.toString())
        }
    }

    /**
     * Estimates token usage using a lightweight character-based heuristic.
     *
     * The estimator assumes that one token corresponds to approximately
     * `charsPerToken` characters.  While less accurate than tokenizer-specific
     * counting, this approach is fast, allocation-free, and model-agnostic.
     *
     * The estimate is rounded upward to avoid accidentally exceeding the
     * configured context window.
     *
     * @param text Text fragment to estimate.
     * @return Estimated number of tokens required to encode the text.
     */
    private fun estimateTokens(text: String): Int {
        val characterCount = countCharacters(text)
        if (characterCount == 0) {
            return 0
        }

        return ceil(characterCount / charsPerToken).toInt()
    }

    /**
     * Counts raw characters in a text fragment.
     *
     * This method exists primarily to centralize character counting logic
     * and provide a single extension point for future normalization or
     * preprocessing behavior.
     *
     * @param text Input text.
     * @return Number of characters in the text.
     */
    private fun countCharacters(text: String): Int {
        return text.length
    }

    /**
     * Creates a finalized prompt budgeting plan for a model invocation.
     *
     * The plan is built in several stages:
     *
     *  1. Estimate token usage for fixed prompt components
     *     (system messages, current prompt, task messages, and tools).
     *  2. Compute remaining token budget available for history.
     *  3. Truncate conversation history from oldest to newest while
     *     preserving the most recent exchanges.
     *  4. Normalize messages by optionally stripping `thinking`
     *     content and removing empty payloads.
     *  5. Compute final prompt character counts for diagnostics.
     *
     * The resulting [PromptPlan] contains both the selected history subset
     * and the fully normalized message list that should be sent to the model.
     *
     * @param systemMessages System messages included in the request.
     * @param history Full persisted conversation history.
     * @param prompt Current user prompt.
     * @param taskMessages Messages generated during the active execution loop.
     * @param tools Tool callbacks available to the model.
     * @return Finalized prompt budgeting plan.
     */
    private fun createPromptPlan(
        systemMessages: List<AimoChatMessage>,
        history: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
    ): PromptPlan {
        val fixedMessages = systemMessages + listOf(prompt) + taskMessages
        val fixedInputTokens = estimateMessagesTokens(fixedMessages) + estimateToolTokens(tools)

        if (fixedInputTokens > maxInputTokens) {
            logger.warn(
                "Fixed request components (system messages, prompt, task messages, and tools) exceed context window. " +
                "Fixed tokens: $fixedInputTokens, max input tokens: $maxInputTokens. Proceeding without history."
            )
        }

        val historyForPrompt = truncateHistoryByTokens(history, maxInputTokens - fixedInputTokens)
        val promptMessages = systemMessages + historyForPrompt + prompt + taskMessages
        val normalizedPromptMessages = promptMessages
            .let { messages ->
                if (!excludeThinking) messages
                else messages.map { message ->
                    if (message.thinking == null) message else message.copy(thinking = null)
                }
            }
            .filterNot { it.isEmptyPayload() }

        return PromptPlan(
            history = historyForPrompt,
            promptMessages = normalizedPromptMessages,
        )
    }

    /**
     * Determines whether a chat message contains any meaningful payload.
     *
     * Messages with no content, reasoning text, tool calls, or tool metadata
     * are removed from the final prompt before execution.
     *
     * @return `true` when the message contains no serializable payload.
     */
    private fun AimoChatMessage.isEmptyPayload(): Boolean {
        return content.isNullOrBlank() &&
            thinking.isNullOrBlank() &&
            toolCalls.isNullOrEmpty() &&
            toolName.isNullOrBlank() &&
            toolCallId.isNullOrBlank()
    }
}

