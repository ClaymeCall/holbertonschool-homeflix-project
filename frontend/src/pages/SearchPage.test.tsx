import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { media } from '../test/fixtures'
import { jsonResponse, mockApi, signedInRoutes, type Route } from '../test/http'
import { renderApp } from '../test/render'

const catalogue = [
  media({ id: 'm1', title: 'La Nuit des Étoiles', genre: 'Documentaire' }),
  media({ id: 'm2', title: 'Les Étoiles de Mer', genre: 'Animation' }),
  media({ id: 'm3', title: 'Vacances 2024', genre: 'Famille' }),
]

/** A fake catalogue that filters like the library service is expected to. */
const searchRoute: Route = ({ url }) => {
  const query = (url.searchParams.get('query') ?? '').toLowerCase()
  const genre = url.searchParams.get('genre')
  return jsonResponse(
    catalogue.filter((m) => m.title.toLowerCase().includes(query) && (!genre || m.genre === genre)),
  )
}

function search(route: Route = searchRoute, genres: Route = () => jsonResponse(['Animation', 'Documentaire', 'Famille'])) {
  return mockApi({ ...signedInRoutes(), 'GET /api/library/media': route, 'GET /api/library/genres': genres })
}

describe('SearchPage', () => {
  it('searches by title', async () => {
    search()
    renderApp('/search')
    const field = await screen.findByRole('searchbox', { name: 'Titre' })
    expect(field).toHaveFocus()

    await userEvent.type(field, 'étoiles{Enter}')

    expect(await screen.findByRole('status')).toHaveTextContent('2 films pour « étoiles »')
    const results = screen.getByRole('list', { name: 'Résultats de la recherche' })
    expect(results.children).toHaveLength(2)
  })

  it('filters by genre', async () => {
    const fetch = search()
    renderApp('/search?q=étoiles')
    const genre = await screen.findByRole('combobox', { name: 'Genre' })
    await screen.findByRole('option', { name: 'Animation' })

    await userEvent.selectOptions(genre, 'Animation')

    expect(await screen.findByText('1 film pour « étoiles » dans le genre Animation')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Les Étoiles de Mer/ })).toBeInTheDocument()
    expect(String(fetch.mock.calls.at(-1)?.[0])).toBe('/api/library/media?query=%C3%A9toiles&genre=Animation')
  })

  it('restores the search from the address, so results can be shared or reloaded', async () => {
    search()
    renderApp('/search?q=vacances&genre=Famille')
    expect(await screen.findByText('1 film pour « vacances » dans le genre Famille')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Titre' })).toHaveValue('vacances')
    expect(screen.getByRole('combobox', { name: 'Genre' })).toHaveValue('Famille')
  })

  it('explains when nothing matches and lets the user clear the search', async () => {
    search()
    renderApp('/search?q=introuvable')

    expect(await screen.findByRole('heading', { name: 'Aucun film trouvé' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Effacer la recherche' }))

    expect(await screen.findByText('3 films dans le catalogue')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Titre' })).toHaveValue('')
  })

  it('still searches by title when the genres cannot be loaded', async () => {
    search(searchRoute, () => jsonResponse({}, 503))
    renderApp('/search')

    const genre = await screen.findByRole('combobox', { name: 'Genre' })
    expect(await screen.findByText("La liste des genres n'a pas pu être chargée.")).toBeInTheDocument()
    expect(genre).toBeDisabled()
    expect(await screen.findByText('3 films dans le catalogue')).toBeInTheDocument()
  })

  it('shows an error with a retry button when the search fails', async () => {
    let failing = true
    search((request) => (failing ? jsonResponse({}, 500) : searchRoute(request)))
    renderApp('/search?q=vacances')

    expect(await screen.findByRole('alert')).toHaveTextContent('Une erreur est survenue')
    failing = false
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }))
    expect(await screen.findByText('1 film pour « vacances »')).toBeInTheDocument()
  })

  it('is reachable from the main navigation, which marks the current page', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/library/media': searchRoute,
      'GET /api/library/genres': () => jsonResponse([]),
      'GET /api/library/categories': () => jsonResponse([]),
    })
    renderApp('/')
    const nav = await screen.findByRole('navigation', { name: 'Navigation principale' })
    expect(screen.getByRole('link', { name: 'Accueil' })).toHaveAttribute('aria-current', 'page')

    await userEvent.click(screen.getByRole('link', { name: 'Rechercher' }))
    expect(await screen.findByRole('heading', { name: 'Rechercher' })).toBeInTheDocument()
    expect(nav.querySelector('[aria-current="page"]')).toHaveTextContent('Rechercher')
  })
})
