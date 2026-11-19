import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, setSessionHandlers } from '../api/client'
import * as identity from '../api/identity'
import { AuthContext, type AuthContextValue, type Session } from './AuthContext'

/**
 * The token lasts 15 minutes. Renewing it at about two thirds of that keeps
 * requests the app cannot retry itself, such as video streaming, working
 * (identity and permissions design, "Expiry and renewal").
 */
const RENEW_EVERY_MS = 10 * 60 * 1000

/**
 * Browsers slow down timers in a hidden tab, so the token may have run low
 * meanwhile: the session is also renewed when the tab becomes visible again,
 * unless it was renewed less than this long ago.
 */
const MIN_RENEW_GAP_MS = 60 * 1000

async function loadSession(signal: AbortSignal): Promise<Session> {
  if (await identity.isSetupRequired(signal)) {
    return { status: 'setupRequired' }
  }
  try {
    return { status: 'signedIn', account: await identity.getCurrentAccount(signal) }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { status: 'signedOut' }
    }
    throw error
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({ status: 'checking' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    loadSession(controller.signal).then(
      (loaded) => {
        if (!controller.signal.aborted) setSession(loaded)
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setSession({ status: 'unreachable', error })
      },
    )
    return () => controller.abort()
  }, [attempt])

  const expire = useCallback(() => {
    setSession((current) => (current.status === 'signedIn' ? { status: 'signedOut', reason: 'expired' } : current))
  }, [])

  useEffect(() => setSessionHandlers({ renew: identity.renewSession, onExpired: expire }), [expire])

  const signedIn = session.status === 'signedIn'
  useEffect(() => {
    if (!signedIn) return
    let lastRenewal = Date.now()
    const renew = () => {
      lastRenewal = Date.now()
      identity.renewSession().catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) expire()
      })
    }
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible' && Date.now() - lastRenewal >= MIN_RENEW_GAP_MS) renew()
    }
    const timer = window.setInterval(renew, RENEW_EVERY_MS)
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [signedIn, expire])

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      signIn: async (credentials) => {
        const account = await identity.signIn(credentials)
        setSession({ status: 'signedIn', account })
      },
      signOut: async () => {
        try {
          await identity.signOut()
        } finally {
          // Even if the call fails, the interface stops showing the account.
          setSession({ status: 'signedOut', reason: 'signedOut' })
        }
      },
      completeSetup: async (credentials) => {
        await identity.createFirstAdministrator(credentials)
        setSession({ status: 'signedOut', reason: 'setupDone' })
      },
      expire,
      retry: () => {
        setSession({ status: 'checking' })
        setAttempt((value) => value + 1)
      },
    }),
    [session, expire],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
