import { useId, type ReactNode } from 'react'
import type { MediaSummary } from '../api/library'
import { MediaCard } from './MediaCard'
import styles from './MediaRow.module.css'

/** A titled, horizontally scrolling section of movies, or `empty` when it has none. */
export function MediaRow({ title, items, empty }: { title: string; items: MediaSummary[]; empty?: ReactNode }) {
  const headingId = useId()
  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <h2 id={headingId} className={styles.title}>
        {title}
      </h2>
      {items.length === 0 && empty ? (
        <p className={styles.empty}>{empty}</p>
      ) : (
        <ul className={styles.row}>
          {items.map((media) => (
            <li key={media.id} className={styles.item}>
              <MediaCard media={media} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
