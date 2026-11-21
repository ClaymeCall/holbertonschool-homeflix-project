/**
 * Identity service API (first-run setup and session).
 *
 * Paths follow the OpenAPI contract docs/api/identity.yaml (#134, ADR-0010),
 * behind the gateway prefix `/api/identity` (ADR-0017): first-run setup
 * (`/setup/status`, `/setup/admin`, implemented in #125) and the session
 * (`/auth/sign-in`, `/auth/sign-out`, `/auth/refresh`, `/auth/me`, G3), and the administrator's
 * account management (`/accounts`, G8).
 *
 * The session token lives in an HttpOnly cookie set by the server (ADR-0009):
 * these calls never see or store it.
 */
import { apiRequest, fetchAllPages } from './client'

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
  return apiRequest<Account>(`${BASE}/auth/sign-in`, { method: 'POST', body: credentials, renewSession: false })
}

export function signOut(): Promise<void> {
  return apiRequest<void>(`${BASE}/auth/sign-out`, { method: 'POST', renewSession: false })
}

/** The signed-in account, or an ApiError with status 401 when there is no valid session. */
export function getCurrentAccount(signal?: AbortSignal): Promise<Account> {
  return apiRequest<Account>(`${BASE}/auth/me`, { signal, renewSession: false })
}

/** Asks the identity service for a fresh token. Fails for a disabled or deleted account (G3). */
export function renewSession(): Promise<void> {
  return apiRequest<void>(`${BASE}/auth/refresh`, { method: 'POST', renewSession: false })
}

/** An account as an administrator sees it (G8). Never holds the password. */
export interface ManagedAccount extends Account {
  disabled: boolean
  createdAt: string
}

export interface NewAccount extends Credentials {
  admin: boolean
}

/** The contract's largest page. */
const ACCOUNTS_PAGE = 100

/** Every account, oldest first. */
export function listAccounts(signal?: AbortSignal): Promise<ManagedAccount[]> {
  return fetchAllPages(ACCOUNTS_PAGE, (offset) =>
    apiRequest<ManagedAccount[]>(`${BASE}/accounts?limit=${ACCOUNTS_PAGE}&offset=${offset}`, { signal }),
  )
}

/** Fails with 400 `validation_failed`, with `fields.email` when the address is already used. */
export function createAccount(account: NewAccount): Promise<ManagedAccount> {
  return apiRequest<ManagedAccount>(`${BASE}/accounts`, { method: 'POST', body: account })
}

/** Fails with 409 `last_administrator` when no enabled administrator would be left. */
export function changeAccountRole(id: string, admin: boolean): Promise<ManagedAccount> {
  return apiRequest<ManagedAccount>(`${BASE}/accounts/${encodeURIComponent(id)}/role`, {
    method: 'PUT',
    body: { admin },
  })
}

/** Fails with 409 `last_administrator` when no enabled administrator would be left. */
export function disableAccount(id: string): Promise<ManagedAccount> {
  return apiRequest<ManagedAccount>(`${BASE}/accounts/${encodeURIComponent(id)}/disable`, { method: 'POST' })
}

export function enableAccount(id: string): Promise<ManagedAccount> {
  return apiRequest<ManagedAccount>(`${BASE}/accounts/${encodeURIComponent(id)}/enable`, { method: 'POST' })
}
