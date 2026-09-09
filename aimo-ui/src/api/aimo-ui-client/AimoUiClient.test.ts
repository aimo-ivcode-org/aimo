import { afterEach, describe, expect, it, vi } from 'vitest'
import { aimoUiClient } from './AimoUiClient'

describe('aimoUiClient', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it('uses a same-origin title URL instead of hard-coded localhost', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            text: async () => '[]',
        })
        vi.stubGlobal('fetch', fetchMock)

        await aimoUiClient.getTitles()

        expect(fetchMock).toHaveBeenCalledWith('/aimo-api/title/', expect.objectContaining({
            method: 'GET',
        }))
    })
})

