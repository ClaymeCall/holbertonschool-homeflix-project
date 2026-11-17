import { describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch } from '../test/http'
import { ApiError, apiRequest } from './client'
import { describeError } from './errors'

describe('apiRequest', () => {
  it('sends JSON on the same origin and returns the parsed body', async () => {
    const fetch = mockFetch(() => jsonResponse({ ok: true }))
    await expect(apiRequest('/api/x', { method: 'POST', body: { a: 1 } })).resolves.toEqual({ ok: true })
    const [, init] = fetch.mock.calls[0]
    expect(init).toMatchObject({ method: 'POST', credentials: 'same-origin', body: '{"a":1}' })
  })

  it('throws an ApiError with the status and code, never the server message', async () => {
    mockFetch(() => jsonResponse({ code: 'MEDIA_NOT_FOUND', message: 'java.io.FileNotFoundException' }, 404))
    const error = await apiRequest('/api/x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, code: 'MEDIA_NOT_FOUND' })
    expect(describeError(error)).not.toContain('java')
  })

  it('reports network failures with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    const error = await apiRequest('/api/x').catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 0 })
    expect(describeError(error)).toMatch(/Impossible de joindre HomeFlix/)
  })
})
