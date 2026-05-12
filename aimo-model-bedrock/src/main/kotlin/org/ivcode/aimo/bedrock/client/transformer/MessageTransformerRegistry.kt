package org.ivcode.aimo.bedrock.client.transformer

/**
 * Selects the appropriate [MessageTransformer] for a given Bedrock model ID.
 *
 * Model ID patterns:
 *  - DeepSeek:  `deepseek.*` / `us.deepseek.*`
 *  - Nova:      `amazon.nova.*` / `us.amazon.nova.*`
 *  - Default:   everything else
 */
internal object MessageTransformerRegistry {

    fun create(modelId: String): MessageTransformer {
        val id = modelId.lowercase()
        return when {
            id.contains("deepseek") -> DeepSeekMessageTransformer()
            id.contains("nova")     -> NovaMessageTransformer()
            else                    -> DefaultMessageTransformer()
        }
    }
}

