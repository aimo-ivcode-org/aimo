package org.ivcode.aimo.core.model

/**
 * Implementation of [AimoChatModelFactory] that aggregates models from all
 * registered [AimoChatModelProviderFactory] instances.
 *
 * This is the application-level view of available models. It enforces that a
 * single global primary model is selected and provides non-null access to it.
 *
 * Model discovery and primary selection are delegated to provider-local factories.
 * The primary model result is cached after first resolution to avoid repeated computation.
 */
internal class AimoChatModelFactoryImpl(
    private val chatModelFactories: Map<String, AimoChatModelProviderFactory>
) : AimoChatModelFactory {

    // Cached primary model; computed once on first call, reused thereafter
    @Volatile
    private var cachedPrimaryModel: AimoChatModelConfig? = null

    @Volatile
    private var primaryResolutionAttempted = false

    /**
     * Return all model configurations from all provider factories.
     * The order is deterministic based on the iteration order of the injected map.
     */
    fun getModels(): List<AimoChatModelConfig> {
        val models = mutableListOf<AimoChatModelConfig>()
        chatModelFactories.values.forEach { factory ->
            models.addAll(factory.getModels())
        }
        return models.toList()  // Return immutable copy
    }

    /**
     * Look up a single model by name by querying each provider factory in turn.
     *
     * Validates that the model name is unique across all providers (fails fast if duplicates detected).
     * This ensures consistent model resolution regardless of provider iteration order.
     *
     * @return matching model or null when not found
     * @throws IllegalStateException if the same model name is exposed by multiple providers
     */
    override fun getModel(name: String): AimoChatModelConfig? {
        var foundModel: AimoChatModelConfig? = null
        var foundProviderName: String? = null

        for (factory in chatModelFactories.values) {
            val model = factory.getModel(name)
            if (model != null) {
                if (foundModel != null) {
                    // Duplicate model name detected across providers
                    throw IllegalStateException(
                        "Duplicate model name '$name' exposed by providers " +
                            "'$foundProviderName' and '${factory.provider}'. " +
                            "Model names must be unique across all providers."
                    )
                }
                foundModel = model
                foundProviderName = factory.provider
            }
        }
        return foundModel
    }

    /**
     * List all globally available model names.
     *
     * Verifies that no two providers expose the same model name (fails fast if duplicates detected).
     *
     * @throws IllegalStateException if duplicate model names are found across providers
     */
    override fun getNames(): List<String> {
        val names = mutableListOf<String>()
        val seenNames = mutableMapOf<String, String>()  // modelName -> providerName

        chatModelFactories.values.forEach { factory ->
            factory.getNames().forEach { name ->
                val existingProvider = seenNames[name]
                if (existingProvider != null) {
                    throw IllegalStateException(
                        "Duplicate model name '$name' exposed by providers " +
                            "'$existingProvider' and '${factory.provider}'. " +
                            "Model names must be unique across all providers."
                    )
                }
                seenNames[name] = factory.provider
                names.add(name)
            }
        }
        return names.toList()
    }

    /**
     * Gets the globally selected primary model.
     *
     * Selection order:
     * 1. A model marked with isPrimary=true (must be unique across all providers)
     * 2. If no explicit primary exists and exactly one model is configured, return it
     * 3. Otherwise throw IllegalStateException with clear diagnostic message
     *
     * Result is cached after first computation to avoid repeated model instantiation/engine creation.
     *
     * @throws IllegalStateException if primary model selection is ambiguous or fails
     */
    override fun getPrimaryModel(): AimoChatModelConfig {
        // Double-checked locking for thread-safe lazy initialization
        if (primaryResolutionAttempted) {
            return cachedPrimaryModel ?: throw IllegalStateException(
                "Primary model resolution previously failed; see logs for details"
            )
        }

        return synchronized(this) {
            if (primaryResolutionAttempted) {
                cachedPrimaryModel ?: throw IllegalStateException(
                    "Primary model resolution previously failed; see logs for details"
                )
            } else {
                val resolvedModel = resolvePrimaryModel()
                cachedPrimaryModel = resolvedModel
                primaryResolutionAttempted = true
                resolvedModel
            }
        }
    }

    /**
     * Resolves the primary model by trying explicit primary first, then single model fallback.
     *
     * @throws IllegalStateException if resolution fails (duplicate primary, missing config, or ambiguous)
     */
    private fun resolvePrimaryModel(): AimoChatModelConfig {
        val factories = chatModelFactories.values.toList()

        // Try to use explicitly marked primary model
        val primaryModel = selectExplicitPrimaryModel(factories)
        if (primaryModel != null) return primaryModel

        // Fall back to single-model default
        return selectSingleModelDefault()
    }

    /**
     * Selects the explicitly marked primary model from all factories.
     *
     * @return the primary model if found and valid, null if no explicit primary is marked
     * @throws IllegalStateException if multiple primary models found or consistency check fails
     */
    private fun selectExplicitPrimaryModel(factories: List<AimoChatModelProviderFactory>): AimoChatModelConfig? {
        val primaryModels = factories.mapNotNull { factory ->
            factory.getPrimaryName()?.let { primaryName ->
                factory.getModel(primaryName) ?: throw IllegalStateException(
                    "Provider '${factory.provider}' reported primary model name '$primaryName' " +
                        "but getModel('$primaryName') returned null. This indicates a configuration inconsistency."
                )
            }
        }

        check(primaryModels.size <= 1) {
            "Only one model can be marked primary=true globally. " +
                "Found ${primaryModels.size} primary models: ${primaryModels.map { it.name }}"
        }

        return primaryModels.firstOrNull()
    }

    /**
     * Selects a model when no explicit primary is marked.
     * Returns the single model if exactly one exists; throws if zero or multiple.
     *
     * @throws IllegalStateException if no models configured or multiple models with no explicit primary
     */
    private fun selectSingleModelDefault(): AimoChatModelConfig {
        val allModels = getModels()

        return when {
            allModels.isEmpty() -> throw IllegalStateException(
                "No models are configured. Please add at least one model configuration."
            )
            allModels.size == 1 -> allModels.first()
            else -> throw IllegalStateException(
                "No primary model is configured. " +
                    "Multiple models exist: ${allModels.map { it.name }}. " +
                    "Mark exactly one with primary=true in configuration."
            )
        }
    }

    /**
     * Returns the globally selected primary model name.
     *
     * @throws IllegalStateException if primary model selection fails (see [getPrimaryModel])
     */
    override fun getPrimaryName(): String {
        return getPrimaryModel().name
    }
}

