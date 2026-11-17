import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { MediaDetails } from '../api/library'
import { jsonResponse, mockFetch } from '../test/http'
import { renderApp } from '../test/render'

const movie: MediaDetails = {
  id: 'm1',
  title: 'La Nuit des Étoiles',
  description: 'Une famille observe le ciel.',
  posterUrl: 'https://upload.wikimedia.org/poster.jpg',
  genre: 'Documentaire',
  durationSeconds: 5520,
  availability: 'AVAILABLE',
}

describe('MovieDetailsPage', () => {
  it('shows every field of an available movie and a play link', async () => {
    const fetch = mockFetch(() => jsonResponse(movie))
    renderApp('/movies/m1')

    expect(await screen.findByRole('heading', { name: movie.title })).toBeInTheDocument()
    expect(fetch.mock.calls[0][0]).toBe('/api/library/media/m1')
    expect(screen.getByText('Une famille observe le ciel.')).toBeInTheDocument()
    expect(screen.getByText('Documentaire')).toBeInTheDocument()
    expect(screen.getByText('1 h 32 min')).toBeInTheDocument()
    expect(screen.getByText('Disponible')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: `Affiche de ${movie.title}` })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Lire' })).toHaveAttribute('href', '/watch/m1')
  })

  it('shows placeholders when metadata is missing', async () => {
    mockFetch(() => jsonResponse({ ...movie, description: null, posterUrl: null, genre: null, durationSeconds: null }))
    renderApp('/movies/m1')

    expect(await screen.findByText('Aucune description disponible pour ce film.')).toBeInTheDocument()
    expect(screen.getByText('Genre non renseigné')).toBeInTheDocument()
    expect(screen.getByText('Durée inconnue')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: `Affiche indisponible pour ${movie.title}` })).toBeInTheDocument()
  })

  it('disables playback with a clear message when the movie is unavailable', async () => {
    mockFetch(() => jsonResponse({ ...movie, availability: 'UNAVAILABLE' }))
    renderApp('/movies/m1')

    const play = await screen.findByRole('button', { name: 'Lire' })
    expect(play).toBeDisabled()
    expect(play).toHaveAccessibleDescription(/fichier est introuvable/)
    expect(screen.getByText('Indisponible')).toBeInTheDocument()
  })

  it('explains that a downloading movie cannot be played yet', async () => {
    mockFetch(() => jsonResponse({ ...movie, availability: 'DOWNLOADING' }))
    renderApp('/movies/m1')

    expect(await screen.findByRole('button', { name: 'Lire' })).toHaveAccessibleDescription(
      /en cours de téléchargement/,
    )
    expect(screen.getByText('En téléchargement')).toBeInTheDocument()
  })

  it('shows a not found message for an unknown movie', async () => {
    mockFetch(() => jsonResponse({ code: 'NOT_FOUND' }, 404))
    renderApp('/movies/unknown')

    expect(await screen.findByRole('heading', { name: 'Film introuvable' })).toBeInTheDocument()
  })

  it('shows an error with a retry button that reloads the movie', async () => {
    let calls = 0
    mockFetch(() => (++calls === 1 ? jsonResponse({}, 503) : jsonResponse(movie)))
    renderApp('/movies/m1')

    expect(await screen.findByRole('alert')).toHaveTextContent('momentanément indisponible')
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }))
    expect(await screen.findByRole('heading', { name: movie.title })).toBeInTheDocument()
  })
})
