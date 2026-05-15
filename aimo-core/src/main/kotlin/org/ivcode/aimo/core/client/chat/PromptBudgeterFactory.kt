package org.ivcode.aimo.core.client.chat

import org.ivcode.aimo.core.model.AimoChatModel
import org.ivcode.aimo.core.model.AimoPromptBudgeterType

internal interface PromptBudgeterFactory {
    fun create(
        model: AimoChatModel,
        initialObservedPromptCharacters: Long,
        initialObservedPromptTokens: Long,
    ): PromptBudgeter
}

internal object DefaultPromptBudgeterFactory : PromptBudgeterFactory {
    override fun create(
        model: AimoChatModel,
        initialObservedPromptCharacters: Long,
        initialObservedPromptTokens: Long,
    ): PromptBudgeter {
        return when (model.context.budgeterType) {
            AimoPromptBudgeterType.CONTEXT_WINDOW -> ContextWindowPromptBudgeter(
                maxInputTokens = model.context.size,
                initialObservedPromptCharacters = initialObservedPromptCharacters,
                initialObservedPromptTokens = initialObservedPromptTokens,
                excludeThinking = model.context.excludeThinking,
            )

            AimoPromptBudgeterType.NO_OP -> NoOpPromptBudgeter(
                excludeThinking = model.context.excludeThinking,
            )
        }
    }
}


