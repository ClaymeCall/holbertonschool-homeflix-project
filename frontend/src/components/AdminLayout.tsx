import { NavLink, Outlet } from 'react-router-dom'
import styles from './AdminLayout.module.css'

function linkClass({ isActive }: { isActive: boolean }) {
  return isActive ? `${styles.link} ${styles.active}` : styles.link
}

/**
 * The administration area (K1; UX-06): service status, accounts, library and downloads in one place.
 * It opens on the status, so a problem is seen first.
 * The route is wrapped in RequireAdmin, and every call it makes is checked by the server too (SEC-02).
 */
export function AdminLayout() {
  return (
    <div className={styles.area}>
      <nav className={styles.nav} aria-label="Administration">
        <NavLink to="/admin/status" className={linkClass}>
          Status
        </NavLink>
        <NavLink to="/admin/downloads" className={linkClass}>
          Downloads
        </NavLink>
      </nav>
      <Outlet />
    </div>
  )
}
