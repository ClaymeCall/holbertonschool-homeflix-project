import { vi } from 'vitest'

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Handler = (url: string, init: RequestInit | undefined) => Response | Promise<Response>

/** Replaces the global fetch with a handler and returns the mock to inspect calls. */
export function mockFetch(handler: Handler) {
  const fn = vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
    Promise.resolve(handler(typeof input === 'string' ? input : input.toString(), init)),
  )
  vi.stubGlobal('fetch', fn)
  return fn
}
