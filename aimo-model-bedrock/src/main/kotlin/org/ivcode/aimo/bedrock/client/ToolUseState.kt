package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.core.document.Document
import tools.jackson.databind.ObjectMapper
import tools.jackson.core.type.TypeReference

/**
 * Accumulates tool-use state across stream delta events.
 */
internal class ToolUseState {
    var toolUseId: String? = null
    var name: String? = null
    val inputChunks: StringBuilder = StringBuilder()
    var inputDocument: Document? = null

    fun mergeFrom(partial: ToolUsePartial?) {
        if (partial == null) return
        if (!partial.toolUseId.isNullOrBlank()) toolUseId = partial.toolUseId
        if (!partial.name.isNullOrBlank()) name = partial.name
        if (!partial.inputChunk.isNullOrEmpty()) inputChunks.append(partial.inputChunk)
        if (partial.inputDocument != null) inputDocument = partial.inputDocument
    }

    fun toToolUse(mapper: ObjectMapper): ToolUse? {
        val resolvedId = toolUseId?.takeIf { it.isNotBlank() } ?: return null
        val resolvedName = name?.takeIf { it.isNotBlank() } ?: return null
        val input = when {
            inputDocument != null -> DocumentConverter.documentToMap(inputDocument!!)
            inputChunks.isNotBlank() -> parseToolInput(mapper, inputChunks.toString())
            else -> emptyMap()
        }

        return ToolUse(
            toolUseId = resolvedId,
            name = resolvedName,
            input = input,
        )
    }

    private fun parseToolInput(mapper: ObjectMapper, raw: String): Map<String, Any?> {
        return try {
            mapper.readValue(raw, object : TypeReference<Map<String, Any?>>() {})
        } catch (_: Exception) {
            mapOf("raw" to raw)
        }
    }
}

