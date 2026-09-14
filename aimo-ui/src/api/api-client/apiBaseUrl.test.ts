import { describe, expect, it } from 'vitest'
import { resolveApiBaseUrl } from './apiBaseUrl'

describe('resolveApiBaseUrl', () => {
    it('defaults to same-origin requests when no override is configured', () => {
        expect(resolveApiBaseUrl(undefined)).toBe('')
    })

    it('trims whitespace and trailing slashes from an explicit override', () => {
        expect(resolveApiBaseUrl('  http://localhost:8080///  ')).toBe('http://localhost:8080')
    })
})

