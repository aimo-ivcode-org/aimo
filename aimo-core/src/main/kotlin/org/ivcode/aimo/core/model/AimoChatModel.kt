package org.ivcode.aimo.core.model

/**
 * Runtime configuration for an Aimo-owned chat model implementation.
 *
 * This is the **model configuration layer** that combines a concrete chat engine with
 * model-specific defaults and context rules. It forms part of the core seam:
 * `AimoChatModelProviderFactory` → `AimoChatModelConfig` → `AimoChatEngine`.
 *
 * @property name Human-readable model identifier.
 * @property chatEngine Provider-backed engine used to execute prompts.
 *     The engine stores the model's default options (from YAML configuration) and merges them
 *     with per-request options via [AimoPrompt.options] when building requests.
 * @property isPrimary Whether this model is the default selection when multiple models exist.
 * @property context Prompt budgeting behavior for this model.
 */
data class AimoChatModelConfig (
    val name: String,
    val chatEngine: AimoChatEngine,
    val isPrimary: Boolean = false,
    val context: AimoChatContext = AimoChatContext(),
)

data class AimoChatContext(
    val size: Int = 8192,
    val excludeThinking: Boolean = false,
    /** Selects how prompt messages are budgeted for this model. */
    val budgeterType: AimoPromptBudgeterType = AimoPromptBudgeterType.CONTEXT_WINDOW,
)

enum class AimoPromptBudgeterType {
    CONTEXT_WINDOW,
    NO_OP,
}

/**
 * Provider-local factory for chat models.
 *
 * A provider factory manages only the models belonging to a single provider such as Ollama
 * or Bedrock. A provider may expose zero or more named models and may optionally designate
 * one of them as its provider-local primary model.
 *
 * This contract is intentionally nullable for default and named lookup because a provider may
 * have no configured models, and callers may request a name that does not exist.
 *
 * @property provider Stable provider identifier such as `ollama`.
 */
interface AimoChatModelProviderFactory {
    val provider: String

    /**
     * Gets the named model when it exists for this provider.
     *
     * @return the matching model, or `null` when [name] is unknown.
     */
    fun getModel(name: String): AimoChatModelConfig?

    /**
     * Gets this provider's default model.
     *
     * Implementations typically return the provider-local primary model when one is defined,
     * otherwise the provider's first configured model.
     *
     * @return the provider default model, or `null` when no models are configured.
     */
    fun getDefaultModel(): AimoChatModelConfig?

    /**
     * Lists all model names known to this provider.
     */
    fun getNames(): List<String>

    /**
     * Gets all model configurations for this provider.
     *
     * Equivalent to calling getModel(name) for each name in getNames().
     *
     * @return immutable list of all models for this provider
     */
    fun getModels(): List<AimoChatModelConfig>

    /**
     * Returns the provider-local primary model name when one is configured.
     *
     * This is provider-scoped only; it does not imply that the model is the application's
     * global default across all providers.
     */
    fun getPrimaryName(): String?
}

/**
 * Global chat model factory contract.
 *
 * This represents the application-level view after all provider models have been combined and
 * a single non-null default model has been resolved. Unlike [AimoChatModelProviderFactory],
 * this contract is non-null for default lookup and primary name resolution.
 */
interface AimoChatModelFactory {

    /**
     * Gets the named model when it exists in the global model set.
     *
     * @return the matching model, or `null` when [name] is unknown.
     */
    fun getModel(name: String): AimoChatModelConfig?

    /**
     * Gets the globally selected primary model.
     */
    fun getPrimaryModel(): AimoChatModelConfig

    /**
     * Lists all globally available model names.
     */
    fun getNames(): List<String>

    /**
     * Returns the globally selected primary model name.
     */
    fun getPrimaryName(): String
}
