import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { describeError } from '../api/errors'
import { ButtonLink } from '../components/Button'
import { ErrorState, LoadingState } from '../components/StatusMessage'
import { useAuth } from './AuthContext'
import { redirectTarget } from './redirect'

/** Shown while the session is checked, or when HomeFlix cannot be reached at all. */
function SessionPending() {
  const { session, retry } = useAuth()
  if (session.status === 'unreachable') {
    return <ErrorState message={describeError(session.error)} onRetry={retry} />
  }
  return <LoadingState label="Connecting to HomeFlix…" />
}

/** Protected screens: only for a signed-in account. The server enforces it too (SEC-02). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()

  switch (session.status) {
    case 'checking':
    case 'unreachable':
      return <SessionPending />
    case 'setupRequired':
      return <Navigate to="/setup" replace />
    case 'signedOut':
      return <Navigate to="/sign-in" replace state={{ from: location }} />
    case 'signedIn':
      return children
  }
}

/**
 * Administrator screens, inside RequireAuth. Anyone else sees that the page is for administrators, and the
 * page never calls its API. The server refuses those calls anyway (SEC-02, ACQ-05).
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  if (session.status === 'signedIn' && session.account.admin) {
    return children
  }
  return (
    <>
      <title>Administrators only · HomeFlix</title>
      <ErrorState title="Administrators only" message="Only an administrator of this HomeFlix can open this page.">
        <ButtonLink to="/" variant="secondary">
          Back to home
        </ButtonLink>
      </ErrorState>
    </>
  )
}

/**
 * Viewing screens: only once a profile is chosen, since the list and the progress shown are the
 * profile's (PRF-02). Placed inside RequireAuth. The library refuses them without a profile too.
 */
export function RequireProfile({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  if (session.status === 'signedIn' && !session.account.profileId) {
    return <Navigate to="/profiles" replace state={{ from: location }} />
  }
  return children
}

/** The first-run setup screen is only reachable while no administrator exists. */
export function RequireSetup({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  switch (session.status) {
    case 'checking':
    case 'unreachable':
      return <SessionPending />
    case 'setupRequired':
      return children
    case 'signedIn':
      return <Navigate to="/" replace />
    case 'signedOut':
      return <Navigate to="/sign-in" replace />
  }
}

/** The sign-in screen is for signed-out users only. */
export function RequireSignedOut({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  switch (session.status) {
    case 'checking':
    case 'unreachable':
      return <SessionPending />
    case 'setupRequired':
      return <Navigate to="/setup" replace />
    case 'signedIn':
      return <Navigate to={redirectTarget(location.state)} replace />
    case 'signedOut':
      return children
  }
}
