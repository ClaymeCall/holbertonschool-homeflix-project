import { useId } from 'react'
import type { MediaSummary } from '../api/library'
import { MediaCard } from './MediaCard'
import styles from './MediaRow.module.css'

/** A titled, horizontally scrolling section of movies. */
export function MediaRow({ title, items }: { title: string; items: MediaSummary[] }) {
  const headingId = useId()
  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <h2 id={headingId} className={styles.title}>
        {title}
      </h2>
      <ul className={styles.row}>
        {items.map((media) => (
          <li key={media.id} className={styles.item}>
            <MediaCard media={media} />
          </li>
        ))}
      </ul>
    </section>
  )
}
