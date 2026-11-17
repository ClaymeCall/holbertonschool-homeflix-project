/**
 * Identity service API (first-run setup and session).
 *
 * PROVISIONAL: the OpenAPI contract (docs/api/, ADR-0010), the identity design
 * (issue #14) and the endpoints (issues G2 and G3) do not exist yet. Paths and
 * shapes below are assumptions kept in this single file so they can be aligned
 * with the contract later.
 *
 * The session token lives in an HttpOnly cookie set by the server (ADR-0009):
 * these calls never see or store it.
 */
import { apiRequest } from './client'

const BASE = '/api/identity'

export type Role = 'ADMIN' | 'USER'

export interface Account {
  id: string
  username: string
  role: Role
}

export interface Credentials {
  username: string
  password: string
}

/** True while no administrator exists, so the first-run setup is offered. */
export async function isSetupRequired(signal?: AbortSignal): Promise<boolean> {
  const status = await apiRequest<{ required: boolean }>(`${BASE}/setup`, { signal, renewSession: false })
  return status.required
}

export function createFirstAdministrator(credentials: Credentials): Promise<void> {
  return apiRequest<void>(`${BASE}/setup`, { method: 'POST', body: credentials, renewSession: false })
}

export function signIn(credentials: Credentials): Promise<Account> {
  return apiRequest<Account>(`${BASE}/auth/login`, { method: 'POST', body: credentials, renewSession: false })
}

export function signOut(): Promise<void> {
  return apiRequest<void>(`${BASE}/auth/logout`, { method: 'POST', renewSession: false })
}

/** The signed-in account, or an ApiError with status 401 when there is no valid session. */
export function getCurrentAccount(signal?: AbortSignal): Promise<Account> {
  return apiRequest<Account>(`${BASE}/auth/me`, { signal, renewSession: false })
}

/** Asks the identity service for a fresh token. Fails for a disabled or deleted account (G3). */
export function renewSession(): Promise<void> {
  return apiRequest<void>(`${BASE}/auth/refresh`, { method: 'POST', renewSession: false })
}
