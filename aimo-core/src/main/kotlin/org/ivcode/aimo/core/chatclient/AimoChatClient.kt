package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatRequest
import org.ivcode.aimo.core.model.AimoChatResponse
import java.util.UUID

/**
 * Lightweight client abstraction used by AIMO to perform chat interactions with a model provider.
 *
 * Implementations represent a single conversation bound to [chatId] and are responsible for
 * interacting with an LLM provider (or provider adapter). Typical responsibilities include:
 *
 *  - Sending prompts and context to the model and returning assembled responses.
 *  - Streaming partial responses back to callers when supported.
 *  - Managing conversation-level state for the duration of a call (for example: message ids,
 *    tool-call lifecycles, or temporary buffers) while the core handles durable persistence via the
 *    DAO layer.
 *  - Invoking model-directed tools (local `@Tool` callbacks or remote MCP tools) when the LLM
 *    requests tool execution and surfacing tool call metadata (tool name, call id, result) in the
 *    produced [AimoChatResponse].
 */
interface AimoChatClient {
    /**
     * The unique identifier of the conversation this client instance is bound to.
     *
     * This value is used by the core to correlate requests, persist history, and scope durable
     * conversation metadata.
     */
    val chatId: UUID

    /**
     * Execute a single (non-streaming) chat request. The call returns a completed
     * [AimoChatResponse] containing all assistant messages and any usage/diagnostic information.
     *
     * @param request the chat request containing prompt, context and other model options
     * @return the completed chat response produced by the model provider
     */
    fun chat(request: AimoChatRequest): AimoChatResponse

    /**
     * Execute a streaming chat request. The provided [callback] will be invoked one or more times
     * with incremental [AimoChatResponse] objects as the model produces partial output. Implementations
     * should also return a final [AimoChatResponse] when the stream completes.
     *
     * Callers may use the callback to forward partial assistant chunks (for example, to an HTTP stream
     * or UI) while the final full response is still being produced.
     *
     * @param request the chat request to execute
     * @param callback invoked for each partial or final response produced by the model
     * @return the final completed chat response once streaming finishes
     */
    fun chatStream(request: AimoChatRequest, callback: (AimoChatResponse) -> Unit): AimoChatResponse
}
