import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { media } from '../test/fixtures'
import { callsTo, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const stars = media({ id: 'm1', title: 'Night of the Stars', genres: ['Documentary'], durationSeconds: 5520 })
const holidays = media({ id: 'm2', title: 'Holidays 2024', availability: 'DOWNLOADING' })

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
      () => jsonResponse([stars, holidays]),
      () => jsonResponse([
        { genre: 'Documentary', items: [stars] },
        { genre: 'Animation', items: [] },
      ]),
    )
    renderApp('/')

    const recent = await screen.findByRole('region', { name: 'Recently added' })
    expect(within(recent).getAllByRole('link')).toHaveLength(2)
    expect(within(recent).getByRole('link', { name: /Night of the Stars/ })).toHaveAttribute('href', '/movies/m1')
    expect(within(recent).getByText('Documentary · 1 h 32 min')).toBeInTheDocument()
    expect(within(recent).getByText('Downloading')).toBeInTheDocument()

    expect(screen.getByRole('region', { name: 'Documentary' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Animation' })).not.toBeInTheDocument()
    expect(String(fetch.mock.calls.find(([url]) => String(url).startsWith('/api/library/media'))?.[0])).toBe(
      '/api/library/media?sort=recent&limit=12',
    )
  })

  it('shows the placeholder poster when a title has none', async () => {
    home(() => jsonResponse([stars]))
    renderApp('/')
    const card = await screen.findByRole('link', { name: /Night of the Stars/ })
    expect(card.querySelector('[aria-hidden="true"]')).toBeInTheDocument()
    expect(within(card).queryByRole('img')).not.toBeInTheDocument()
  })

  it('tells an administrator how to fill an empty catalogue', async () => {
    home(() => jsonResponse([]))
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'The catalogue is empty' })).toBeInTheDocument()
    expect(screen.getByText(/run indexing/)).toBeInTheDocument()
  })

  it('tells a household member who to ask when the catalogue is empty', async () => {
    home(() => jsonResponse([]), undefined, { ...testAccount, admin: false })
    renderApp('/')
    expect(await screen.findByText(/Ask your household administrator/)).toBeInTheDocument()
  })

  it('shows an error with a retry button', async () => {
    let failing = true
    home(() => (failing ? jsonResponse({}, 503) : jsonResponse([stars])))
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('temporarily unavailable')
    failing = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('region', { name: 'Recently added' })).toBeInTheDocument()
  })

  it('renders titles as text, never as HTML', async () => {
    home(() => jsonResponse([media({ id: 'x', title: '<img src=x onerror=alert(1)>' })]))
    renderApp('/')
    expect((await screen.findAllByText('<img src=x onerror=alert(1)>')).length).toBeGreaterThan(0)
    expect(document.querySelector('img[src="x"]')).toBeNull()
  })

  // ---- The active profile's sections (I10; CAT-08, PLY-08) ----

  it('shows the started films in Continue watching and the listed ones in My List', async () => {
    const fetch = mockApi({
      ...signedInRoutes(),
      'GET /api/library/media': () => jsonResponse([stars]),
      'GET /api/library/categories': () => jsonResponse([]),
      'GET /api/library/progress': () =>
        jsonResponse([
          { mediaId: 'm1', positionSeconds: 600, durationSeconds: 5520, state: 'STARTED', updatedAt: '2026-11-22T20:00:00Z', media: stars },
        ]),
      'GET /api/library/favourites': () => jsonResponse([holidays]),
    })
    renderApp('/')

    const continuing = await screen.findByRole('region', { name: 'Continue watching' })
    expect(within(continuing).getByRole('link', { name: /Night of the Stars/ })).toHaveAttribute('href', '/movies/m1')
    const list = screen.getByRole('region', { name: 'My List' })
    expect(within(list).getByRole('link', { name: /Holidays 2024/ })).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/library/progress')[0][0]).toBe('/api/library/progress?state=STARTED&limit=12')
  })

  it('explains the two sections while they are empty', async () => {
    home(() => jsonResponse([stars]))
    renderApp('/')

    const continuing = await screen.findByRole('region', { name: 'Continue watching' })
    expect(continuing).toHaveTextContent('The films you start appear here, so you can pick up where you left off.')
    expect(screen.getByRole('region', { name: 'My List' })).toHaveTextContent(
      'Add films to My List from their page to find them here.',
    )
  })

  it('still shows the catalogue when the list of the profile cannot be read', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/library/media': () => jsonResponse([stars]),
      'GET /api/library/categories': () => jsonResponse([]),
      'GET /api/library/favourites': () =>
        jsonResponse({ error: 'service_unavailable', message: 'This part of HomeFlix is temporarily unavailable.' }, 503),
    })
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Your list cannot be shown' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Recently added' })).toBeInTheDocument()
  })
})
