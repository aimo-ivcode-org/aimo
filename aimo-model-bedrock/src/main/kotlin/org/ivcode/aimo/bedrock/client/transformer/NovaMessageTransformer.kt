package org.ivcode.aimo.bedrock.client.transformer

import org.ivcode.aimo.bedrock.client.ContentBlock
import org.ivcode.aimo.bedrock.client.ConverseResponse

/**
 * Transformer for Amazon Nova models served via Bedrock.
 *
 * Nova embeds its chain-of-thought reasoning inside `<thinking>…</thinking>` tags
 * within the text stream.  This transformer:
 *  - Routes text **inside** tags → [MessageTransformer.ChunkResult.reasoning]
 *  - Routes text **outside** tags → [MessageTransformer.ChunkResult.text]
 *
 * The parser is chunk-aware: opening/closing tags that span multiple stream deltas
 * are handled correctly via an internal carry buffer.
 *
 * [transformFinalResponse] also handles non-streaming responses where thinking
 * content may arrive embedded in a text block rather than as a separate block.
 */
internal class NovaMessageTransformer : MessageTransformer {

    private enum class State { NORMAL, IN_THINKING }

    private var state = State.NORMAL
    private var carry = ""

    override fun consumeChunk(rawText: String): MessageTransformer.ChunkResult {
        val input = carry + rawText
        carry = ""

        val text = StringBuilder()
        val reasoning = StringBuilder()
        var i = 0

        while (i < input.length) {
            when (state) {
                State.NORMAL -> {
                    val tagStart = input.indexOf(OPEN_TAG, i)
                    if (tagStart == -1) {
                        // No open tag found — check if the tail is a partial match
                        val tail = input.substring(i)
                        val partialLen = longestTagPrefixSuffix(tail, OPEN_TAG)
                        if (partialLen > 0) {
                            text.append(tail.dropLast(partialLen))
                            carry = tail.takeLast(partialLen)
                        } else {
                            text.append(tail)
                        }
                        i = input.length
                    } else {
                        text.append(input.substring(i, tagStart))
                        state = State.IN_THINKING
                        i = tagStart + OPEN_TAG.length
                    }
                }

                State.IN_THINKING -> {
                    val tagEnd = input.indexOf(CLOSE_TAG, i)
                    if (tagEnd == -1) {
                        val tail = input.substring(i)
                        val partialLen = longestTagPrefixSuffix(tail, CLOSE_TAG)
                        if (partialLen > 0) {
                            reasoning.append(tail.dropLast(partialLen))
                            carry = tail.takeLast(partialLen)
                        } else {
                            reasoning.append(tail)
                        }
                        i = input.length
                    } else {
                        reasoning.append(input.substring(i, tagEnd))
                        state = State.NORMAL
                        i = tagEnd + CLOSE_TAG.length
                    }
                }
            }
        }

        return MessageTransformer.ChunkResult(
            text = text.toString(),
            reasoning = reasoning.toString(),
        )
    }

    override fun transformFinalResponse(response: ConverseResponse): ConverseResponse {
        val msg = response.output.message
        val hasExplicitReasoning = msg.content.any { it.reasoning != null }

        // If the model already returned a separate reasoning block, nothing to do.
        if (hasExplicitReasoning) return response

        // Otherwise scan text blocks for embedded <thinking> tags.
        val newContent = mutableListOf<ContentBlock>()
        for (cb in msg.content) {
            if (cb.text == null) {
                newContent += cb
                continue
            }
            val extracted = extractThinkingFromText(cb.text)
            if (extracted.reasoning.isNotBlank()) {
                if (extracted.text.isNotBlank()) newContent += cb.copy(text = extracted.text)
                newContent += ContentBlock(reasoning = extracted.reasoning)
            } else {
                newContent += cb
            }
        }

        return response.copy(output = response.output.copy(message = msg.copy(content = newContent)))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private companion object {
        const val OPEN_TAG = "<thinking>"
        const val CLOSE_TAG = "</thinking>"
        val THINKING_PATTERN = Regex("<thinking>(.*?)</thinking>", RegexOption.DOT_MATCHES_ALL)
    }

    private data class ExtractedText(val text: String, val reasoning: String)

    private fun extractThinkingFromText(raw: String): ExtractedText {
        val reasoning = StringBuilder()
        val text = THINKING_PATTERN.replace(raw) { match ->
            reasoning.append(match.groupValues[1])
            ""
        }.trim()
        return ExtractedText(text = text, reasoning = reasoning.toString().trim())
    }

    /**
     * Returns the length of the longest suffix of [tail] that is a prefix of [tag].
     * Used to detect split tags spanning chunk boundaries.
     */
    private fun longestTagPrefixSuffix(tail: String, tag: String): Int {
        val max = minOf(tail.length, tag.length - 1)
        for (len in max downTo 1) {
            if (tail.endsWith(tag.substring(0, len))) return len
        }
        return 0
    }
}

