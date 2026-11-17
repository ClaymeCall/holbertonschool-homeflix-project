import { Link, Outlet } from 'react-router-dom'
import { Logo } from './Logo'
import styles from './AppLayout.module.css'

export function AppLayout() {
  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#contenu">
        Aller au contenu
      </a>
      <header className={styles.header}>
        <Link to="/" className={styles.home} aria-label="HomeFlix, accueil">
          <Logo />
        </Link>
      </header>
      <main id="contenu" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
