package org.ivcode.aimo.core.cache

/**
 * Cumulative prompt-cache token statistics for a conversation.
 *
 * These values accumulate across all model calls within the conversation, letting callers
 * measure how much reprocessing is being avoided by prompt caching.
 *
 * @property totalCacheReadTokens  Tokens retrieved from the provider cache (saved cost/latency).
 * @property totalCacheWriteTokens Tokens written into the provider cache across all calls.
 */
data class SessionCacheStats(
    val totalCacheReadTokens: Long = 0,
    val totalCacheWriteTokens: Long = 0,
)

