import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { media } from '../test/fixtures'
import { jsonResponse, mockApi, signedInRoutes, type Route } from '../test/http'
import { renderApp } from '../test/render'

const catalogue = [
  media({ id: 'm1', title: 'Night of the Stars', genre: 'Documentary' }),
  media({ id: 'm2', title: 'Stars of the Sea', genre: 'Animation' }),
  media({ id: 'm3', title: 'Holidays 2024', genre: 'Family' }),
]

/** A fake catalogue that filters like the library service is expected to. */
const searchRoute: Route = ({ url }) => {
  const query = (url.searchParams.get('query') ?? '').toLowerCase()
  const genre = url.searchParams.get('genre')
  return jsonResponse(
    catalogue.filter((m) => m.title.toLowerCase().includes(query) && (!genre || m.genre === genre)),
  )
}

function search(route: Route = searchRoute, genres: Route = () => jsonResponse(['Animation', 'Documentary', 'Family'])) {
  return mockApi({ ...signedInRoutes(), 'GET /api/library/media': route, 'GET /api/library/genres': genres })
}

describe('SearchPage', () => {
  it('searches by title', async () => {
    search()
    renderApp('/search')
    const field = await screen.findByRole('searchbox', { name: 'Title' })
    expect(field).toHaveFocus()

    await userEvent.type(field, 'stars{Enter}')

    expect(await screen.findByRole('status')).toHaveTextContent('2 films for ‘stars’')
    const results = screen.getByRole('list', { name: 'Search results' })
    expect(results.children).toHaveLength(2)
  })

  it('filters by genre', async () => {
    const fetch = search()
    renderApp('/search?q=stars')
    const genre = await screen.findByRole('combobox', { name: 'Genre' })
    await screen.findByRole('option', { name: 'Animation' })

    await userEvent.selectOptions(genre, 'Animation')

    expect(await screen.findByText('1 film for ‘stars’ in Animation')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Stars of the Sea/ })).toBeInTheDocument()
    expect(String(fetch.mock.calls.at(-1)?.[0])).toBe('/api/library/media?query=stars&genre=Animation')
  })

  it('restores the search from the address, so results can be shared or reloaded', async () => {
    search()
    renderApp('/search?q=holidays&genre=Family')
    expect(await screen.findByText('1 film for ‘holidays’ in Family')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Title' })).toHaveValue('holidays')
    expect(screen.getByRole('combobox', { name: 'Genre' })).toHaveValue('Family')
  })

  it('explains when nothing matches and lets the user clear the search', async () => {
    search()
    renderApp('/search?q=nowhere')

    expect(await screen.findByRole('heading', { name: 'No films found' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }))

    expect(await screen.findByText('3 films in the catalogue')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Title' })).toHaveValue('')
  })

  it('still searches by title when the genres cannot be loaded', async () => {
    search(searchRoute, () => jsonResponse({}, 503))
    renderApp('/search')

    const genre = await screen.findByRole('combobox', { name: 'Genre' })
    expect(await screen.findByText('The list of genres could not be loaded.')).toBeInTheDocument()
    expect(genre).toBeDisabled()
    expect(await screen.findByText('3 films in the catalogue')).toBeInTheDocument()
  })

  it('shows an error with a retry button when the search fails', async () => {
    let failing = true
    search((request) => (failing ? jsonResponse({}, 500) : searchRoute(request)))
    renderApp('/search?q=holidays')

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
    failing = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('1 film for ‘holidays’')).toBeInTheDocument()
  })

  it('is reachable from the main navigation, which marks the current page', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/library/media': searchRoute,
      'GET /api/library/genres': () => jsonResponse([]),
      'GET /api/library/categories': () => jsonResponse([]),
    })
    renderApp('/')
    const nav = await screen.findByRole('navigation', { name: 'Main navigation' })
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page')

    await userEvent.click(screen.getByRole('link', { name: 'Search' }))
    expect(await screen.findByRole('heading', { name: 'Search' })).toBeInTheDocument()
    expect(nav.querySelector('[aria-current="page"]')).toHaveTextContent('Search')
  })
})
