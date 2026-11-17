import { useCallback } from 'react'
import { describeError } from '../api/errors'
import { getCategories, getRecentMedia } from '../api/library'
import { useAuth } from '../auth/AuthContext'
import { ButtonLink } from '../components/Button'
import { MediaRow } from '../components/MediaRow'
import { EmptyState, ErrorState, LoadingState } from '../components/StatusMessage'
import { useAsync } from '../hooks/useAsync'
import styles from './HomePage.module.css'

const RECENT_LIMIT = 12

async function loadHome(signal: AbortSignal) {
  const [recent, categories] = await Promise.all([getRecentMedia(RECENT_LIMIT, signal), getCategories(signal)])
  return { recent, categories: categories.filter((category) => category.items.length > 0) }
}

export function HomePage() {
  const { session } = useAuth()
  const load = useCallback((signal: AbortSignal) => loadHome(signal), [])
  const { state, reload } = useAsync(load)
  const isAdmin = session.status === 'signedIn' && session.account.admin

  return (
    <div className={styles.page}>
      <title>Accueil · HomeFlix</title>
      <section className={styles.welcome}>
        <h1 className={styles.title}>Ce soir, on regarde quoi ?</h1>
        <p className={styles.text}>Tous les films de la maison, au même endroit.</p>
        <ButtonLink to="/search" variant="secondary" className={styles.searchLink}>
          <SearchIcon />
          Rechercher un film
        </ButtonLink>
      </section>

      {state.status === 'loading' && <LoadingState label="Chargement du catalogue…" />}

      {state.status === 'error' && <ErrorState message={describeError(state.error)} onRetry={reload} />}

      {state.status === 'success' &&
        (state.data.recent.length === 0 && state.data.categories.length === 0 ? (
          <EmptyState title="Le catalogue est vide">
            <p>
              {isAdmin
                ? "Connectez un dossier de films puis lancez l'indexation pour remplir le catalogue."
                : "Aucun film n'a encore été ajouté. Demandez à l'administrateur de la maison de connecter la bibliothèque."}
            </p>
          </EmptyState>
        ) : (
          <div className={styles.sections}>
            {state.data.recent.length > 0 && <MediaRow title="Ajouts récents" items={state.data.recent} />}
            {state.data.categories.map((category) => (
              <MediaRow key={category.genre} title={category.genre} items={category.items} />
            ))}
          </div>
        ))}
    </div>
  )
}

function SearchIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" strokeWidth="2.2" />
      <path d="m15.5 15.5 5 5" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  )
}
