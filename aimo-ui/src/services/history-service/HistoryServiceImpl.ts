import {aimoClient} from "../../api/aimo-client/AimoClient";
import type {ChatConversationInfo} from "../../api/aimo-client/AimoClientModel";
import {aimoUiClient} from "../../api/aimo-ui-client/AimoUiClient";
import {HistoryEntry, HistoryService} from "./HistoryService";

export class HistoryServiceImpl implements HistoryService {

    private subscribers: Set<(items: HistoryEntry[]) => void> = new Set()
    private cachedHistory: HistoryEntry[] | null = null;

    async fetchHistory(): Promise<HistoryEntry[]> {
        const [conversations, titles] = await Promise.all([
            aimoClient.getChatConversations(),
            aimoUiClient.getTitles()
        ])
        const titleByChatId = new Map(titles.map((conversationTitle) => [conversationTitle.chatId, conversationTitle.title]))

        const hist = conversations.map((conversation: ChatConversationInfo) => {
            const title = titleByChatId.get(conversation.chatId)

            return {
                id: conversation.chatId,
                title: title ? title : "New Chat",
            } as HistoryEntry
        })

        this.cachedHistory = hist
        this.emitUpdate(hist)

        return hist
    }

    async getHistory(): Promise<HistoryEntry[]> {
        if(this.cachedHistory) {
            return this.cachedHistory;
        } else {
            return this.fetchHistory()
        }
    }

    subscribe(sub: (items: HistoryEntry[]) => void): (() => void) {
        this.subscribers.add(sub)

        this.getHistory().then((history: HistoryEntry[]) => {
            // if still subscribed, trigger sub
            if (this.subscribers.has(sub)) {
                sub(history)
            }
        })

        return () => {
            this.subscribers.delete(sub)
        }
    }

    private emitUpdate(items: HistoryEntry[]) {
        this.subscribers.forEach((sub) => sub(items))
    }
}
