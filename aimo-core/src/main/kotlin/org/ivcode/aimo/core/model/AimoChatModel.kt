package org.ivcode.aimo.core.model

/**
 * Runtime configuration for an Aimo-owned chat model implementation.
 *
 * @property name Human-readable model identifier.
 * @property chatEngine Provider-backed engine used to execute prompts.
 * @property options Default provider-agnostic options applied to requests for this model.
 * @property isPrimary Whether this model is the default selection when multiple models exist.
 * @property contextSize Approximate maximum context window size, measured in tokens.
 */
data class AimoChatModel (
    val name: String,
    val chatEngine: AimoChatEngine,
    val options: AimoChatOptions,
    val isPrimary: Boolean = false,
    val contextSize: Int,
)

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
     * Creates the named model when it exists for this provider.
     *
     * @return the matching model, or `null` when [name] is unknown.
     */
    fun createAimoChatModel(name: String): AimoChatModel?

    /**
     * Creates this provider's default model.
     *
     * Implementations typically return the provider-local primary model when one is defined,
     * otherwise the provider's first configured model.
     *
     * @return the provider default model, or `null` when no models are configured.
     */
    fun createAimoChatModel(): AimoChatModel?

    /**
     * Lists all model names known to this provider.
     */
    fun getNames(): List<String>

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
     * Creates the named model when it exists in the global model set.
     *
     * @return the matching model, or `null` when [name] is unknown.
     */
    fun createAimoChatModel(name: String): AimoChatModel?

    /**
     * Creates the globally selected default model.
     */
    fun createAimoChatModel(): AimoChatModel

    /**
     * Lists all globally available model names.
     */
    fun getNames(): List<String>

    /**
     * Returns the globally selected primary model name.
     */
    fun getPrimaryName(): String
}