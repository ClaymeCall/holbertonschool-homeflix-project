import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { callsTo, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const unavailable = () =>
  jsonResponse(
    {
      error: 'service_unavailable',
      message: 'This part of HomeFlix is temporarily unavailable. Please try again in a moment.',
    },
    503,
  )

function services(routes: Record<string, Route> = {}, account = testAccount) {
  return mockApi({
    ...signedInRoutes(account),
    'GET /api/identity/accounts': () => jsonResponse([]),
    'GET /api/library/roots': () => jsonResponse([]),
    'GET /api/acquisition/entries': () => jsonResponse([]),
    ...routes,
  })
}

async function serviceItem(name: string) {
  const list = await screen.findByRole('list', { name: 'Services' })
  return within(list).getByRole('heading', { name }).closest('li') as HTMLElement
}

describe('Administration area', () => {
  it('is in the main navigation of an administrator and opens on the status', async () => {
    services()
    renderApp('/')
    const nav = await screen.findByRole('navigation', { name: 'Main navigation' })
    await userEvent.click(within(nav).getByRole('link', { name: 'Administration' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Status' })).toBeInTheDocument()
    const admin = screen.getByRole('navigation', { name: 'Administration' })
    expect(within(admin).getByRole('link', { name: 'Status' })).toHaveAttribute('aria-current', 'page')
    expect(within(admin).getByRole('link', { name: 'Downloads' })).toHaveAttribute('href', '/admin/downloads')
  })

  it('is not offered to an ordinary user, and calls no administrator operation for one', async () => {
    const fetch = services({}, { ...testAccount, admin: false })
    renderApp('/admin/status')

    expect(await screen.findByRole('heading', { name: 'Administrators only' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Administration' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Administration' })).not.toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/identity/accounts')).toHaveLength(0)
    expect(callsTo(fetch, 'GET /api/library/roots')).toHaveLength(0)
    expect(callsTo(fetch, 'GET /api/acquisition/entries')).toHaveLength(0)
  })
})

describe('StatusPage', () => {
  it('shows each service as working when it answers', async () => {
    const fetch = services()
    renderApp('/admin/status')

    for (const name of ['Accounts and sign-in', 'Library and playback', 'Downloads']) {
      const item = await serviceItem(name)
      expect(within(item).getByText('Working')).toBeInTheDocument()
      expect(within(item).getByText('Working normally.')).toBeInTheDocument()
    }
    expect(callsTo(fetch, 'GET /api/identity/accounts')[0][0]).toBe('/api/identity/accounts?limit=1')
  })

  it('says in plain language what stops working when a service is unavailable', async () => {
    services({ 'GET /api/library/roots': unavailable })
    renderApp('/admin/status')

    const library = await serviceItem('Library and playback')
    expect(within(library).getByText('Unavailable')).toBeInTheDocument()
    expect(within(library).getByText(/Titles cannot be browsed or played/)).toBeInTheDocument()
    expect(within(await serviceItem('Accounts and sign-in')).getByText('Working')).toBeInTheDocument()
  })

  it('shows a service that cannot be reached at all as unavailable', async () => {
    services({ 'GET /api/acquisition/entries': () => Promise.reject(new TypeError('Failed to fetch')) })
    renderApp('/admin/status')

    const downloads = await serviceItem('Downloads')
    expect(within(downloads).getByText('Unavailable')).toBeInTheDocument()
    expect(within(downloads).getByText(/Downloads cannot be started/)).toBeInTheDocument()
  })

  it('shows the server message when a service refuses the check', async () => {
    services({
      'GET /api/acquisition/entries': () =>
        jsonResponse({ error: 'forbidden', message: 'Only an administrator can do this.' }, 403),
    })
    renderApp('/admin/status')

    const downloads = await serviceItem('Downloads')
    expect(within(downloads).getByText('Not checked')).toBeInTheDocument()
    expect(within(downloads).getByText('Only an administrator can do this.')).toBeInTheDocument()
  })

  it('checks again on request, keeping the focus on its button', async () => {
    let answer: () => Promise<Response> = async () => unavailable()
    const fetch = services({ 'GET /api/library/roots': () => answer() })
    renderApp('/admin/status')
    expect(within(await serviceItem('Library and playback')).getByText('Unavailable')).toBeInTheDocument()

    let answerNow: () => void = () => {}
    answer = () => new Promise((resolve) => (answerNow = () => resolve(jsonResponse([]))))
    const button = screen.getByRole('button', { name: 'Check again' })
    await userEvent.click(button)
    // Still enabled while checking: a disabled button would lose the focus.
    expect(await screen.findByText('Checking the services…')).toBeInTheDocument()
    expect(button).toBeEnabled()
    answerNow()

    expect(within(await serviceItem('Library and playback')).getByText('Working')).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/library/roots')).toHaveLength(2)
    expect(button).toHaveFocus()
  })
})
