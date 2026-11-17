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
  UNAVAILABLE: "Ce film ne peut pas être lu pour le moment : son fichier est introuvable dans la bibliothèque.",
  DOWNLOADING: 'Ce film est en cours de téléchargement. Il pourra être lu une fois le téléchargement terminé.',
}

export function MovieDetailsPage() {
  const { id = '' } = useParams()
  const load = useCallback((signal: AbortSignal) => getMedia(id, signal), [id])
  const { state, reload } = useAsync(load)

  if (state.status === 'loading') {
    return <LoadingState label="Chargement du film…" />
  }

  if (state.status === 'error') {
    if (isNotFound(state.error)) {
      return (
        <ErrorState title="Film introuvable" message="Ce film n'existe pas ou a été retiré du catalogue.">
          <ButtonLink to="/" variant="secondary">
            Retour à l'accueil
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
              <dt className="visually-hidden">Genre</dt>
              <dd className={media.genre ? undefined : styles.missing}>{media.genre ?? 'Genre non renseigné'}</dd>
            </div>
            <div>
              <dt className="visually-hidden">Durée</dt>
              <dd className={duration ? undefined : styles.missing}>{duration ?? 'Durée inconnue'}</dd>
            </div>
            <div>
              <dt className="visually-hidden">Disponibilité</dt>
              <dd>
                <AvailabilityBadge availability={media.availability} />
              </dd>
            </div>
          </dl>

          <p className={media.description ? styles.description : `${styles.description} ${styles.missing}`}>
            {media.description ?? 'Aucune description disponible pour ce film.'}
          </p>

          <div className={styles.actions}>
            {media.availability === 'AVAILABLE' ? (
              <ButtonLink to={`/watch/${encodeURIComponent(media.id)}`}>
                <PlayIcon />
                Lire
              </ButtonLink>
            ) : (
              <>
                <Button disabled aria-describedby="play-unavailable">
                  <PlayIcon />
                  Lire
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
