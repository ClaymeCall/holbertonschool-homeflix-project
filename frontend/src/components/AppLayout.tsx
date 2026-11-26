import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
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
  // Small screens: the navigation and the account menu sit behind a burger button. From 60rem they are always shown.
  const [menuOpen, setMenuOpen] = useState(false)
  // The header's height (it grows with the open menu) is published as --header-height, so that what sticks under it
  // (the administration tabs) always sits right below it.
  const shellRef = useRef<HTMLDivElement>(null)
  const headerRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const shell = shellRef.current
    const header = headerRef.current
    if (!shell || !header || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => shell.style.setProperty('--header-height', `${header.offsetHeight}px`))
    observer.observe(header)
    return () => observer.disconnect()
  }, [])
  return (
    <div ref={shellRef} className={styles.shell}>
      <a className={styles.skipLink} href="#content">
        Skip to content
      </a>
      <header
        ref={headerRef}
        className={styles.header}
        onKeyDown={(event) => {
          if (event.key === 'Escape' && menuOpen) {
            setMenuOpen(false)
            document.getElementById('menu-toggle')?.focus()
          }
        }}
      >
        <Link to="/" className={styles.home} aria-label="HomeFlix, home">
          <Logo />
        </Link>
        <button
          id="menu-toggle"
          type="button"
          className={styles.burger}
          aria-expanded={menuOpen}
          aria-controls="site-menu"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" focusable="false">
            {menuOpen ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
          </svg>
          <span className="visually-hidden">{menuOpen ? 'Close the menu' : 'Open the menu'}</span>
        </button>
        {/* Following a link closes the menu. */}
        <div
          id="site-menu"
          className={menuOpen ? `${styles.menu} ${styles.menuOpen}` : styles.menu}
          onClick={(event) => {
            if ((event.target as HTMLElement).closest('a')) setMenuOpen(false)
          }}
        >
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
        </div>
      </header>
      <main id="content" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
