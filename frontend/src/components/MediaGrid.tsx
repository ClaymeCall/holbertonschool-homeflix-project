import type { MediaSummary } from '../api/library'
import { MediaCard } from './MediaCard'
import styles from './MediaGrid.module.css'

export function MediaGrid({ items, label }: { items: MediaSummary[]; label: string }) {
  return (
    <ul className={styles.grid} aria-label={label}>
      {items.map((media) => (
        <li key={media.id} className={styles.item}>
          <MediaCard media={media} />
        </li>
      ))}
    </ul>
  )
}
