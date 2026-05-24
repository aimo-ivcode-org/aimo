import { describe, expect, it, vi } from 'vitest'
import { ResponseBuilder } from './ResponseBuilder'
import type { ChatMessage, ChatResponse } from './AimoClientModel'

function makeResponse(responseId: string, messages: ChatMessage[]): ChatResponse {
    return {
        chatId: 'chat-1',
        responseId,
        messages,
        createdAt: new Date('2026-04-12T00:00:00.000Z'),
    }
}

describe('ResponseBuilder', () => {
    it('aggregates message content and thinking across streamed partials', () => {
        const builder = new ResponseBuilder()

        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'hel', thinking: 't1', done: false }]))}\n`)
        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'lo', thinking: 't2', done: false }]))}\n`)
        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: '!', done: true }]))}\n`)

        const last = builder.last
        expect(last).toBeTruthy()
        if (!last) {
            throw new Error('Expected a final accumulated response')
        }
        expect(last.messages).toHaveLength(1)
        expect(last.messages[0].content).toBe('hello!')
        expect(last.messages[0].thinking).toBe('t1t2')
        expect(last.messages[0].done).toBe(true)
        expect(last.createdAt).toBeInstanceOf(Date)
    })

    it('stops concatenating a message after done=true', () => {
        const builder = new ResponseBuilder()
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined)

        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'final', done: true }]))}\n`)
        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'SHOULD_NOT_APPEND', done: true }]))}\n`)

        expect(builder.last?.messages[0].content).toBe('final')
        expect(builder.last?.messages[0].done).toBe(true)
        expect(errorSpy).toHaveBeenCalledTimes(1)
        errorSpy.mockRestore()
    })

    it('parses concatenated JSON objects with no newline separators', () => {
        const builder = new ResponseBuilder()

        const a = JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'a', done: false }]))
        const b = JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'b', done: true }]))
        builder.push(`${a}${b}`)

        expect(builder.last?.messages[0].content).toBe('ab')
    })

    it('emits raw parsed chunks to onResponse while keeping last accumulated', () => {
        const seen: ChatResponse[] = []
        const builder = new ResponseBuilder((response) => seen.push(response))

        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'hel', done: false }]))}\n`)
        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'lo', done: true }]))}\n`)

        expect(seen).toHaveLength(2)
        expect(seen[0].messages[0].content).toBe('hel')
        expect(seen[1].messages[0].content).toBe('lo')
        expect(seen[0].createdAt).toBeInstanceOf(Date)
        expect(builder.last?.messages[0].content).toBe('hello')
    })

    it('emits a completed message exactly once when it reaches done', () => {
        const doneMessages: ChatResponse[] = []
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined)
        const builder = new ResponseBuilder(undefined, (response) => doneMessages.push(response))

        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'hel', thinking: 't1', done: false }]))}\n`)
        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'lo', thinking: 't2', done: true }]))}\n`)
        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'ignored', done: true }]))}\n`)

        expect(doneMessages).toHaveLength(1)
        expect(doneMessages[0].chatId).toBe('chat-1')
        expect(doneMessages[0].responseId).toBe('r1')
        expect(doneMessages[0].createdAt).toBeInstanceOf(Date)
        expect(doneMessages[0].messages).toEqual([{
            messageId: 1,
            type: 'ASSISTANT',
            content: 'hello',
            thinking: 't1t2',
            done: true,
        }])
        expect(errorSpy).toHaveBeenCalledTimes(1)
        errorSpy.mockRestore()
    })

    it('emits done immediately when the first chunk is already complete', () => {
        const doneMessages: ChatResponse[] = []
        const builder = new ResponseBuilder(undefined, (response) => doneMessages.push(response))

        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'TOOL', content: 'tool-result', toolName: 'search', done: true }]))}\n`)

        expect(doneMessages).toHaveLength(1)
        expect(doneMessages[0].chatId).toBe('chat-1')
        expect(doneMessages[0].responseId).toBe('r1')
        expect(doneMessages[0].messages).toEqual([{
            messageId: 1,
            type: 'TOOL',
            content: 'tool-result',
            toolName: 'search',
            done: true,
        }])
    })

    it('keeps accumulators isolated by responseId', () => {
        const builder = new ResponseBuilder()

        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'one', done: true }]))}\n`)
        builder.push(`${JSON.stringify(makeResponse('r2', [{ messageId: 1, type: 'ASSISTANT', content: 'two', done: true }]))}\n`)

        expect(builder.last?.responseId).toBe('r2')
        expect(builder.last?.messages[0].content).toBe('two')
    })

    it('flushes and parses trailing JSON object without newline', () => {
        const builder = new ResponseBuilder()

        builder.push(JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'tail', done: true }])))
        builder.flush()

        expect(builder.last?.messages[0].content).toBe('tail')
    })

    it('propagates usage through streaming chunks', () => {
        const builder = new ResponseBuilder()

        // First chunk: no usage
        builder.push(`${JSON.stringify(makeResponse('r1', [{ messageId: 1, type: 'ASSISTANT', content: 'hel', done: false }]))}\n`)

        // Second chunk: includes usage
        const withUsage: ChatResponse = {
            chatId: 'chat-1',
            responseId: 'r1',
            messages: [{ messageId: 1, type: 'ASSISTANT', content: 'lo', done: true }],
            createdAt: new Date('2026-04-12T00:00:00.000Z'),
            usage: { inputTokens: 10, outputTokens: 5 },
        }
        builder.push(`${JSON.stringify(withUsage)}\n`)

        expect(builder.last?.usage).toEqual({ inputTokens: 10, outputTokens: 5 })
    })

    it('includes usage in single message done callbacks', () => {
        const doneMessages: ChatResponse[] = []
        const builder = new ResponseBuilder(undefined, (response) => doneMessages.push(response))

        const withUsage: ChatResponse = {
            chatId: 'chat-1',
            responseId: 'r1',
            messages: [{ messageId: 1, type: 'ASSISTANT', content: 'hello', done: true }],
            createdAt: new Date('2026-04-12T00:00:00.000Z'),
            usage: { inputTokens: 10, outputTokens: 5 },
        }
        builder.push(`${JSON.stringify(withUsage)}\n`)

        expect(doneMessages).toHaveLength(1)
        expect(doneMessages[0].usage).toEqual({ inputTokens: 10, outputTokens: 5 })
    })
})

