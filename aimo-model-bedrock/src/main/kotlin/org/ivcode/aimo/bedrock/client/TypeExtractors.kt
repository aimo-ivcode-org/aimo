package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.core.document.Document

/**
 * Type extraction helpers using reflection to safely access AWS SDK model fields.
 */
internal object TypeExtractors {

    fun extractReasoningText(source: Any?): String? {
        if (source == null) return null

        val reasoning = invokeNoArg(source, "reasoningContent")
            ?: invokeNoArg(source, "reasoning")
            ?: return null

        return invokeNoArg(reasoning, "text") as? String
            ?: (invokeNoArg(reasoning, "reasoningText")?.let { invokeNoArg(it, "text") as? String })
    }

    fun extractToolUseStart(source: Any?): ToolUsePartial? {
        if (source == null) return null
        val toolUse = invokeNoArg(source, "toolUse") ?: return null
        return ToolUsePartial(
            toolUseId = invokeNoArg(toolUse, "toolUseId") as? String,
            name = invokeNoArg(toolUse, "name") as? String,
            inputChunk = invokeNoArg(toolUse, "input") as? String,
            inputDocument = invokeNoArg(toolUse, "input") as? Document,
        )
    }

    fun extractToolUseDelta(source: Any?): ToolUsePartial? {
        if (source == null) return null
        val toolUse = invokeNoArg(source, "toolUse") ?: return null
        return ToolUsePartial(
            toolUseId = invokeNoArg(toolUse, "toolUseId") as? String,
            name = invokeNoArg(toolUse, "name") as? String,
            inputChunk = invokeNoArg(toolUse, "input") as? String,
            inputDocument = invokeNoArg(toolUse, "input") as? Document,
        )
    }

    fun invokeNoArg(target: Any, methodName: String): Any? {
        return try {
            target.javaClass.methods
                .firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?.invoke(target)
        } catch (_: Exception) {
            null
        }
    }
}

internal data class ToolUsePartial(
    val toolUseId: String? = null,
    val name: String? = null,
    val inputChunk: String? = null,
    val inputDocument: Document? = null,
)

