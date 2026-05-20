export interface ChatRequest {
    prompt: string,
    stream: boolean,
}

export interface ChatResponse {
    chatId: string,
    responseId: string,
    messages: ChatMessage[],
    createdAt: Date,
    usage?: ChatUsage,
}

export type MessageType = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL';

export interface ChatMessage {
    messageId: number,
    type: MessageType,
    content?: string,
    thinking?: string,
    toolName?: string,
    done?: boolean,
}

export interface ChatHistoryRequest {
    chatId: string,
    requestId: string,
    messages: ChatMessage[],
    createdAt: Date,
}

export interface ChatConversationInfo {
    chatId: string,
}

export interface ChatUsage {
    inputTokens?: number,
    outputTokens?: number,
    promptCache?: ChatPromptCacheUsage,
}

export interface ChatPromptCacheUsage {
    cacheReadInputTokens: number,
    cacheWriteInputTokens: number,
}

/**
 * Streaming callback contract for chat requests.
 *
 * Event order:
 * 1) `onResponseChunk` fires for each complete parsed response object from the stream.
 * 2) `onMessageComplete` fires when a single message reaches `done === true`.
 *    The payload is a `ChatResponse` wrapper with a single fully accumulated
 *    message and includes `chatId`, `responseId`, and `createdAt`.
 * 3) `onComplete` fires once after stream parsing/accumulation finishes.
 */
export interface ChatCallback {
    /** Raw normalized response chunk emitted as soon as it is parsed. */
    onResponseChunk?: (response: ChatResponse) => void,
    /** Single-message response emitted once when that message becomes done. */
    onMessageComplete?: (response: ChatResponse) => void,
    /** Final accumulated response emitted once after stream completion. */
    onComplete?: (response: ChatResponse) => void,
}
