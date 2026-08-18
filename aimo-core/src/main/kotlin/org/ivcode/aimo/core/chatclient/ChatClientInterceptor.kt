package org.ivcode.aimo.core.chatclient

import org.ivcode.aimo.core.model.AimoChatRequest
import org.ivcode.aimo.core.model.AimoChatResponse

/**
 * Interceptor for chat client (request-level) operations.
 *
 * Handles cross-cutting concerns at the chat request layer such as:
 * - Guard-rails (validate user input, filter responses)
 * - Security filtering (redact sensitive data)
 * - Logging and tracing (observe chat operations)
 * - Error handling (retry, fallback, exception mapping)
 * - Caching (cache chat responses)
 *
 * **NOT interchangeable with ConversationInterceptor** — different operations, different signatures.
 *
 * Interceptors use an around-style continuation pattern where each interceptor can read/modify
 * the request and context before calling `next` to pass control to the next interceptor or base
 * operation. Modifications to request and context are visible to downstream handlers.
 */
interface ChatClientInterceptor {
    /**
     * Around-style interception using continuation pattern.
     *
     * Intercept a chat request, optionally modifying the request before
     * delegating to the core operation (or next interceptor).
     *
     * Interceptors are invoked once per chat operation. For streaming chat, the interceptor
     * wraps the entire stream lifecycle (not individual chunks).
     *
     * Request already contains a `context` map (see [AimoChatRequest.context]) with parameters
     * like `chatId`, `requestId`, `userId`, `requestMetadata`, etc. If an interceptor needs to
     * modify context, it should create a new request with an updated context map.
     *
     * @param request The chat request containing the prompt and context; interceptor may build
     *                a new request if modifications are needed.
     * @param next Continuation callback that receives the (possibly modified) request
     *             and performs the core chat operation or calls the next interceptor.
     * @return the [AimoChatResponse] from the operation
     */
    fun aroundChat(
        request: AimoChatRequest,
        next: (request: AimoChatRequest) -> AimoChatResponse
    ): AimoChatResponse = next(request)
}

/**
 * Compose chat client interceptors into a single continuation chain.
 *
 * Placed here so the composition logic lives next to the interceptor definition.
 */
internal fun composeChatInterceptors(
    interceptors: List<ChatClientInterceptor>,
    base: (AimoChatRequest) -> AimoChatResponse
): (AimoChatRequest) -> AimoChatResponse {
    return interceptors.foldRight(base) { interceptor, next ->
        { request: AimoChatRequest ->
            interceptor.aroundChat(request) { req -> next(req) }
        }
    }
}

