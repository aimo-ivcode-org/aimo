package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.core.document.Document

/**
 * Type extraction helpers using reflection to safely access AWS SDK model fields.
 */
internal object TypeExtractors {

    fun extractReasoningText(source: Any?): String? =
        source?.let { current ->
            val reasoning = invokeNoArg(current, "reasoningContent")
                ?: invokeNoArg(current, "reasoning")

            reasoning?.let {
                invokeNoArg(it, "text") as? String
                    ?: (invokeNoArg(it, "reasoningText")?.let { nested -> invokeNoArg(nested, "text") as? String })
            }
        }

    fun extractToolUseStart(source: Any?): ToolUsePartial? =
        source?.let { current ->
            invokeNoArg(current, "toolUse")?.let { toolUse ->
                ToolUsePartial(
                    toolUseId = invokeNoArg(toolUse, "toolUseId") as? String,
                    name = invokeNoArg(toolUse, "name") as? String,
                    inputChunk = invokeNoArg(toolUse, "input") as? String,
                    inputDocument = invokeNoArg(toolUse, "input") as? Document,
                )
            }
        }

    fun extractToolUseDelta(source: Any?): ToolUsePartial? =
        source?.let { current ->
            invokeNoArg(current, "toolUse")?.let { toolUse ->
                ToolUsePartial(
                    toolUseId = invokeNoArg(toolUse, "toolUseId") as? String,
                    name = invokeNoArg(toolUse, "name") as? String,
                    inputChunk = invokeNoArg(toolUse, "input") as? String,
                    inputDocument = invokeNoArg(toolUse, "input") as? Document,
                )
            }
        }

    fun invokeNoArg(target: Any, methodName: String): Any? =
        runCatching {
            target.javaClass.methods
                .firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?.invoke(target)
        }.getOrNull()
}

internal data class ToolUsePartial(
    val toolUseId: String? = null,
    val name: String? = null,
    val inputChunk: String? = null,
    val inputDocument: Document? = null,
)
