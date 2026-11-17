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
  UNAVAILABLE: "Ce film ne peut pas être lu pour le moment : son fichier est introuvable dans la bibliothèque.",
  DOWNLOADING: 'Ce film est en cours de téléchargement. Il pourra être lu une fois le téléchargement terminé.',
}

export function PlayerPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => getMedia(id, signal), [id])
  const { state, reload } = useAsync(load)
  const backTo = `/movies/${encodeURIComponent(id)}`

  if (state.status === 'loading') {
    return (
      <div className={styles.page}>
        <LoadingState label="Préparation de la lecture…" />
      </div>
    )
  }

  if (state.status === 'error') {
    return (
      <div className={styles.page}>
        {isNotFound(state.error) ? (
          <ErrorState title="Film introuvable" message="Ce film n'existe pas ou a été retiré du catalogue.">
            <ButtonLink to="/" variant="secondary">
              Retour à l'accueil
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
        <ErrorState title="Lecture impossible" message={notPlayable[media.availability]}>
          <ButtonLink to={backTo} variant="secondary">
            Retour au film
          </ButtonLink>
        </ErrorState>
      </div>
    )
  }

  return (
    <main className={styles.page}>
      <title>{`Lecture : ${media.title} · HomeFlix`}</title>
      <VideoPlayer mediaId={media.id} title={media.title} backTo={backTo} />
    </main>
  )
}
