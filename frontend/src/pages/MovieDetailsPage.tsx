import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { describeError, isNotFound } from '../api/errors'
import {
  addFavourite,
  getMedia,
  getProgress,
  listFavourites,
  removeFavourite,
  resetProgress,
  type MediaDetails,
} from '../api/library'
import { AvailabilityBadge } from '../components/AvailabilityBadge'
import { Button, ButtonLink } from '../components/Button'
import formStyles from '../components/Form.module.css'
import { Poster } from '../components/Poster'
import { ErrorState, LoadingState } from '../components/StatusMessage'
import { formatClock, formatDuration } from '../format'
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
  const watchUrl = `/watch/${encodeURIComponent(media.id)}`
  const loadProgress = useCallback((signal: AbortSignal) => getProgress(media.id, signal), [media.id])
  const progress = useAsync(loadProgress)
  const [reset, setReset] = useState(false)
  const [notice, setNotice] = useState<{ kind: 'success' | 'error'; text: string } | null>(null)
  const noticeRef = useRef<HTMLParagraphElement>(null)

  // Progress that cannot be read only hides Resume and Reset: the film can still be played.
  const saved = !reset && progress.state.status === 'success' ? progress.state.data : null
  const started = saved?.state === 'STARTED'

  // Resetting removes its own button: focus goes to the outcome.
  useEffect(() => {
    if (notice) noticeRef.current?.focus()
  }, [notice])

  async function handleReset() {
    try {
      await resetProgress(media.id)
      setReset(true)
      setNotice({ kind: 'success', text: 'The progress was reset. The film now plays from the beginning.' })
    } catch (error) {
      setNotice({ kind: 'error', text: describeError(error) })
    }
  }

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

          {notice && (
            <p
              ref={noticeRef}
              tabIndex={-1}
              className={notice.kind === 'error' ? formStyles.alert : formStyles.notice}
              role={notice.kind === 'error' ? 'alert' : 'status'}
            >
              {notice.text}
            </p>
          )}

          <div className={styles.actions}>
            {media.availability === 'AVAILABLE' && started ? (
              <>
                <ButtonLink to={watchUrl}>
                  <PlayIcon />
                  Resume from {formatClock(saved.positionSeconds)}
                </ButtonLink>
                <ButtonLink to={`${watchUrl}?from=start`} variant="secondary">
                  Play from the beginning
                </ButtonLink>
              </>
            ) : media.availability === 'AVAILABLE' ? (
              <ButtonLink to={watchUrl}>
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
            <MyListButton mediaId={media.id} onError={(text) => setNotice({ kind: 'error', text })} />
            {saved?.state && (
              <Button variant="secondary" onClick={handleReset}>
                Reset progress
              </Button>
            )}
          </div>
        </div>
      </div>
    </article>
  )
}

/**
 * Adds the title to the active profile's "My List" or removes it (CAT-07). A toggle button: its name stays
 * "My List" and aria-pressed says whether the title is in it.
 */
function MyListButton({ mediaId, onError }: { mediaId: string; onError: (message: string) => void }) {
  const favourites = useAsync(listFavourites)
  const [inList, setInList] = useState<boolean | null>(null)
  const [busy, setBusy] = useState(false)

  if (favourites.state.status !== 'success') return null
  const listed = inList ?? favourites.state.data.some((media) => media.id === mediaId)

  async function toggle() {
    if (busy) return
    setBusy(true)
    try {
      await (listed ? removeFavourite(mediaId) : addFavourite(mediaId))
      setInList(!listed)
    } catch (error) {
      onError(describeError(error))
    } finally {
      setBusy(false)
    }
  }

  // aria-disabled rather than disabled while the request runs: a disabled button loses focus, so a keyboard
  // user would be sent back to the top of the page (UX-04).
  return (
    <Button variant="secondary" aria-pressed={listed} onClick={toggle} aria-disabled={busy}>
      <span aria-hidden="true">{listed ? '✓' : '+'}</span>
      My List
    </Button>
  )
}

function PlayIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M4 2.5v11l9-5.5z" fill="currentColor" />
    </svg>
  )
}
