/**
 * Identity service API (first-run setup, session and viewing profiles).
 *
 * Paths follow the OpenAPI contract docs/api/identity.yaml (#134, ADR-0010),
 * behind the gateway prefix `/api/identity` (ADR-0017): first-run setup
 * (`/setup/status`, `/setup/admin`, implemented in #125) and the session
 * (`/auth/sign-in`, `/auth/sign-out`, `/auth/refresh`, `/auth/me`, G3) and the
 * viewing profiles (`/profiles`, G9).
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
  /** The active profile, or null until one is chosen in this session (a new sign-in has none). */
  profileId: string | null
}

/** A viewing profile of the signed-in account (at most 5, names unique within the account). */
export interface Profile {
  id: string
  name: string
  createdAt: string
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

/** The profiles of the signed-in account, oldest first. */
export function listProfiles(signal?: AbortSignal): Promise<Profile[]> {
  return apiRequest<Profile[]>(`${BASE}/profiles`, { signal })
}

/**
 * Creates a profile (201). Fails with 409 `profile_limit_reached` or `profile_name_taken`,
 * or 400 `validation_failed` with the rejected `fields`.
 */
export function createProfile(name: string): Promise<Profile> {
  return apiRequest<Profile>(`${BASE}/profiles`, { method: 'POST', body: { name } })
}

/**
 * Makes the profile active: the server re-issues the session cookie with it, so the library
 * shows that profile's list and progress. Returns the account with its new `profileId`.
 */
export function selectProfile(profileId: string): Promise<Account> {
  return apiRequest<Account>(`${BASE}/profiles/${encodeURIComponent(profileId)}/select`, { method: 'POST' })
}
