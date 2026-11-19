import { screen } from '@testing-library/react'
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
})
