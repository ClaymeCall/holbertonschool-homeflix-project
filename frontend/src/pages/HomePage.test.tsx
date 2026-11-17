import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { media } from '../test/fixtures'
import { jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const stars = media({ id: 'm1', title: 'Night of the Stars', genre: 'Documentary', durationSeconds: 5520 })
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
})
