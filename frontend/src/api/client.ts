/**
 * Minimal JSON client for the HomeFlix APIs.
 *
 * Every call goes through the gateway on the same origin (ADR-0013, ADR-0017),
 * so the HttpOnly session cookie is sent automatically (ADR-0009). The token
 * is never read or stored in JavaScript.
 */

/** Status 0 means the request never reached a server (network failure). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined

  constructor(status: number, code?: string) {
    super(`API request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal } = options
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0)
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorCode(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/**
 * Reads only the machine-readable error code. The server's message is never
 * shown to the user: the interface writes its own plain-language text (SEC-04).
 */
async function readErrorCode(response: Response): Promise<string | undefined> {
  try {
    const data: unknown = await response.json()
    if (data && typeof data === 'object' && 'code' in data && typeof data.code === 'string') {
      return data.code
    }
  } catch {
    // No JSON body: the status alone describes the error.
  }
  return undefined
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
