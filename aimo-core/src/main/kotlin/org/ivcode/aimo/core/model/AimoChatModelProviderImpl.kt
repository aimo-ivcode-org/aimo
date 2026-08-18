package org.ivcode.aimo.core.model

/**
 * Implementation of [AimoChatModelProvider] that aggregates models from all
 * registered [AimoChatModelProviderFactory] instances.
 *
 * This provider does not perform model discovery itself; it queries the
 * provider-local factories to obtain model names and configurations.
 */
class AimoChatModelProviderImpl(
     private val chatModelFactories: Map<String, AimoChatModelProviderFactory>
) : AimoChatModelProvider {

    /**
     * Return all model configurations from all provider factories.
     * The order is deterministic based on the iteration order of the injected map.
     */
    override fun getModels(): List<AimoChatModelConfig> {
         // Core operation: collect raw models from provider factories
         val base: (MutableMap<String, Any>) -> List<AimoChatModelConfig> = { _ ->
             val models = mutableListOf<AimoChatModelConfig>()
             chatModelFactories.values.forEach { factory ->
                 factory.getNames().forEach { name ->
                     factory.getModel(name)?.let { models.add(it) }
                 }
             }
             models.toList()  // Return immutable copy, not the mutable backing list
         }

         // Directly return collected models. Interceptor support was removed from core
         // to keep policy and request-scoped access control in server modules.
         return base(mutableMapOf())
     }

    /**
     * Look up a single model by name by querying each provider factory in turn.
     * Returns the first match or null when not found.
     */
    override fun getModel(name: String): AimoChatModelConfig? {
        // Core lookup operation
        val base: (String, MutableMap<String, Any>) -> AimoChatModelConfig? = { n, _ ->
            var found: AimoChatModelConfig? = null
            for (factory in chatModelFactories.values) {
                val model = factory.getModel(n)
                if (model != null) {
                    found = model
                    break
                }
            }
            found
        }

        return base(name, mutableMapOf())
    }
}

