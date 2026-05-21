import { ChatMessage, ChatResponse, ChatUsage } from "./AimoClientModel";
import { normalizeChatResponse } from "./AimoClientNormalizers";

type ResponseAccumulator = {
    chatId: string,
    responseId: string,
    createdAt: Date,
    messageOrder: string[],
    messagesByKey: Map<string, ChatMessage>,
    usage?: ChatUsage,
}

/**
 * Accumulates raw streaming text, parses complete JSON objects from it using
 * two strategies (NDJSON and brace-counted concatenated JSON), optionally
 * emits each complete ChatResponse chunk to a callback, and keeps a separate
 * accumulated final response in `last`.
 *
 * Callback behavior:
 * - `onResponse`: receives each parsed/normalized response chunk.
 * - `onMessageDone`: receives a single-message `ChatResponse` once a message
 *   reaches `done === true`.
 *
 * `last` always points to the current accumulated response snapshot.
 *
 * Usage:
 *   const builder = new ResponseBuilder(onResponse, onMessageDone)
 *   builder.push(decodedChunk)   // call for each decoded stream chunk
 *   builder.flush()              // call once after the stream ends
 *   return builder.last          // final / most-recent response
 */
export class ResponseBuilder {
    private buffer = ''
    private _last: ChatResponse | null = null
    private readonly accumulatorsByResponseId = new Map<string, ResponseAccumulator>()

    constructor(
        // Called for each complete parsed response object.
        private readonly onResponse?: (response: ChatResponse) => void,
        // Called when an accumulated message reaches done=true.
        private readonly onMessageDone?: (response: ChatResponse) => void,
    ) {}

    /** The most recently emitted (complete) ChatResponse, or null if none yet. */
    get last(): ChatResponse | null {
        return this._last
    }

    /** Feed a decoded text chunk from the stream. */
    push(chunk: string): void {
        this.buffer += chunk
        this.drain()
    }

    /**
     * Call once after the stream is done to process any text that remains in
     * the buffer (e.g. a trailing object not followed by a newline).
     */
    flush(): void {
        const leftover = this.buffer.trim()
        if (!leftover) return

        const parts = leftover.split('\n').map(p => p.trim()).filter(Boolean)
        if (parts.length > 1) {
            parts.forEach(p => this.emitJson(p))
        } else {
            this.emitJson(leftover)
        }
        this.buffer = ''
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Drains the buffer by attempting two parsing strategies in order:
     * 1. NDJSON – split on newlines and emit each non-empty line.
     * 2. Brace counting – scan for balanced `{…}` objects so concatenated JSON
     *    objects (no separating newline) are also handled correctly.
     *    A basic string/escape state machine prevents braces inside string
     *    literals from being miscounted.
     */
    private drain(): void {
        // Strategy 1: newline-delimited objects
        let nlIndex: number
        while ((nlIndex = this.buffer.indexOf('\n')) >= 0) {
            const line = this.buffer.slice(0, nlIndex).trim()
            this.buffer = this.buffer.slice(nlIndex + 1)
            if (line) this.emitJson(line)
        }

        // Strategy 2: brace-counted concatenated objects
        let braceDepth = 0
        let inString = false
        let escape = false
        let start = -1
        for (let i = 0; i < this.buffer.length; i++) {
            const ch = this.buffer[i]
            if (escape)          { escape = false; continue }
            if (ch === '\\')     { escape = true;  continue }
            if (ch === '"')      { inString = !inString; continue }
            if (inString)        continue
            if (ch === '{') {
                if (braceDepth === 0) start = i
                braceDepth++
            } else if (ch === '}') {
                braceDepth--
                if (braceDepth === 0 && start >= 0) {
                    const objStr = this.buffer.slice(start, i + 1)
                    this.emitJson(objStr)
                    this.buffer = this.buffer.slice(i + 1)
                    i = -1      // restart scan on remaining buffer
                    start = -1
                }
            }
        }
    }

    private emitJson(jsonStr: string): void {
        if (!jsonStr) return
        try {
            const incoming = normalizeChatResponse(JSON.parse(jsonStr) as ChatResponse)
            this.onResponse?.(incoming)
            this._last = this.accumulate(incoming)
        } catch {
            // Malformed / incomplete JSON – ignore and wait for more data
        }
    }


    private accumulate(incoming: ChatResponse): ChatResponse {
        if (!incoming.responseId) {
            return incoming
        }

        let acc = this.accumulatorsByResponseId.get(incoming.responseId)
        if (!acc) {
            acc = {
                chatId: incoming.chatId,
                responseId: incoming.responseId,
                createdAt: incoming.createdAt,
                messageOrder: [],
                messagesByKey: new Map<string, ChatMessage>(),
                usage: incoming.usage,
            }
            this.accumulatorsByResponseId.set(incoming.responseId, acc)
        } else {
            acc.chatId = incoming.chatId || acc.chatId
            acc.createdAt = incoming.createdAt ?? acc.createdAt
            // Propagate the latest usage value if provided
            if (incoming.usage) {
                acc.usage = incoming.usage
            }
        }

        for (const msg of incoming.messages ?? []) {
            const key = this.messageKey(msg)
            const existing = acc.messagesByKey.get(key)
            if (!existing) {
                const first: ChatMessage = { ...msg }
                acc.messagesByKey.set(key, first)
                acc.messageOrder.push(key)
                if (first.done === true) {
                    this.onMessageDone?.(this.singleMessageResponse(acc, first))
                }
                continue
            }

            if (existing.done === true) {
                console.error('ResponseBuilder received a message after done=true', {
                    responseId: acc.responseId,
                    messageKey: key,
                    incoming: msg,
                })
                continue
            }

            existing.content = this.concat(existing.content, msg.content)
            existing.thinking = this.concat(existing.thinking, msg.thinking)

            if (msg.toolName !== undefined) {
                existing.toolName = msg.toolName
            }

            if (msg.done !== undefined) {
                existing.done = msg.done
            }

            if (existing.done === true) {
                this.onMessageDone?.(this.singleMessageResponse(acc, existing))
            }
        }

        return {
            chatId: acc.chatId,
            responseId: acc.responseId,
            createdAt: acc.createdAt,
            messages: acc.messageOrder
                .map((key) => acc.messagesByKey.get(key))
                .filter((m): m is ChatMessage => m != null)
                .map((m) => ({ ...m })),
            usage: acc.usage,
        }
    }

    private concat(current?: string, incoming?: string): string | undefined {
        if (!incoming) return current
        return `${current ?? ''}${incoming}`
    }

    private messageKey(message: ChatMessage): string {
        return `${message.messageId}:${message.type}`
    }

    private singleMessageResponse(acc: ResponseAccumulator, message: ChatMessage): ChatResponse {
        return {
            chatId: acc.chatId,
            responseId: acc.responseId,
            createdAt: acc.createdAt,
            messages: [{ ...message }],
            usage: acc.usage,
        }
    }
}

