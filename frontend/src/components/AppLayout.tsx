import { Link, NavLink, Outlet } from 'react-router-dom'
import { AccountMenu } from './AccountMenu'
import { Logo } from './Logo'
import styles from './AppLayout.module.css'

function navClass({ isActive }: { isActive: boolean }) {
  return isActive ? `${styles.navLink} ${styles.active}` : styles.navLink
}

export function AppLayout() {
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
        </nav>
        <AccountMenu />
      </header>
      <main id="content" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
