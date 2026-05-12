package org.ivcode.aimo.bedrock.client.transformer

/** Passthrough — no model-specific transformation applied. */
internal class DefaultMessageTransformer : MessageTransformer {
    override fun consumeChunk(rawText: String) = MessageTransformer.ChunkResult(text = rawText)
}

