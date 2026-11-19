import { afterEach, describe, expect, it, vi } from 'vitest'
import { emptyResponse, jsonResponse } from '../test/http'
import { ApiError, apiRequest, setSessionHandlers } from './client'
import { describeError } from './errors'

function stubFetch(...responses: Response[]) {
  const fn = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => {
    const next = responses.shift()
    return next ? Promise.resolve(next) : Promise.reject(new Error('unexpected request'))
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

let unregister: (() => void) | undefined
afterEach(() => unregister?.())

describe('apiRequest', () => {
  it('sends JSON on the same origin and returns the parsed body', async () => {
    const fetch = stubFetch(jsonResponse({ ok: true }))
    await expect(apiRequest('/api/x', { method: 'POST', body: { a: 1 } })).resolves.toEqual({ ok: true })
    expect(fetch.mock.calls[0][1]).toMatchObject({ method: 'POST', credentials: 'same-origin', body: '{"a":1}' })
  })

  it('accepts responses with no body', async () => {
    stubFetch(emptyResponse(201))
    await expect(apiRequest('/api/x', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('throws an ApiError with the status, the code and the server message', async () => {
    stubFetch(jsonResponse({ error: 'not_found', message: 'This address does not exist.' }, 404))
    const error = await apiRequest('/api/x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, code: 'not_found', serverMessage: 'This address does not exist.', fields: {} })
    expect(describeError(error)).toBe('This address does not exist.')
  })

  it('keeps the message of each field rejected by the server', async () => {
    stubFetch(
      jsonResponse(
        { error: 'validation_failed', message: 'Some fields are invalid.', fields: { email: 'Enter a valid email address.' } },
        400,
      ),
    )
    const error = await apiRequest('/api/x', { method: 'POST' }).catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 400, code: 'validation_failed', fields: { email: 'Enter a valid email address.' } })
    expect(describeError(error)).toBe('Some fields are invalid.')
  })

  it('does not trust the body of an unexpected 5xx', async () => {
    stubFetch(jsonResponse({ error: 'internal_error', message: 'java.lang.NullPointerException' }, 500))
    const error = await apiRequest('/api/x').catch((e: unknown) => e)
    expect(describeError(error)).toBe('Something went wrong on our side. Please try again in a moment.')
  })

  it('writes its own text when the answer is not the common error body', async () => {
    stubFetch(new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }))
    const error = await apiRequest('/api/x').catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 502, code: undefined, serverMessage: undefined, fields: {} })
    expect(describeError(error)).toBe('Something went wrong on our side. Please try again in a moment.')
  })

  it('writes its own text when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    const error = await apiRequest('/api/x').catch((e: unknown) => e)
    expect(describeError(error)).toBe('HomeFlix cannot be reached. Check your connection, then try again.')
  })

  it('reports network failures with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    const error = await apiRequest('/api/x').catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 0 })
    expect(describeError(error)).toMatch(/HomeFlix cannot be reached/)
  })

  it('renews an expired session once and retries the request', async () => {
    const fetch = stubFetch(jsonResponse({}, 401), jsonResponse({ ok: true }))
    const renew = vi.fn(() => Promise.resolve())
    unregister = setSessionHandlers({ renew, onExpired: vi.fn() })

    await expect(apiRequest('/api/x')).resolves.toEqual({ ok: true })
    expect(renew).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('reports the session as expired when renewal fails', async () => {
    stubFetch(jsonResponse({}, 401))
    const onExpired = vi.fn()
    unregister = setSessionHandlers({ renew: () => Promise.reject(new ApiError(401)), onExpired })

    await expect(apiRequest('/api/x')).rejects.toMatchObject({ status: 401 })
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it('does not try to renew when asked not to', async () => {
    stubFetch(jsonResponse({}, 401))
    const renew = vi.fn(() => Promise.resolve())
    unregister = setSessionHandlers({ renew, onExpired: vi.fn() })

    await expect(apiRequest('/api/x', { renewSession: false })).rejects.toMatchObject({ status: 401 })
    expect(renew).not.toHaveBeenCalled()
  })
})
