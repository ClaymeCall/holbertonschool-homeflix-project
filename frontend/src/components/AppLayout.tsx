import { useEffect, useRef } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigationType } from 'react-router-dom'
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
  const backOrForward = useNavigationType() === 'POP'
  const mainRef = useRef<HTMLElement>(null)

  // BrowserRouter keeps the scroll position of the previous page, so a film opened from far down the home page
  // would open scrolled down. A followed link starts the new page at the top. Back and forward are left to the
  // browser, which brings the reader back to where they were.
  useEffect(() => {
    if (firstLoad || backOrForward) return
    window.scrollTo(0, 0)
  }, [pathname, firstLoad, backOrForward])

  // After a navigation, the link that was followed is often gone and focus would fall back to the top of the
  // document. Moving it to the main content tells keyboard and screen reader users that a new page is shown
  // (UX-04). Not on the first load, where the browser starts at the top, and not when the new page has
  // already moved focus into its content (Search focuses its field). focus() would scroll by default, which
  // would undo the position the browser restores on back and forward: the scroll is left to the effect above.
  useEffect(() => {
    const main = mainRef.current
    if (firstLoad || !main || main.contains(document.activeElement)) return
    main.focus({ preventScroll: true })
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
