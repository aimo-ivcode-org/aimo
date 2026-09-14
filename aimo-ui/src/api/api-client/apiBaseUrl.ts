/**
 * Resolves the browser-visible API base URL.
 *
 * Defaults to same-origin requests so the packaged UI works when served by the
 * Spring Boot app.
 */
export function resolveApiBaseUrl(baseUrl?: string): string {
    return (baseUrl?.trim() ?? '').replace(/\/+$/, '')
}

export const apiBaseUrl = resolveApiBaseUrl()


