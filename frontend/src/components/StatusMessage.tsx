import type { ReactNode } from 'react'
import { Button } from './Button'
import styles from './StatusMessage.module.css'

/** Loading, error and empty states shared by every screen (UX-05). */

export function LoadingState({ label = 'Chargement…' }: { label?: string }) {
  return (
    <div className={styles.box} role="status">
      <span className={styles.spinner} aria-hidden="true" />
      <p>{label}</p>
    </div>
  )
}

export function ErrorState({
  title = 'Un problème est survenu',
  message,
  onRetry,
  children,
}: {
  title?: string
  message: string
  onRetry?: () => void
  children?: ReactNode
}) {
  return (
    <div className={styles.box} role="alert">
      <h2 className={styles.title}>{title}</h2>
      <p className={styles.text}>{message}</p>
      {(onRetry || children) && (
        <div className={styles.actions}>
          {onRetry && <Button onClick={onRetry}>Réessayer</Button>}
          {children}
        </div>
      )}
    </div>
  )
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className={styles.box}>
      <h2 className={styles.title}>{title}</h2>
      {children && <div className={styles.text}>{children}</div>}
    </div>
  )
}
