import { createContext, useContext } from 'react'
import type { Account, Credentials } from '../api/identity'

/** Why the user is signed out, to show the right message on the sign-in screen. */
export type SignedOutReason = 'expired' | 'signedOut' | 'setupDone'

export type Session =
  | { status: 'checking' }
  | { status: 'unreachable'; error: unknown }
  | { status: 'setupRequired' }
  | { status: 'signedOut'; reason?: SignedOutReason }
  | { status: 'signedIn'; account: Account }

export interface AuthContextValue {
  session: Session
  signIn: (credentials: Credentials) => Promise<void>
  signOut: () => Promise<void>
  completeSetup: (credentials: Credentials) => Promise<void>
  /** Makes this profile the active one for the rest of the session. */
  selectProfile: (profileId: string) => Promise<void>
  /** Signs the user out with the expired-session message, for failures the API client cannot see. */
  expire: () => void
  retry: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}
