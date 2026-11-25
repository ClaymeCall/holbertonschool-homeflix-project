import { useEffect, useRef } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AccountMenu } from './AccountMenu'
import { Logo } from './Logo'
import styles from './AppLayout.module.css'

function navClass({ isActive }: { isActive: boolean }) {
  return isActive ? `${styles.navLink} ${styles.active}` : styles.navLink
}

export function AppLayout() {
  const { session } = useAuth()
  const admin = session.status === 'signedIn' && session.account.admin
  const { pathname, key } = useLocation()
  const firstLoad = key === 'default'
  const mainRef = useRef<HTMLElement>(null)

  // After a navigation, the link that was followed is often gone and focus would fall back to the top of the
  // document. Moving it to the main content tells keyboard and screen reader users that a new page is shown
  // (UX-04). Not on the first load, where the browser starts at the top, and not when the new page has
  // already moved focus into its content (Search focuses its field).
  useEffect(() => {
    const main = mainRef.current
    if (firstLoad || !main || main.contains(document.activeElement)) return
    main.focus()
  }, [pathname, firstLoad])

  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#content">
        Skip to content
      </a>
      <header className={styles.header}>
        <Link to="/" className={styles.home} aria-label="HomeFlix, home">
          <Logo />
        </Link>
        <nav className={styles.nav} aria-label="Main navigation">
          <NavLink to="/" end className={navClass}>
            Home
          </NavLink>
          <NavLink to="/search" className={navClass}>
            Search
          </NavLink>
          {admin && (
            <NavLink to="/admin" className={navClass}>
              Administration
            </NavLink>
          )}
        </nav>
        <AccountMenu />
      </header>
      <main id="content" ref={mainRef} className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
