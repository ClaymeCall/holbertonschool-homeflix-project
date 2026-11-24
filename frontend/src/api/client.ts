/**
 * Minimal JSON client for the HomeFlix APIs.
 *
 * Every call goes through the gateway on the same origin (ADR-0013, ADR-0017),
 * so the HttpOnly session cookie is sent automatically (ADR-0009). The token
 * is never read or stored in JavaScript.
 */

/**
 * Status 0 means the request never reached a server (network failure).
 * `code` is the machine-readable `error` of the response body, `serverMessage`
 * the sentence the server wrote for the user, and `fields` the message of each
 * field the server rejected (validation errors). They are undefined or empty
 * when the answer was not the common error body (ADR-0020, ADR-0021).
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly serverMessage: string | undefined
  readonly fields: Readonly<Record<string, string>>

  constructor(
    status: number,
    body: { code?: string; message?: string; fields?: Readonly<Record<string, string>> } = {},
  ) {
    super(`API request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.serverMessage = body.message
    this.fields = body.fields ?? {}
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  /** Try to renew an expired session once on 401. Off for the identity session calls themselves. */
  renewSession?: boolean
  /** Lets the request finish after the page is closed, for a last save (fetch `keepalive`). */
  keepalive?: boolean
}

interface SessionHandlers {
  renew: () => Promise<void>
  onExpired: () => void
}

let sessionHandlers: SessionHandlers | null = null
let renewing: Promise<boolean> | null = null

/** Registers how to renew the session and what to do when it has expired. Returns an unregister function. */
export function setSessionHandlers(handlers: SessionHandlers): () => void {
  sessionHandlers = handlers
  return () => {
    if (sessionHandlers === handlers) sessionHandlers = null
  }
}

/** Renews the session, sharing one renewal between concurrent callers. */
function renewOnce(handlers: SessionHandlers): Promise<boolean> {
  renewing ??= handlers.renew().then(
    () => true,
    () => false,
  ).finally(() => {
    renewing = null
  })
  return renewing
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  const handlers = sessionHandlers
  if (response.status === 401 && options.renewSession !== false && handlers) {
    if (await renewOnce(handlers)) {
      return parse<T>(await send(path, options))
    }
    handlers.onExpired()
  }
  return parse<T>(response)
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const { method = 'GET', body, signal, keepalive } = options
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  try {
    return await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
      signal,
      keepalive,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0)
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await readErrorBody(response)
    throw new ApiError(response.status, body)
  }
  // 201 or 204 responses may have no body.
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/**
 * Reads the error body `{"error", "message", "fields"}` of the gateway and the
 * services (ADR-0020). The `message` is written for the user and the interface
 * shows it as it is (ADR-0021). An answer that is not this body, such as a page
 * from a proxy, gives an empty result, and the status alone describes the error.
 */
async function readErrorBody(
  response: Response,
): Promise<{ code?: string; message?: string; fields: Record<string, string> }> {
  try {
    const data: unknown = await response.json()
    if (data && typeof data === 'object') {
      const text = (value: unknown) => (typeof value === 'string' && value.trim() ? value : undefined)
      const fields: Record<string, string> = {}
      if ('fields' in data && data.fields && typeof data.fields === 'object') {
        for (const [name, message] of Object.entries(data.fields)) {
          const shown = text(message)
          if (shown) fields[name] = shown
        }
      }
      return {
        code: 'error' in data ? text(data.error) : undefined,
        message: 'message' in data ? text(data.message) : undefined,
        fields,
      }
    }
  } catch {
    // No JSON body: the status alone describes the error.
  }
  return { fields: {} }
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
