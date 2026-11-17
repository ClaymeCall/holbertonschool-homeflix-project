import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockApi, signedInRoutes } from './test/http'
import { renderApp } from './test/render'

describe('App', () => {
  it('shows the HomeFlix logo linking to the home page', async () => {
    mockApi(signedInRoutes())
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'HomeFlix, accueil' })).toHaveAttribute('href', '/')
  })

  it('offers a skip link to the main content', async () => {
    mockApi(signedInRoutes())
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'Aller au contenu' })).toHaveAttribute('href', '#contenu')
  })

  it('shows a not found page for unknown paths', async () => {
    mockApi(signedInRoutes())
    renderApp('/nowhere')
    expect(await screen.findByRole('heading', { name: 'Page introuvable' })).toBeInTheDocument()
  })

  it('explains when HomeFlix cannot be reached and lets the user retry', async () => {
    let reachable = false
    const routes = signedInRoutes()
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        if (!reachable) return Promise.reject(new TypeError('Failed to fetch'))
        const url = new URL(String(input), 'http://homeflix.test')
        return Promise.resolve(routes[`${init?.method ?? 'GET'} ${url.pathname}`]({ url, init }))
      }),
    )
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Impossible de joindre HomeFlix')
    reachable = true
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }))
    expect(await screen.findByRole('button', { name: 'Se déconnecter' })).toBeInTheDocument()
  })

  it('never shows technical details from the server', async () => {
    mockApi({ 'GET /api/identity/setup/status': () => jsonResponse({ message: 'NullPointerException at line 42' }, 500) })
    renderApp('/')
    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent('NullPointerException')
  })
})
