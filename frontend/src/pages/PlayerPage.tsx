import { useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { describeError, isNotFound } from '../api/errors'
import { getMedia } from '../api/library'
import { ButtonLink } from '../components/Button'
import { ErrorState, LoadingState } from '../components/StatusMessage'
import { VideoPlayer } from '../components/VideoPlayer'
import { useAsync } from '../hooks/useAsync'
import styles from './PlayerPage.module.css'

const notPlayable = {
  UNAVAILABLE: 'This film cannot be played at the moment: its file cannot be found in the library.',
  DOWNLOADING: 'This film is downloading. It can be played once the download has finished.',
}

export function PlayerPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => getMedia(id, signal), [id])
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

  const media = state.data
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
      <VideoPlayer mediaId={media.id} title={media.title} backTo={backTo} />
    </main>
  )
}
