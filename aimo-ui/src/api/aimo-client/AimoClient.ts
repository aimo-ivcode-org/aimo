import {
    ChatCallback,
    ChatConversationInfo,
    ChatHistoryRequest,
    ChatRequest,
    ChatResponse,
} from "./AimoClientModel";
import { normalizeChatResponse, normalizeHistoryRequest } from "./AimoClientNormalizers";
import {ApiClient} from "../api-client/ApiClient";
import { apiBaseUrl } from "../api-client/apiBaseUrl";
import {ResponseBuilder} from "./ResponseBuilder";

const CONTROLLER_CHAT = "/aimo-api/chat"
const CONTROLLER_HISTORY = "/aimo-api/history"
const CONTROLLER_CONVERSATION = "/aimo-api/conversation"

export interface AimoClient {
    chat: (chatId: string, request: ChatRequest, callback: ChatCallback) => Promise<ChatResponse | null>
    getHistory: (chatId: string) => Promise<ChatHistoryRequest[]>
    createChatConversation: () => Promise<ChatConversationInfo>
    getChatConversations: () => Promise<ChatConversationInfo[]>
    deleteChatConversation: (chatId: string) => Promise<void>
}

class AimoClientImpl extends ApiClient implements AimoClient {

    constructor(baseUrl: string) {
        // remove trailing slash(es) if present
        super(baseUrl)
    }

    chat = (
        chatId: string,
        request: ChatRequest,
        callback: ChatCallback
    ) => this.POST(
        CONTROLLER_CHAT,
        `/${encodeURIComponent(chatId)}`,
        {
            'Content-Type': 'application/json',
            'X-Timezone-Offset': String(new Date().getTimezoneOffset()),
        },
        request,
    ).then(async res => {
        if (!res.body) {
            // No stream support; try to parse whole body as JSON
            const txt = await res.text()
            return normalizeChatResponse(JSON.parse(txt) as ChatResponse)
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        const builder = new ResponseBuilder(
            (response) => callback?.onResponseChunk?.(response),
            (response) => callback?.onMessageComplete?.(response),
        )

        let done = false
        while (!done) {
            const { value, done: streamDone } = await reader.read()
            done = streamDone
            if (value) {
                const chunk = decoder.decode(value, { stream: true })
                builder.push(chunk)
            }
        }

        builder.flush()
        if (builder.last) {
            callback?.onComplete?.(builder.last)
        }
        return builder.last
    })

    getHistory = (
        chatId: string
    ) => this.GET(CONTROLLER_HISTORY, `/${encodeURIComponent(chatId)}`).then(async res => {
        if (!res.ok) {
            throw new Error(`failed to fetch chat history: ${res.status} ${res.statusText}`)
        }

        const txt = await res.text()
        const parsed = JSON.parse(txt) as ChatHistoryRequest[]

        return parsed.map((req) => normalizeHistoryRequest(req))
    })

    createChatConversation = () => this.POST(CONTROLLER_CONVERSATION, "/").then(async res => {
        if(!res.ok) {
            throw new Error(`failed to create conversation: ${res.status} ${res.statusText}`)
        }

        const txt = await res.text()
        const parsed = JSON.parse(txt)

        return parsed as ChatConversationInfo
    })

    deleteChatConversation = (
        chatId: string
    ) => this.DELETE(CONTROLLER_CONVERSATION, `/${encodeURIComponent(chatId)}`).then(async res => {
        if(!res.ok) {
            throw new Error(`failed to delete conversation: ${res.status} ${res.statusText}`)
        }
    })

    getChatConversations = () => this.GET(CONTROLLER_CONVERSATION, "/").then(async res => {
        if(!res.ok) {
            throw new Error(`failed to get conversations: ${res.status} ${res.statusText}`)
        }

        const txt = await res.text()
        const parsed = JSON.parse(txt)

        return parsed as ChatConversationInfo[]
    })

}

export const aimoClient: AimoClient = new AimoClientImpl(apiBaseUrl)
