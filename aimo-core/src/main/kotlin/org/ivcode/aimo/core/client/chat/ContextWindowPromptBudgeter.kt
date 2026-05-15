package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.AimoChatMessage
import org.ivcode.aimo.core.AimoChatResponse
import org.ivcode.aimo.core.AimoConversationClient
import org.ivcode.aimo.core.cache.AimoSessionCache
import org.ivcode.aimo.core.cache.SessionTokenCalibration
import org.ivcode.aimo.core.model.AimoToolCallback
import kotlin.math.ceil

internal class ContextWindowPromptBudgeter(
    private val maxInputTokens: Int,
    initialObservedPromptCharacters: Long = 0,
    initialObservedPromptTokens: Long = 0,
    private val excludeThinking: Boolean = false,
    private val calibrationConversation: AimoConversationClient? = null,
    private val calibrationSessionCache: AimoSessionCache? = null,
) : PromptBudgeter {
    constructor(
        maxInputTokens: Int,
        conversation: AimoConversationClient,
        sessionCache: AimoSessionCache,
        excludeThinking: Boolean = false,
    ) : this(
        maxInputTokens = maxInputTokens,
        initialObservedPromptCharacters = resolveObservedPromptCharacters(conversation, sessionCache),
        initialObservedPromptTokens = resolveObservedPromptTokens(conversation, sessionCache),
        excludeThinking = excludeThinking,
        calibrationConversation = conversation,
        calibrationSessionCache = sessionCache,
    )

    private var observedPromptCharacters: Long = initialObservedPromptCharacters.coerceAtLeast(0)
    private var observedPromptTokens: Long = initialObservedPromptTokens.coerceAtLeast(0)

    private data class PromptPlan(
        val history: List<AimoChatMessage>,
        val promptMessages: List<AimoChatMessage>,
        val promptCharacters: Int,
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
        val observedInputTokens = response.usage?.inputTokens?.toLong()
        updateCalibration(
            observedPromptCharacters = plan.promptCharacters.toLong(),
            observedPromptTokens = observedInputTokens,
        )
        return response
    }

    private fun maxRequestCharactersForLookup(): Int {
        return ceil(maxInputTokens * charactersPerToken()).toInt().coerceAtLeast(0)
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
            val messageTokens = estimateTokens(messageTextForBudgeting(message))
            if (tokenCount + messageTokens > tokenBudget) {
                break
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
        return messages.sumOf { estimateTokens(messageTextForBudgeting(it)) }
    }

    private fun countMessageCharacters(messages: List<AimoChatMessage>): Int {
        return messages.sumOf { countCharacters(messageTextForBudgeting(it)) }
    }

    private fun messageTextForBudgeting(message: AimoChatMessage): String {
        val content = message.content.orEmpty()
        if (excludeThinking) {
            return content
        }

        val thinking = message.thinking.orEmpty()
        if (thinking.isBlank()) {
            return content
        }

        return if (content.isBlank()) thinking else "$content\n$thinking"
    }

    /**
     * Estimates input cost of tool metadata sent alongside prompts.
     *
     * @param tools Tool callbacks included in prompt options.
     * @return Estimated aggregate token count for tool definitions.
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

    private fun countToolCharacters(tools: List<AimoToolCallback>): Int {
        return tools.sumOf { toolCallback ->
            val def = toolCallback.toolDefinition

            countCharacters(def.name) +
                countCharacters(def.description ?: "") +
                countCharacters(def.inputSchema.toString())
        }
    }

    /**
     * Estimates token count for a text fragment using a simple character heuristic.
     *
     * @param text Input text.
     * @return Estimated token count.
     */
    private fun estimateTokens(text: String): Int {
        val characterCount = countCharacters(text)
        if (characterCount == 0) {
            return 0
        }

        return ceil(characterCount / charactersPerToken()).toInt()
    }

    private fun countCharacters(text: String): Int {
        return text.length
    }

    private fun createPromptPlan(
        systemMessages: List<AimoChatMessage>,
        history: List<AimoChatMessage>,
        prompt: AimoChatMessage,
        taskMessages: List<AimoChatMessage>,
        tools: List<AimoToolCallback>,
    ): PromptPlan {
        val fixedMessages = systemMessages + listOf(prompt) + taskMessages
        val fixedInputTokens = estimateMessagesTokens(fixedMessages) + estimateToolTokens(tools)
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

        val promptCharacters = countMessageCharacters(normalizedPromptMessages) +
            countToolCharacters(tools)

        return PromptPlan(
            history = historyForPrompt,
            promptMessages = normalizedPromptMessages,
            promptCharacters = promptCharacters,
        )
    }

    private fun AimoChatMessage.isEmptyPayload(): Boolean {
        return content.isNullOrBlank() &&
            thinking.isNullOrBlank() &&
            toolCalls.isNullOrEmpty() &&
            toolName.isNullOrBlank() &&
            toolCallId.isNullOrBlank()
    }

    private fun charactersPerToken(): Double {
        synchronized(this) {
            if (observedPromptTokens > 0) {
                return (observedPromptCharacters.toDouble() / observedPromptTokens.toDouble())
                    .coerceIn(MIN_CHARACTERS_PER_TOKEN, MAX_CHARACTERS_PER_TOKEN)
            }
        }

        return DEFAULT_CHARACTERS_PER_TOKEN
    }

    private fun updateCalibration(observedPromptCharacters: Long, observedPromptTokens: Long?) {
        val tokens = (observedPromptTokens ?: 0L).coerceAtLeast(0L)
        if (observedPromptCharacters <= 0L || tokens <= 0L) {
            return
        }

        val updated = synchronized(this) {
            this.observedPromptCharacters += observedPromptCharacters
            this.observedPromptTokens += tokens
            SessionTokenCalibration(
                observedPromptCharacters = this.observedPromptCharacters,
                observedPromptTokens = this.observedPromptTokens,
            )
        }

        calibrationSessionCache?.writeRuntimeProperty(CACHE_KEY__TOKEN_CALIBRATION, updated)
        calibrationConversation?.writeChatProperty(
            METADATA_KEY__OBSERVED_PROMPT_CHARACTERS,
            updated.observedPromptCharacters,
        )
        calibrationConversation?.writeChatProperty(
            METADATA_KEY__OBSERVED_PROMPT_TOKENS,
            updated.observedPromptTokens,
        )
    }

    private companion object {
        const val DEFAULT_CHARACTERS_PER_TOKEN = 4.0
        const val MIN_CHARACTERS_PER_TOKEN = 1.0
        const val MAX_CHARACTERS_PER_TOKEN = 12.0
        const val CACHE_KEY__TOKEN_CALIBRATION = "chat.tokenCalibration"
        const val METADATA_KEY__OBSERVED_PROMPT_CHARACTERS = "chat.inputTokenBudgeter.observedPromptCharacters"
        const val METADATA_KEY__OBSERVED_PROMPT_TOKENS = "chat.inputTokenBudgeter.observedPromptTokens"

        private fun resolveObservedPromptCharacters(
            conversation: AimoConversationClient,
            sessionCache: AimoSessionCache,
        ): Long {
            return getTokenCalibration(sessionCache)?.observedPromptCharacters
                ?: conversation.getChatProperty(METADATA_KEY__OBSERVED_PROMPT_CHARACTERS).toNonNegativeLong()
        }

        private fun resolveObservedPromptTokens(
            conversation: AimoConversationClient,
            sessionCache: AimoSessionCache,
        ): Long {
            return getTokenCalibration(sessionCache)?.observedPromptTokens
                ?: conversation.getChatProperty(METADATA_KEY__OBSERVED_PROMPT_TOKENS).toNonNegativeLong()
        }

        private fun getTokenCalibration(sessionCache: AimoSessionCache): SessionTokenCalibration? {
            return sessionCache.getRuntimeProperty(CACHE_KEY__TOKEN_CALIBRATION) as? SessionTokenCalibration
        }

        private fun Any?.toNonNegativeLong(): Long {
            return when (this) {
                is Number -> toLong().coerceAtLeast(0)
                is String -> toLongOrNull()?.coerceAtLeast(0) ?: 0
                else -> 0
            }
        }
    }
}







