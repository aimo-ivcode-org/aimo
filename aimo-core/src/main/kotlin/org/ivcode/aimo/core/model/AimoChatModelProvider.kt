package org.ivcode.aimo.core.model

/**
 * Provider contract for resolving chat model configurations.
 *
 * Purpose
 * - Implementations expose the set of models available to the application and allow
 *   lookup of a specific model by name.
 * - This interface keeps a small, focused surface for simple implementations.
 *
 * Usage
 * - Callers should obtain a concrete [AimoChatModelConfig] from a provider when
 *   they need to resolve a model to be used by a chat client or engine.
 *
 * Thread-safety & lifecycle
 * - Implementations are typically long-lived and should be safe for concurrent
 *   use. If an implementation maintains mutable internal state, it must ensure
 *   appropriate synchronization.
 *
 * Access Control
 * - This interface intentionally provides no interceptor or filtering support.
 * - Access control and request-scoped visibility enforcement belong in the
 *   server or host modules where security context is available.
 */
interface AimoChatModelProvider {

     /**
      * List all model configurations exposed by this provider.
      *
      * Implementations should return an immutable list or a defensive copy to avoid
      * accidental mutation by callers.
      *
      * @return immutable List of [AimoChatModelConfig]
      */
     fun getModels(): List<AimoChatModelConfig>

     /**
      * Look up a single model configuration by its stable name.
      *
      * @param name stable model name
      * @return matching [AimoChatModelConfig] or null when not found
      */
     fun getModel(name: String): AimoChatModelConfig?
}
