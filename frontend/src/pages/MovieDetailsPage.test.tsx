import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { MediaDetails } from '../api/library'
import { callsTo, jsonResponse, mockApi, signedInRoutes, type Route } from '../test/http'
import { renderApp } from '../test/render'

const movie: MediaDetails = {
  id: 'm1',
  title: 'Night of the Stars',
  description: 'A family watches the sky.',
  posterUrl: 'https://upload.wikimedia.org/poster.jpg',
  genres: ['Documentary', 'Nature'],
  durationSeconds: 5520,
  availability: 'AVAILABLE',
}

function mockMovie(route: Route) {
  return mockApi({ ...signedInRoutes(), 'GET /api/library/media/m1': route })
}

describe('MovieDetailsPage', () => {
  it('shows every field of an available movie and a play link', async () => {
    const fetch = mockMovie(() => jsonResponse(movie))
    renderApp('/movies/m1')

    expect(await screen.findByRole('heading', { name: movie.title })).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/library/media/m1')).toHaveLength(1)
    expect(screen.getByText('A family watches the sky.')).toBeInTheDocument()
    expect(screen.getByText('Documentary, Nature')).toBeInTheDocument()
    expect(screen.getByText('1 h 32 min')).toBeInTheDocument()
    expect(screen.getByText('Available')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: `Poster for ${movie.title}` })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Play' })).toHaveAttribute('href', '/watch/m1')
  })

  it('shows placeholders when metadata is missing', async () => {
    mockMovie(() => jsonResponse({ ...movie, description: null, posterUrl: null, genres: [], durationSeconds: null }))
    renderApp('/movies/m1')

    expect(await screen.findByText('No description available for this film.')).toBeInTheDocument()
    expect(screen.getByText('Genre not specified')).toBeInTheDocument()
    expect(screen.getByText('Unknown duration')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: `Poster unavailable for ${movie.title}` })).toBeInTheDocument()
  })

  it('disables playback with a clear message when the movie is unavailable', async () => {
    mockMovie(() => jsonResponse({ ...movie, availability: 'UNAVAILABLE' }))
    renderApp('/movies/m1')

    const play = await screen.findByRole('button', { name: 'Play' })
    expect(play).toBeDisabled()
    expect(play).toHaveAccessibleDescription(/file cannot be found/)
    expect(screen.getByText('Unavailable')).toBeInTheDocument()
  })

  it('explains that a downloading movie cannot be played yet', async () => {
    mockMovie(() => jsonResponse({ ...movie, availability: 'DOWNLOADING' }))
    renderApp('/movies/m1')

    expect(await screen.findByRole('button', { name: 'Play' })).toHaveAccessibleDescription(
      /is downloading/,
    )
    expect(screen.getByText('Downloading')).toBeInTheDocument()
  })

  it('shows a not found message for an unknown movie', async () => {
    mockApi(signedInRoutes())
    renderApp('/movies/unknown')

    expect(await screen.findByRole('heading', { name: 'Film not found' })).toBeInTheDocument()
  })

  it('shows an error with a retry button that reloads the movie', async () => {
    let calls = 0
    mockMovie(() => (++calls === 1 ? jsonResponse({}, 503) : jsonResponse(movie)))
    renderApp('/movies/m1')

    expect(await screen.findByRole('alert')).toHaveTextContent('temporarily unavailable')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: movie.title })).toBeInTheDocument()
  })

  // ---- Resume, My List and reset (I10; CAT-07, PLY-08, PLY-09) ----

  function withProgress(progress: Route, routes: Record<string, Route> = {}) {
    return mockApi({
      ...signedInRoutes(),
      'GET /api/library/media/m1': () => jsonResponse(movie),
      'GET /api/library/progress/m1': progress,
      ...routes,
    })
  }

  const startedAt = (positionSeconds: number, state: 'STARTED' | 'COMPLETED' = 'STARTED') => () =>
    jsonResponse({ mediaId: 'm1', positionSeconds, durationSeconds: 5520, state, updatedAt: '2026-11-22T20:00:00Z' })

  it('offers to resume a started film, or to play it from the beginning', async () => {
    withProgress(startedAt(2530))
    renderApp('/movies/m1')

    expect(await screen.findByRole('link', { name: /Resume from 42:10/ })).toHaveAttribute('href', '/watch/m1')
    expect(screen.getByRole('link', { name: 'Play from the beginning' })).toHaveAttribute('href', '/watch/m1?from=start')
    expect(screen.queryByRole('link', { name: /^Play$/ })).not.toBeInTheDocument()
  })

  it('offers Play for a film that was not started, and nothing to reset', async () => {
    withProgress(() => jsonResponse({ error: 'not_found', message: 'This address does not exist.' }, 404))
    renderApp('/movies/m1')

    expect(await screen.findByRole('link', { name: /Play/ })).toHaveAttribute('href', '/watch/m1')
    expect(screen.queryByRole('link', { name: /Resume/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reset progress' })).not.toBeInTheDocument()
  })

  it('plays a completed film from the beginning, and can reset it', async () => {
    withProgress(startedAt(5400, 'COMPLETED'))
    renderApp('/movies/m1')

    expect(await screen.findByRole('button', { name: 'Reset progress' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Play/ })).toHaveAttribute('href', '/watch/m1')
    expect(screen.queryByRole('link', { name: /Resume/ })).not.toBeInTheDocument()
  })

  it('resets the progress, so the film plays from the beginning again', async () => {
    const fetch = withProgress(startedAt(2530), { 'DELETE /api/library/progress/m1': () => new Response(null, { status: 204 }) })
    renderApp('/movies/m1')

    await userEvent.click(await screen.findByRole('button', { name: 'Reset progress' }))

    const notice = await screen.findByRole('status')
    expect(notice).toHaveTextContent('The progress was reset. The film now plays from the beginning.')
    expect(notice).toHaveFocus()
    expect(callsTo(fetch, 'DELETE /api/library/progress/m1')).toHaveLength(1)
    expect(screen.getByRole('link', { name: /Play/ })).toHaveAttribute('href', '/watch/m1')
    expect(screen.queryByRole('button', { name: 'Reset progress' })).not.toBeInTheDocument()
  })

  it('adds the film to My List and removes it again', async () => {
    const fetch = withProgress(startedAt(0), {
      'PUT /api/library/favourites/m1': () => new Response(null, { status: 204 }),
      'DELETE /api/library/favourites/m1': () => new Response(null, { status: 204 }),
    })
    renderApp('/movies/m1')

    const toggle = await screen.findByRole('button', { name: 'My List' })
    expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(toggle)
    await waitFor(() => expect(toggle).toHaveAttribute('aria-pressed', 'true'))
    expect(callsTo(fetch, 'PUT /api/library/favourites/m1')).toHaveLength(1)

    await userEvent.click(toggle)
    await waitFor(() => expect(toggle).toHaveAttribute('aria-pressed', 'false'))
    expect(callsTo(fetch, 'DELETE /api/library/favourites/m1')).toHaveLength(1)
  })

  it('keeps focus on My List while the change is saved, and sends it once', async () => {
    let answer: (response: Response) => void = () => undefined
    const fetch = withProgress(startedAt(0), {
      'PUT /api/library/favourites/m1': () => new Promise<Response>((resolve) => (answer = resolve)),
    })
    renderApp('/movies/m1')

    const toggle = await screen.findByRole('button', { name: 'My List' })
    toggle.focus()
    await userEvent.keyboard('{Enter}')
    await waitFor(() => expect(toggle).toHaveAttribute('aria-disabled', 'true'))
    expect(toggle).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    expect(callsTo(fetch, 'PUT /api/library/favourites/m1')).toHaveLength(1)

    answer(new Response(null, { status: 204 }))
    await waitFor(() => expect(toggle).toHaveAttribute('aria-pressed', 'true'))
    expect(toggle).toHaveAttribute('aria-disabled', 'false')
    expect(toggle).toHaveFocus()
  })

  it('shows a film already in My List as pressed', async () => {
    withProgress(startedAt(0), { 'GET /api/library/favourites': () => jsonResponse([movie]) })
    renderApp('/movies/m1')

    expect(await screen.findByRole('button', { name: 'My List' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('shows the server message when My List cannot be changed', async () => {
    withProgress(startedAt(0), {
      'PUT /api/library/favourites/m1': () =>
        jsonResponse({ error: 'service_unavailable', message: 'This part of HomeFlix is temporarily unavailable.' }, 503),
    })
    renderApp('/movies/m1')

    const toggle = await screen.findByRole('button', { name: 'My List' })
    await userEvent.click(toggle)

    expect(await screen.findByRole('alert')).toHaveTextContent('This part of HomeFlix is temporarily unavailable.')
    expect(toggle).toHaveAttribute('aria-pressed', 'false')
  })
})
