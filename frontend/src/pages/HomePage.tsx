import { useCallback } from 'react'
import { describeError } from '../api/errors'
import { getCategories, getRecentMedia, listContinueWatching, listFavourites } from '../api/library'
import { useAuth } from '../auth/AuthContext'
import { ButtonLink } from '../components/Button'
import { MediaRow } from '../components/MediaRow'
import { EmptyState, ErrorState, LoadingState } from '../components/StatusMessage'
import { useAsync } from '../hooks/useAsync'
import styles from './HomePage.module.css'

const RECENT_LIMIT = 12
const CONTINUE_LIMIT = 12

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
      <title>Home · HomeFlix</title>
      <section className={styles.welcome}>
        <h1 className={styles.title}>What shall we watch tonight?</h1>
        <p className={styles.text}>All your household's films, in one place.</p>
        <ButtonLink to="/search" variant="secondary" className={styles.searchLink}>
          <SearchIcon />
          Search for a film
        </ButtonLink>
      </section>

      {state.status === 'loading' && <LoadingState label="Loading the catalogue…" />}

      {state.status === 'error' && <ErrorState message={describeError(state.error)} onRetry={reload} />}

      {state.status === 'success' &&
        (state.data.recent.length === 0 && state.data.categories.length === 0 ? (
          <EmptyState title="The catalogue is empty">
            <p>
              {isAdmin
                ? "Connect a film folder, then run indexing to fill the catalogue."
                : "No films have been added yet. Ask your household administrator to connect the library."}
            </p>
          </EmptyState>
        ) : (
          <div className={styles.sections}>
            <PersonalRows />
            {state.data.recent.length > 0 && <MediaRow title="Recently added" items={state.data.recent} />}
            {state.data.categories.map((category) => (
              <MediaRow key={category.genre} title={category.genre} items={category.items} />
            ))}
          </div>
        ))}
    </div>
  )
}

/** The active profile's titles. A section that cannot be read says so, and the rest of the home still shows. */
async function loadPersonal(signal: AbortSignal) {
  const [started, favourites] = await Promise.all([listContinueWatching(CONTINUE_LIMIT, signal), listFavourites(signal)])
  return { continueWatching: started.flatMap((entry) => (entry.media ? [entry.media] : [])), favourites }
}

/** Continue watching and My List, for the active profile (CAT-08, PLY-08). */
function PersonalRows() {
  const { state, reload } = useAsync(loadPersonal)

  if (state.status === 'loading') return <LoadingState label="Loading your list…" />
  if (state.status === 'error') {
    return <ErrorState title="Your list cannot be shown" message={describeError(state.error)} onRetry={reload} />
  }
  return (
    <>
      <MediaRow
        title="Continue watching"
        items={state.data.continueWatching}
        empty="The films you start appear here, so you can pick up where you left off."
      />
      <MediaRow
        title="My List"
        items={state.data.favourites}
        empty="Add films to My List from their page to find them here."
      />
    </>
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
