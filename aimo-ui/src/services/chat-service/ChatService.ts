import {
    ChatCallback,
    ChatRequest,
    ChatResponse,
    ChatMessage
} from "../../api/aimo-client/AimoClientModel";
import {aimoClient, AimoClient} from "../../api/aimo-client/AimoClient";

/**
 * Callback function type for message subscriptions
 */
export type MessageSubscriber = (message: ChatMessage) => void;

/**
 * Unsubscribe function type
 */
export type Unsubscribe = () => void;

/**
 * Options for subscribing to messages
 */
export interface SubscriptionOptions {
    /**
     * Filter by message type
     */
    types?: Array<'user' | 'assistant' | 'system'>;
}

/**
 * Chat Service interface for managing chat messages and subscriptions
 */
export interface ChatService {
    /**
     * Subscribe to incoming chat messages
     * @param callback Function to be called when a message is received
     * @param options Optional subscription options including type filters
     * @returns Unsubscribe function to remove the subscription
     */
    subscribe(callback: MessageSubscriber, options?: SubscriptionOptions): Unsubscribe;

    /**
     * Unsubscribe from chat messages
     * @param callback The callback function to remove
     */
    unsubscribe(callback: MessageSubscriber): void;

    /**
     * Send a chat message
     * @param chatId The conversation ID
     * @param request The chat request containing prompt and stream settings
     * @param callback Callback for handling chat responses
     * @returns Promise that resolves with the chat response
     */
    chat(chatId: string, request: ChatRequest, callback: ChatCallback): Promise<ChatResponse | null>;
}



class ChatServiceImpl implements ChatService {
    private readonly subscribers: Map<MessageSubscriber, SubscriptionOptions | undefined> = new Map()

    constructor(private readonly client: AimoClient) {}

    subscribe(callback: MessageSubscriber, options?: SubscriptionOptions): Unsubscribe {
        this.subscribers.set(callback, options)
        return () => this.unsubscribe(callback)
    }

    unsubscribe(callback: MessageSubscriber): void {
        this.subscribers.delete(callback)
    }

    async chat(chatId: string, request: ChatRequest, callback: ChatCallback): Promise<ChatResponse | null> {
        const wrappedCallback: ChatCallback = {
            onResponseChunk: (response: ChatResponse) => {
                callback.onResponseChunk?.(response)
            },
            onMessageComplete: (response: ChatResponse) => {
                callback.onMessageComplete?.(response)
                for (const message of response.messages ?? []) {
                    this.notifySubscribers(message)
                }
            },
            onComplete: (response: ChatResponse) => {
                callback.onComplete?.(response)
            }
        }
        return this.client.chat(chatId, request, wrappedCallback)
    }

    private notifySubscribers(message: ChatMessage): void {
        for (const [subscriber, options] of this.subscribers) {
            if (this.matchesFilter(message, options)) {
                subscriber(message)
            }
        }
    }

    private matchesFilter(message: ChatMessage, options?: SubscriptionOptions): boolean {
        if (!options?.types?.length) return true
        const msgType = message.type.toLowerCase() as 'user' | 'assistant' | 'system'
        return options.types.includes(msgType)
    }
}

export const chatService: ChatService = new ChatServiceImpl(aimoClient);