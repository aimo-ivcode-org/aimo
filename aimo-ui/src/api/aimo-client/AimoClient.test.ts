import { afterEach, describe, expect, it, vi } from 'vitest'
import { aimoClient } from './AimoClient'

describe('aimoClient', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it('uses a same-origin chat history URL instead of hard-coded localhost', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            text: async () => '[]',
        })
        vi.stubGlobal('fetch', fetchMock)

        await aimoClient.getHistory('chat id/with spaces')

        expect(fetchMock).toHaveBeenCalledWith('/aimo-api/history/chat%20id%2Fwith%20spaces', expect.objectContaining({
            method: 'GET',
        }))
    })
})

