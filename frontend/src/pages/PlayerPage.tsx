import { useCallback } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { describeError, isNotFound } from '../api/errors'
import { getMedia, getProgress } from '../api/library'
import { ButtonLink } from '../components/Button'
import { ErrorState, LoadingState } from '../components/StatusMessage'
import { VideoPlayer } from '../components/VideoPlayer'
import { useAsync } from '../hooks/useAsync'
import styles from './PlayerPage.module.css'

const notPlayable = {
  UNAVAILABLE: 'This film cannot be played at the moment: its file cannot be found in the library.',
  DOWNLOADING: 'This film is downloading. It can be played once the download has finished.',
}

/**
 * The title and where to start: the saved position of a started title, unless the viewer chose the
 * beginning (`?from=start`). Progress that cannot be read never stops playback: it starts at the beginning.
 */
async function loadPlayback(id: string, fromStart: boolean, signal: AbortSignal) {
  const [media, progress] = await Promise.all([
    getMedia(id, signal),
    fromStart ? null : getProgress(id, signal).catch(() => null),
  ])
  return { media, startAt: progress?.state === 'STARTED' ? progress.positionSeconds : 0 }
}

export function PlayerPage() {
  const { id = '' } = useParams()
  const [search] = useSearchParams()
  const fromStart = search.get('from') === 'start'
  const load = useCallback((signal: AbortSignal) => loadPlayback(id, fromStart, signal), [id, fromStart])
  const { state, reload } = useAsync(load)
  const backTo = `/movies/${encodeURIComponent(id)}`

  if (state.status === 'loading') {
    return (
      <div className={styles.page}>
        <LoadingState label="Preparing playback…" />
      </div>
    )
  }

  if (state.status === 'error') {
    return (
      <div className={styles.page}>
        {isNotFound(state.error) ? (
          <ErrorState title="Film not found" message="This film does not exist or has been removed from the catalogue.">
            <ButtonLink to="/" variant="secondary">
              Back to home
            </ButtonLink>
          </ErrorState>
        ) : (
          <ErrorState message={describeError(state.error)} onRetry={reload} />
        )}
      </div>
    )
  }

  const { media, startAt } = state.data
  if (media.availability !== 'AVAILABLE') {
    return (
      <div className={styles.page}>
        <title>{`${media.title} · HomeFlix`}</title>
        <ErrorState title="Cannot play" message={notPlayable[media.availability]}>
          <ButtonLink to={backTo} variant="secondary">
            Back to the film
          </ButtonLink>
        </ErrorState>
      </div>
    )
  }

  return (
    <main className={styles.page}>
      <title>{`Playing: ${media.title} · HomeFlix`}</title>
      <VideoPlayer mediaId={media.id} title={media.title} backTo={backTo} startAt={startAt} />
    </main>
  )
}
