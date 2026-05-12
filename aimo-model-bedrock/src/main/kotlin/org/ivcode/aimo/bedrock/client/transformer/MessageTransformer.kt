package org.ivcode.aimo.bedrock.client.transformer

import org.ivcode.aimo.bedrock.client.ConverseResponse

/**
 * Model-specific interpreter applied to Bedrock text content.
 *
 * A **fresh instance must be created per request** — all implementations are stateful
 * across streaming chunks.
 *
 * Two responsibilities:
 * 1. [consumeChunk] — real-time, stateful processing of streaming text deltas.
 * 2. [transformFinalResponse] — optional post-processing of the fully assembled
 *    [ConverseResponse] (used for the final stream response and for non-streaming calls).
 */
internal interface MessageTransformer {

    /**
     * Result of processing a single streaming text delta.
     *
     * @param text     Visible assistant text to forward to the caller (may be empty).
     * @param reasoning Extracted reasoning / thinking content (may be empty).
     */
    data class ChunkResult(
        val text: String = "",
        val reasoning: String = "",
    )

    /** Process an incremental text chunk. Returns split visible/reasoning content. */
    fun consumeChunk(rawText: String): ChunkResult

    /**
     * Apply any final post-processing to the assembled [ConverseResponse].
     * Default implementation is a no-op.
     */
    fun transformFinalResponse(response: ConverseResponse): ConverseResponse = response
}

