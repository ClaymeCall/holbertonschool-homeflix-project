import { Link, Outlet } from 'react-router-dom'
import { AccountMenu } from './AccountMenu'
import { Logo } from './Logo'
import styles from './AppLayout.module.css'

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
        <AccountMenu />
      </header>
      <main id="content" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
