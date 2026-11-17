import { useState } from 'react'
import { LogoMark } from './Logo'
import styles from './Poster.module.css'

/**
 * Movie poster. The browser loads the external URL directly with no referrer
 * (ADR-0018); a placeholder is shown when there is no poster or it fails to load.
 */
export function Poster({ src, title, className }: { src: string | null; title: string; className?: string }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const classes = [styles.poster, className].filter(Boolean).join(' ')

  if (!src || failedSrc === src) {
    return (
      <div className={`${classes} ${styles.placeholder}`} role="img" aria-label={`Poster unavailable for ${title}`}>
        <LogoMark className={styles.mark} />
        <span className={styles.placeholderTitle} aria-hidden="true">
          {title}
        </span>
      </div>
    )
  }

  return (
    <img
      className={classes}
      src={src}
      alt={`Poster for ${title}`}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setFailedSrc(src)}
    />
  )
}
