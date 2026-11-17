import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Button } from './Button'
import styles from './AccountMenu.module.css'

export function AccountMenu() {
  const { session, signOut } = useAuth()
  const [signingOut, setSigningOut] = useState(false)

  if (session.status !== 'signedIn') return null
  const { account } = session

  async function handleSignOut() {
    setSigningOut(true)
    await signOut().catch(() => undefined)
  }

  return (
    <div className={styles.menu}>
      <span className={styles.account}>
        <span className="visually-hidden">Connecté en tant que </span>
        {account.username}
        {account.role === 'ADMIN' && <span className={styles.role}>Admin</span>}
      </span>
      <Button variant="secondary" onClick={handleSignOut} disabled={signingOut}>
        {signingOut ? 'Déconnexion…' : 'Se déconnecter'}
      </Button>
    </div>
  )
}
