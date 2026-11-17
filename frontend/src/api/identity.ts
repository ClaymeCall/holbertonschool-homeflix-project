/**
 * Identity service API (first-run setup and session).
 *
 * First-run setup matches the identity service of #125 (G2): `GET /setup/status`
 * and `POST /setup/admin`, behind the gateway prefix `/api/identity` (ADR-0017).
 *
 * PROVISIONAL: the session endpoints (`/auth/...`, G3) and the OpenAPI contract
 * (docs/api/, ADR-0010) do not exist yet. Their paths are assumptions kept in
 * this single file. The account shape follows #125 (`email`, `admin`).
 *
 * The session token lives in an HttpOnly cookie set by the server (ADR-0009):
 * these calls never see or store it.
 */
import { apiRequest } from './client'

const BASE = '/api/identity'

export interface Account {
  id: string
  email: string
  admin: boolean
}

export interface Credentials {
  email: string
  password: string
}

/** True while no administrator exists, so the first-run setup is offered. */
export async function isSetupRequired(signal?: AbortSignal): Promise<boolean> {
  const status = await apiRequest<{ setupRequired: boolean }>(`${BASE}/setup/status`, {
    signal,
    renewSession: false,
  })
  return status.setupRequired
}

/**
 * Creates the first administrator (201). Fails with 409 `setup_already_completed`
 * once one exists, or 400 `validation_failed` with the rejected `fields`.
 */
export function createFirstAdministrator(credentials: Credentials): Promise<Account> {
  return apiRequest<Account>(`${BASE}/setup/admin`, { method: 'POST', body: credentials, renewSession: false })
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
