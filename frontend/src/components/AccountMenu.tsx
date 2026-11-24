import { useState } from 'react'
import { Link } from 'react-router-dom'
import { listProfiles } from '../api/identity'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { Button } from './Button'
import styles from './AccountMenu.module.css'

export function AccountMenu() {
  const { session, signOut } = useAuth()
  const [signingOut, setSigningOut] = useState(false)

  if (session.status !== 'signedIn') return null
  const { account } = session
  const { profileId } = account

  async function handleSignOut() {
    setSigningOut(true)
    await signOut().catch(() => undefined)
  }

  return (
    <div className={styles.menu}>
      <span className={styles.account}>
        <span className="visually-hidden">Signed in as </span>
        <span className={styles.email}>{account.email}</span>
        {account.admin && <span className={styles.role}>Admin</span>}
      </span>
      {/* Keyed, so a new active profile is read again and its name shown. */}
      {profileId && <ActiveProfile key={profileId} profileId={profileId} />}
      <Link to="/profiles" className={styles.switch}>
        {profileId ? 'Switch profile' : 'Choose a profile'}
      </Link>
      <Button variant="secondary" onClick={handleSignOut} disabled={signingOut}>
        {signingOut ? 'Signing out…' : 'Sign out'}
      </Button>
    </div>
  )
}

/** The name of the active profile. Nothing is shown until it is known, or if it cannot be read. */
function ActiveProfile({ profileId }: { profileId: string }) {
  const profiles = useAsync(listProfiles)
  const name =
    profiles.state.status === 'success' ? profiles.state.data.find((profile) => profile.id === profileId)?.name : undefined
  if (!name) return null
  return (
    <span className={styles.profile}>
      <span className="visually-hidden">Watching as </span>
      {name}
    </span>
  )
}
