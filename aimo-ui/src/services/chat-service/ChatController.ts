import {aimoClient} from '../../api/aimo-client/AimoClient'
import type {ChatHistoryRequest, ChatMessage, ChatResponse} from '../../api/aimo-client/AimoClientModel'
import type {ChatHandle} from '../../components/chat/Chat'
import React from "react";
import {chatConversation} from "../chat-conversation-service/ChatConversation";
import { historyService } from "../history-service/HistoryService";
import { chatService } from "./ChatService";

/**
 * Transforms an array of chat history requests into a flat array of chat responses.
 * Sorts requests chronologically and maps each request to a response object.
 *
 * @param {ChatHistoryRequest[]} requests - Array of chat history requests to flatten
 * @returns {ChatResponse[]} Array of chat responses sorted by creation time
 */
function flattenHistory(requests: ChatHistoryRequest[]): ChatResponse[] {
    const sorted = [...requests].sort((a, b) => {
        const aTime = a.createdAt.getTime()
        const bTime = b.createdAt.getTime()
        return aTime - bTime
    })
    return sorted.map((r) => ({
        chatId: r.chatId,
        responseId: r.requestId,
        messages: r.messages,
        createdAt: r.createdAt,
    }))
}

/**
 * Extracts the last message from a chat response.
 * Returns undefined if the response is null or contains no messages.
 *
 * @param {ChatResponse | null} resp - The chat response to extract the last message from
 * @returns {ChatMessage | undefined} The last message in the response, or undefined if none exists
 */
function lastMessageFromResponse(resp: ChatResponse | null): ChatMessage | undefined {
    if (!resp?.messages?.length) return undefined
    return resp.messages[resp.messages.length - 1]
}

/**
 * Calculates incremental content and thinking deltas from a streaming chat message.
 * Handles cases where the API sends growing content or only new fragments.
 * Tracks accumulated content/thinking in the accumulator object and returns only the new delta.
 *
 * @param {Object} acc - Accumulator object tracking cumulative content and thinking
 * @param {string} acc.content - Previously accumulated content
 * @param {string} acc.thinking - Previously accumulated thinking
 * @param {ChatMessage} msg - The current message from the stream containing new content
 * @returns {Object} Object containing the delta (new) content and thinking since the last message
 * @returns {string} deltaContent - Incremental content added in this message
 * @returns {string} deltaThinking - Incremental thinking added in this message
 */
function streamTextDeltas(
    acc: { content: string; thinking: string },
    msg: ChatMessage
): { deltaContent: string; deltaThinking: string } {
    const content = msg.content ?? ''
    const thinking = msg.thinking ?? ''
    let deltaContent: string
    if (content.startsWith(acc.content)) {
        deltaContent = content.slice(acc.content.length)
        acc.content = content
    } else {
        deltaContent = content
        acc.content += content
    }
    let deltaThinking: string
    if (thinking.startsWith(acc.thinking)) {
        deltaThinking = thinking.slice(acc.thinking.length)
        acc.thinking = thinking
    } else {
        deltaThinking = thinking
        acc.thinking += thinking
    }
    return { deltaContent, deltaThinking }
}

/**
 * Generates a unique key for tracking stream accumulation of a specific message within a response.
 * Combines response ID, message ID, and message type to create a unique identifier.
 *
 * @param {string} responseId - The ID of the API response
 * @param {ChatMessage} msg - The message object containing messageId and type
 * @returns {string} A composite key in format: `responseId:messageId:type`
 */
function streamAccKey(responseId: string, msg: ChatMessage): string {
    return `${responseId}:${msg.messageId}:${msg.type}`
}

/**
 * Controller class that manages chat interactions between the UI and the backend API.
 *
 * Responsibilities:
 * - Attaches/detaches to a Chat component via React refs
 * - Listens for chat conversation changes and fetches history
 * - Handles sending user messages and streaming responses back to the Chat component
 * - Manages error handling and UI state (busy, input disabled)
 *
 * The controller uses an imperative API (`addResponse`, `appendMessage`) to update the Chat
 * component in real-time as messages stream in from the backend.
 */
export class ChatController {
    private chatHandle?: React.RefObject<ChatHandle | null> | null

    private unsubscribeConversationChange: (() => void) | null = null

    /**
     * Attaches the controller to a Chat component.
     * Sets up a listener for chat conversation changes that automatically loads chat history
     * when a new conversation is selected. If already attached, this is a no-op.
     *
     * Behavior:
     * - When the conversation ID changes to null, clears all responses from the Chat component
     * - When a valid conversation ID is set, disables input, fetches history, and updates the component
     * - If history fetch fails, clears the selected conversation and re-throws the error
     *
     * @param {React.RefObject<ChatHandle | null>} chatHandle - Ref to the Chat component's imperative API
     */
    attach(chatHandle: React.RefObject<ChatHandle | null>) {
        if (this.chatHandle) {
            return
        }
        if (this.unsubscribeConversationChange) {
            this.unsubscribeConversationChange()
        }

        this.chatHandle = chatHandle
        this.unsubscribeConversationChange = chatConversation.onChange(async (id: string | null) => {
            if (!id) {
                this.chatHandle?.current?.setResponses([])
            } else {
                const enableInput = this.chatHandle?.current?.disableInput()
                try {
                    const history = await aimoClient.getHistory(id)
                    const responses = flattenHistory(history)
                    this.chatHandle?.current?.setResponses(responses)

                    void historyService.fetchHistory()
                } catch (error) {
                    void chatConversation.clear(false)
                    throw error
                } finally {
                    enableInput?.()
                }
            }
        })
    }

    /**
     * Detaches the controller from the Chat component.
     * Clears the reference to the Chat handle and unsubscribes from conversation changes.
     * After this method is called, the controller will no longer respond to chat events.
     */
    detach() {
        this.chatHandle = undefined

        if (this.unsubscribeConversationChange) {
            this.unsubscribeConversationChange()
            this.unsubscribeConversationChange = null
        }
    }

    /**
     * Callback handler for sending chat messages. Pass this method to the Chat component as `onSend`.
     *
     * Workflow:
     * 1. Creates a new conversation if one doesn't exist
     * 2. Adds the user message to the chat display
     * 3. Sends the message to the backend API with streaming enabled
     * 4. Appends streamed message chunks in real-time to the Chat component
     * 5. Handles errors by adding an error message response
     * 6. Manages UI state (busy indicator, input disabled) during the request
     *
     * @param {ChatMessage} userMsg - The user's message to send (should contain content)
     * @returns {Promise<ChatMessage | undefined>} The last message from the API response, or undefined if failed
     * @throws {Error} If the Chat component is not attached or if the API request fails
     */
    onSend = async (userMsg: ChatMessage): Promise<ChatMessage | undefined> => {
        let id = chatConversation.id
        if (!id) {
            const newChat = await aimoClient.createChatConversation()

            // TODO: I need all listeners to finish before proceeding
            id = await chatConversation.setId(newChat.chatId)
            // Create a response wrapper for the user message
            const userResponse: ChatResponse = {
                chatId: id,
                responseId: `local-${Date.now()}`,
                messages: [userMsg],
                createdAt: new Date(),
            }
            this.chatHandle?.current?.addResponse(userResponse)
        }

        const handle = this.chatHandle?.current
        if (!handle) {
            throw new Error('Chat handle is not attached. Cannot send message.')
        }

        const req = { prompt: userMsg.content ?? '', stream: true as const }
        const streamAccByRequestAndMessage = new Map<string, { content: string; thinking: string }>()
        const unsetBusy = handle.busy()
        const enableInput = handle.disableInput()
        try {
            return await chatService.chat(id, req, {
                onResponseChunk: (resp: ChatResponse) => {
                    const list = resp.messages
                    if (!list?.length) return

                    unsetBusy()

                    const responseId = resp.responseId
                    for (const apiMsg of list) {
                        const accKey = streamAccKey(responseId, apiMsg)
                        let acc = streamAccByRequestAndMessage.get(accKey)
                        if (!acc) {
                            acc = { content: '', thinking: '' }
                            streamAccByRequestAndMessage.set(accKey, acc)
                            handle.addResponse(resp)
                            acc.content = apiMsg.content ?? ''
                            acc.thinking = apiMsg.thinking ?? ''
                        } else {
                            const { deltaContent, deltaThinking } = streamTextDeltas(acc, apiMsg)
                            if (deltaContent || deltaThinking) {
                                handle.appendMessage(resp, {
                                    messageId: apiMsg.messageId,
                                    type: apiMsg.type,
                                    content: deltaContent,
                                    thinking: deltaThinking,
                                    toolName: apiMsg.toolName
                                })
                            }
                        }
                    }
                },
            }).then((resp) => lastMessageFromResponse(resp))
        } catch (err) {
            const errText = typeof err === 'string' ? err : (err instanceof Error ? err.message : 'Error')
            try {
                const errorResponse: ChatResponse = {
                    chatId: id,
                    responseId: `error-${Date.now()}`,
                    messages: [{
                        messageId: Date.now(),
                        type: 'SYSTEM',
                        content: `[Error] ${errText}`,
                    }],
                    createdAt: new Date(),
                }
                handle.addResponse(errorResponse)
            } catch {
                // ignore
            }
            throw err
        } finally {
            unsetBusy()
            enableInput()
        }
    }
}
