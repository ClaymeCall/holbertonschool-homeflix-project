import type { ReactNode } from 'react'
import { Logo } from './Logo'
import styles from './AuthLayout.module.css'

/** Centered card used by the setup and sign-in screens. */
export function AuthLayout({ title, intro, children }: { title: string; intro?: ReactNode; children: ReactNode }) {
  return (
    <div className={styles.page}>
      <title>{`${title} · HomeFlix`}</title>
      <main className={styles.card}>
        <Logo />
        <h1 className={styles.title}>{title}</h1>
        {intro && <div className={styles.intro}>{intro}</div>}
        {children}
      </main>
    </div>
  )
}
