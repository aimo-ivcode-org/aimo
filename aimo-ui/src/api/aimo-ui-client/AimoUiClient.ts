import {ApiClient} from "../api-client/ApiClient";
import { apiBaseUrl } from "../api-client/apiBaseUrl";

const CONTROLLER_TITLE = "/aimo-api/title";

export interface ConversationTitle {
    chatId: string
    source: string
    title: string | null
}

export interface AimoUiClient {
    getTitles: () => Promise<ConversationTitle[]>
    getTitle: (chatId: string) => Promise<ConversationTitle | null>
    setTitle: (chatId: string, title: string) => Promise<void>
}

class AimoUiClientImpl extends ApiClient implements AimoUiClient {
    constructor(baseUrl: string) {
        super(baseUrl);
    }

    getTitles = () => this.GET(CONTROLLER_TITLE, "/").then(async res => {
        if (!res.ok) {
            throw new Error(`failed to get titles: ${res.status} ${res.statusText}`)
        }

        const txt = await res.text()
        return txt ? JSON.parse(txt) as ConversationTitle[] : []
    })

    getTitle = (chatId: string) => this.GET(CONTROLLER_TITLE, `/${encodeURIComponent(chatId)}`).then(async res => {
        if (!res.ok) {
            throw new Error(`failed to get title: ${res.status} ${res.statusText}`)
        }

        const txt = await res.text()
        return txt ? JSON.parse(txt) as ConversationTitle | null : null
    })

    setTitle = (
        chatId: string,
        title: string
    ) => this.PUT(CONTROLLER_TITLE, `/${encodeURIComponent(chatId)}/${encodeURIComponent(title)}`).then(async res => {
        if (!res.ok) {
            throw new Error(`failed to set title: ${res.status} ${res.statusText}`)
        }
    })
}

export const aimoUiClient: AimoUiClient = new AimoUiClientImpl(apiBaseUrl)
