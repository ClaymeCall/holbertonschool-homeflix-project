import { vi } from 'vitest'
import type { Account, Profile } from '../api/identity'

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function emptyResponse(status = 204): Response {
  return new Response(null, { status })
}

export type Route = (request: { url: URL; init: RequestInit | undefined }) => Response | Promise<Response>

export const testProfile: Profile = { id: 'p1', name: 'Camille', createdAt: '2026-11-01T09:00:00Z' }

/** A signed-in administrator with an active profile, so the viewing screens open directly. */
export const testAccount: Account = { id: 'a1', email: 'camille@home.example', admin: true, profileId: testProfile.id }

/** Routes for a signed-in household where setup is already done. */
export function signedInRoutes(account: Account = testAccount): Record<string, Route> {
  return {
    'GET /api/identity/setup/status': () => jsonResponse({ setupRequired: false }),
    'GET /api/identity/auth/me': () => jsonResponse(account),
    'GET /api/identity/profiles': () => jsonResponse([testProfile]),
    'POST /api/identity/auth/refresh': () => jsonResponse(account),
    'POST /api/identity/auth/sign-out': () => emptyResponse(),
  }
}

/**
 * Replaces the global fetch with routes keyed by "METHOD /path" (query string
 * ignored in the key). Unknown routes answer 404. Returns the mock to inspect calls.
 */
export function mockApi(routes: Record<string, Route>) {
  const fn = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === 'string' ? input : input.toString(), 'http://homeflix.test')
    const route = routes[`${init?.method ?? 'GET'} ${url.pathname}`]
    return Promise.resolve(route ? route({ url, init }) : jsonResponse({ error: 'not_found', message: 'This address does not exist.' }, 404))
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

/** Requests made to the given "METHOD /path" route. */
export function callsTo(fn: ReturnType<typeof mockApi>, key: string) {
  return fn.mock.calls.filter(([input, init]) => {
    const url = new URL(String(input), 'http://homeflix.test')
    return `${init?.method ?? 'GET'} ${url.pathname}` === key
  })
}
