import { Link } from 'react-router-dom'
import type { MediaSummary } from '../api/library'
import { formatDuration } from '../format'
import { AvailabilityBadge } from './AvailabilityBadge'
import { Poster } from './Poster'
import styles from './MediaCard.module.css'

export function MediaCard({ media }: { media: MediaSummary }) {
  const duration = formatDuration(media.durationSeconds)
  const details = [media.genre, duration].filter(Boolean).join(' · ')

  return (
    <Link to={`/movies/${encodeURIComponent(media.id)}`} className={styles.card}>
      <span className={styles.posterFrame}>
        <Poster src={media.posterUrl} title={media.title} decorative className={styles.poster} />
        {media.availability !== 'AVAILABLE' && (
          <span className={styles.badge}>
            <AvailabilityBadge availability={media.availability} />
          </span>
        )}
      </span>
      <span className={styles.title}>{media.title}</span>
      {details && <span className={styles.details}>{details}</span>}
    </Link>
  )
}
