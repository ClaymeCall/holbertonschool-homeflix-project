import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { describeError } from '../api/errors'
import { getGenres, searchMedia } from '../api/library'
import { Button } from '../components/Button'
import { MediaGrid } from '../components/MediaGrid'
import { EmptyState, ErrorState, LoadingState } from '../components/StatusMessage'
import { useAsync } from '../hooks/useAsync'
import styles from './SearchPage.module.css'

const QUERY_MAX_LENGTH = 100

function resultsLabel(count: number, query: string, genre: string): string {
  const films = count === 1 ? '1 film' : `${count} films`
  const filters = [query && `pour « ${query} »`, genre && `dans le genre ${genre}`].filter(Boolean).join(' ')
  return filters ? `${films} ${filters}` : `${films} dans le catalogue`
}

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const query = (params.get('q') ?? '').trim().slice(0, QUERY_MAX_LENGTH)
  const genre = params.get('genre') ?? ''
  const [draft, setDraft] = useState(query)
  const queryId = useId()
  const genreId = useId()

  // Keep the field in sync when the URL changes (back button, "clear" action).
  const [syncedQuery, setSyncedQuery] = useState(query)
  if (syncedQuery !== query) {
    setSyncedQuery(query)
    setDraft(query)
  }

  const loadGenres = useCallback((signal: AbortSignal) => getGenres(signal), [])
  const genres = useAsync(loadGenres).state

  const loadResults = useCallback((signal: AbortSignal) => searchMedia({ query, genre }, signal), [query, genre])
  const { state: results, reload } = useAsync(loadResults)

  useEffect(() => {
    document.getElementById(queryId)?.focus()
  }, [queryId])

  function applyFilters(next: { query: string; genre: string }) {
    const nextParams = new URLSearchParams()
    const trimmed = next.query.trim()
    if (trimmed) nextParams.set('q', trimmed)
    if (next.genre) nextParams.set('genre', next.genre)
    setParams(nextParams)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    applyFilters({ query: draft, genre })
  }

  const hasFilters = Boolean(query || genre)

  return (
    <div className={styles.page}>
      <title>{query ? `Recherche « ${query} » · HomeFlix` : 'Rechercher · HomeFlix'}</title>
      <h1 className={styles.title}>Rechercher</h1>

      <form className={styles.filters} role="search" onSubmit={handleSubmit}>
        <div className={styles.field}>
          <label htmlFor={queryId} className={styles.label}>
            Titre
          </label>
          <div className={styles.queryRow}>
            <input
              id={queryId}
              className={styles.input}
              type="search"
              name="q"
              value={draft}
              maxLength={QUERY_MAX_LENGTH}
              placeholder="Ex. : La Nuit des Étoiles"
              autoComplete="off"
              onChange={(event) => setDraft(event.target.value)}
            />
            <Button type="submit">Rechercher</Button>
          </div>
        </div>

        <div className={styles.field}>
          <label htmlFor={genreId} className={styles.label}>
            Genre
          </label>
          <select
            id={genreId}
            className={styles.input}
            name="genre"
            value={genre}
            disabled={genres.status !== 'success'}
            aria-describedby={genres.status === 'error' ? `${genreId}-error` : undefined}
            onChange={(event) => applyFilters({ query: draft, genre: event.target.value })}
          >
            <option value="">Tous les genres</option>
            {genres.status === 'success' &&
              genres.data.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            {/* Keep a genre from the URL selectable even if the list does not contain it. */}
            {genre && genres.status === 'success' && !genres.data.includes(genre) && (
              <option value={genre}>{genre}</option>
            )}
          </select>
          {genres.status === 'error' && (
            <p id={`${genreId}-error`} className={styles.hint}>
              La liste des genres n'a pas pu être chargée.
            </p>
          )}
        </div>
      </form>

      <div className={styles.results}>
        {results.status === 'loading' && <LoadingState label="Recherche en cours…" />}

        {results.status === 'error' && <ErrorState message={describeError(results.error)} onRetry={reload} />}

        {results.status === 'success' && (
          <>
            <p className={styles.count} role="status">
              {resultsLabel(results.data.length, query, genre)}
            </p>
            {results.data.length === 0 ? (
              <EmptyState title={hasFilters ? 'Aucun film trouvé' : 'Le catalogue est vide'}>
                {hasFilters ? (
                  <>
                    <p>Vérifiez l'orthographe ou essayez un autre genre.</p>
                    <Button variant="secondary" className={styles.clear} onClick={() => applyFilters({ query: '', genre: '' })}>
                      Effacer la recherche
                    </Button>
                  </>
                ) : (
                  <p>Aucun film n'a encore été ajouté à la bibliothèque.</p>
                )}
              </EmptyState>
            ) : (
              <MediaGrid items={results.data} label="Résultats de la recherche" />
            )}
          </>
        )}
      </div>
    </div>
  )
}
