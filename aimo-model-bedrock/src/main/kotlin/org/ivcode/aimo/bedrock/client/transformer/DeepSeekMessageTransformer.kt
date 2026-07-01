package org.ivcode.aimo.bedrock.client.transformer

import org.ivcode.aimo.bedrock.client.ContentBlock
import org.ivcode.aimo.bedrock.client.ConverseResponse

/**
 * Transformer for DeepSeek models served via Bedrock.
 *
 * DeepSeek emits a model-side control marker before tool calls:
 *   `<|DSML|function_calls`  or  `<｜DSML｜function_calls`
 * The tag is never closed with `>` before the stream ends, so it must be
 * suppressed chunk-by-chunk to avoid leaking raw control text to the UI.
 */
internal class DeepSeekMessageTransformer : MessageTransformer {

    private val filter = DsmlControlFilter()

    override fun consumeChunk(rawText: String): MessageTransformer.ChunkResult {
        val filtered = filter.consume(rawText)
        return MessageTransformer.ChunkResult(text = filtered)
    }

    override fun transformFinalResponse(response: ConverseResponse): ConverseResponse {
        val msg = response.output.message
        val cleaned = msg.content.map { cb ->
            if (cb.text != null) cb.copy(text = DSML_PATTERN.replace(cb.text, "")) else cb
        }
        return response.copy(output = response.output.copy(message = msg.copy(content = cleaned)))
    }

    // -------------------------------------------------------------------------
    // DSML tag filter (stateful, chunk-aware)
    // -------------------------------------------------------------------------

    private companion object {
        /** Matches the full DSML tag in non-streaming text (with or without closing `>`). */
        val DSML_PATTERN = Regex("<[|｜]DSML[|｜]function_calls[^>]*>?")
    }

    /**
     * Strips `<|DSML|function_calls…>` (and the full-width pipe variant) from a stream
     * of text chunks. Handles tags that span multiple chunks and tags that are never
     * terminated with `>`.
     */
    private class DsmlControlFilter {
        private val markers = listOf(
            "<|DSML|function_calls",
            "<\uFF5CDSML\uFF5Cfunction_calls",
        )
        private var droppingUntilGt = false
        private var carry = ""

        fun consume(input: String): String {
            if (input.isEmpty() && carry.isEmpty()) return input

            var text = carry + input
            carry = ""

            if (droppingUntilGt) {
                val end = text.indexOf('>')
                if (end < 0) return ""
                droppingUntilGt = false
                text = text.substring(end + 1)
            }

            val out = StringBuilder()
            var index = 0
            while (index < text.length) {
                val markerMatch = findNextMarker(text, index)
                if (markerMatch == null) {
                    val tail = text.substring(index)
                    val split = splitCarryTail(tail)
                    out.append(split.first)
                    carry = split.second
                    break
                }

                out.append(text.substring(index, markerMatch.first))
                var afterMarker = markerMatch.first + markerMatch.second.length

                if (afterMarker < text.length && text[afterMarker] == '>') {
                    index = afterMarker + 1
                    continue
                }

                val gt = text.indexOf('>', afterMarker)
                if (gt >= 0) {
                    index = gt + 1
                    continue
                }

                droppingUntilGt = true
                index = text.length
            }

            return out.toString()
        }

        /** Returns (position, marker) of the earliest marker hit, or null. */
        private fun findNextMarker(text: String, start: Int): Pair<Int, String>? {
            var best: Pair<Int, String>? = null
            for (marker in markers) {
                val pos = text.indexOf(marker, start)
                if (pos >= 0 && (best == null || pos < best.first)) best = pos to marker
            }
            return best
        }

        /** Split tail into (visiblePrefix, carryForNextChunk). */
        private fun splitCarryTail(tail: String): Pair<String, String> {
            if (tail.isEmpty()) return "" to ""
            val maxLen = markers.maxOf { it.length }
            val window = tail.takeLast(maxLen.coerceAtMost(tail.length))
            val carryLen = longestSuffixPrefix(window)
            return if (carryLen == 0) tail to "" else tail.dropLast(carryLen) to tail.takeLast(carryLen)
        }

        private fun longestSuffixPrefix(window: String): Int {
            var best = 0
            for (marker in markers) {
                val max = minOf(window.length, marker.length - 1)
                for (len in 1..max) {
                    if (window.endsWith(marker.substring(0, len)) && len > best) best = len
                }
            }
            return best
        }
    }
}

