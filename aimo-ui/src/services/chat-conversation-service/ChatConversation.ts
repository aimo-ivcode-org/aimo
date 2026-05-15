import { ChatConversationImpl } from './ChatConversationImpl.js'

export interface ChatConversation {
    get id(): string | null
    setId(id?: string, push?: boolean): Promise<string>
    clear(push?: boolean): Promise<void>
    onChange(cb: (id: string | null) => Promise<void>): () => void
}

export const chatConversation: ChatConversation = new ChatConversationImpl()
