import { useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { describeError, isNotFound } from '../api/errors'
import { getMedia, type MediaDetails } from '../api/library'
import { AvailabilityBadge } from '../components/AvailabilityBadge'
import { Button, ButtonLink } from '../components/Button'
import { Poster } from '../components/Poster'
import { ErrorState, LoadingState } from '../components/StatusMessage'
import { formatDuration } from '../format'
import { useAsync } from '../hooks/useAsync'
import styles from './MovieDetailsPage.module.css'

const unavailableReason: Record<Exclude<MediaDetails['availability'], 'AVAILABLE'>, string> = {
  UNAVAILABLE: 'This film cannot be played at the moment: its file cannot be found in the library.',
  DOWNLOADING: 'This film is downloading. It can be played once the download has finished.',
}

export function MovieDetailsPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => getMedia(id, signal), [id])
  const { state, reload } = useAsync(load)

  if (state.status === 'loading') {
    return <LoadingState label="Loading the film…" />
  }

  if (state.status === 'error') {
    if (isNotFound(state.error)) {
      return (
        <ErrorState title="Film not found" message="This film does not exist or has been removed from the catalogue.">
          <ButtonLink to="/" variant="secondary">
            Back to home
          </ButtonLink>
        </ErrorState>
      )
    }
    return <ErrorState message={describeError(state.error)} onRetry={reload} />
  }

  return <MovieDetails media={state.data} />
}

function MovieDetails({ media }: { media: MediaDetails }) {
  const duration = formatDuration(media.durationSeconds)

  return (
    <article className={styles.hero}>
      <title>{`${media.title} · HomeFlix`}</title>
      <div className={styles.layout}>
        <Poster src={media.posterUrl} title={media.title} className={styles.poster} />

        <div className={styles.info}>
          <h1 className={styles.title}>{media.title}</h1>

          <dl className={styles.meta}>
            <div>
              <dt className="visually-hidden">Genres</dt>
              <dd className={media.genres.length > 0 ? undefined : styles.missing}>
                {media.genres.length > 0 ? media.genres.join(', ') : 'Genre not specified'}
              </dd>
            </div>
            <div>
              <dt className="visually-hidden">Duration</dt>
              <dd className={duration ? undefined : styles.missing}>{duration ?? 'Unknown duration'}</dd>
            </div>
            <div>
              <dt className="visually-hidden">Availability</dt>
              <dd>
                <AvailabilityBadge availability={media.availability} />
              </dd>
            </div>
          </dl>

          <p className={media.description ? styles.description : `${styles.description} ${styles.missing}`}>
            {media.description ?? 'No description available for this film.'}
          </p>

          <div className={styles.actions}>
            {media.availability === 'AVAILABLE' ? (
              <ButtonLink to={`/watch/${encodeURIComponent(media.id)}`}>
                <PlayIcon />
                Play
              </ButtonLink>
            ) : (
              <>
                <Button disabled aria-describedby="play-unavailable">
                  <PlayIcon />
                  Play
                </Button>
                <p id="play-unavailable" className={styles.unavailable}>
                  {unavailableReason[media.availability]}
                </p>
              </>
            )}
          </div>
        </div>
      </div>
    </article>
  )
}

function PlayIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M4 2.5v11l9-5.5z" fill="currentColor" />
    </svg>
  )
}
