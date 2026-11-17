import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { media } from '../test/fixtures'
import { jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const etoiles = media({ id: 'm1', title: 'La Nuit des Étoiles', genre: 'Documentaire', durationSeconds: 5520 })
const vacances = media({ id: 'm2', title: 'Vacances 2024', availability: 'DOWNLOADING' })

function home(recent: Route, categories: Route = () => jsonResponse([]), account = testAccount) {
  return mockApi({
    ...signedInRoutes(account),
    'GET /api/library/media': recent,
    'GET /api/library/categories': categories,
  })
}

describe('HomePage', () => {
  it('shows recently added titles and one section per category', async () => {
    const fetch = home(
      () => jsonResponse([etoiles, vacances]),
      () => jsonResponse([
        { genre: 'Documentaire', items: [etoiles] },
        { genre: 'Animation', items: [] },
      ]),
    )
    renderApp('/')

    const recent = await screen.findByRole('region', { name: 'Ajouts récents' })
    expect(within(recent).getAllByRole('link')).toHaveLength(2)
    expect(within(recent).getByRole('link', { name: /La Nuit des Étoiles/ })).toHaveAttribute('href', '/movies/m1')
    expect(within(recent).getByText('Documentaire · 1 h 32 min')).toBeInTheDocument()
    expect(within(recent).getByText('En téléchargement')).toBeInTheDocument()

    expect(screen.getByRole('region', { name: 'Documentaire' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Animation' })).not.toBeInTheDocument()
    expect(String(fetch.mock.calls.find(([url]) => String(url).startsWith('/api/library/media'))?.[0])).toBe(
      '/api/library/media?sort=recent&limit=12',
    )
  })

  it('shows the placeholder poster when a title has none', async () => {
    home(() => jsonResponse([etoiles]))
    renderApp('/')
    const card = await screen.findByRole('link', { name: /La Nuit des Étoiles/ })
    expect(card.querySelector('[aria-hidden="true"]')).toBeInTheDocument()
    expect(within(card).queryByRole('img')).not.toBeInTheDocument()
  })

  it('tells an administrator how to fill an empty catalogue', async () => {
    home(() => jsonResponse([]))
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Le catalogue est vide' })).toBeInTheDocument()
    expect(screen.getByText(/lancez l'indexation/)).toBeInTheDocument()
  })

  it('tells a household member who to ask when the catalogue is empty', async () => {
    home(() => jsonResponse([]), undefined, { ...testAccount, role: 'USER' })
    renderApp('/')
    expect(await screen.findByText(/Demandez à l'administrateur/)).toBeInTheDocument()
  })

  it('shows an error with a retry button', async () => {
    let failing = true
    home(() => (failing ? jsonResponse({}, 503) : jsonResponse([etoiles])))
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('momentanément indisponible')
    failing = false
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }))
    expect(await screen.findByRole('region', { name: 'Ajouts récents' })).toBeInTheDocument()
  })

  it('renders titles as text, never as HTML', async () => {
    home(() => jsonResponse([media({ id: 'x', title: '<img src=x onerror=alert(1)>' })]))
    renderApp('/')
    expect((await screen.findAllByText('<img src=x onerror=alert(1)>')).length).toBeGreaterThan(0)
    expect(document.querySelector('img[src="x"]')).toBeNull()
  })
})
