import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { jsonResponse, mockApi, signedInRoutes } from './test/http'
import { renderApp } from './test/render'

/** Stands for the browser's back button. */
function BrowserBack() {
  const navigate = useNavigate()
  return (
    <button type="button" onClick={() => navigate(-1)}>
      Browser back
    </button>
  )
}

describe('App', () => {
  it('shows the HomeFlix logo linking to the home page', async () => {
    mockApi(signedInRoutes())
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'HomeFlix, home' })).toHaveAttribute('href', '/')
  })

  it('offers a skip link to the main content', async () => {
    mockApi(signedInRoutes())
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#content')
  })

  it('moves focus to the main content after following a link, not on the first load', async () => {
    mockApi(signedInRoutes())
    renderApp('/nowhere')
    const backHome = await screen.findByRole('link', { name: 'Back to home' })
    expect(screen.getByRole('main')).not.toHaveFocus()

    await userEvent.click(backHome)

    expect(await screen.findByRole('main')).toHaveFocus()
  })

  it('starts a page opened from a link at the top, and leaves back and forward to the browser', async () => {
    mockApi(signedInRoutes())
    render(
      <MemoryRouter initialEntries={['/nowhere']}>
        <App />
        <BrowserBack />
      </MemoryRouter>,
    )
    const backHome = await screen.findByRole('link', { name: 'Back to home' })
    expect(window.scrollTo).not.toHaveBeenCalled()

    // Two links: each new page starts at the top.
    await userEvent.click(backHome)
    await userEvent.click(await screen.findByRole('link', { name: 'Search' }))
    await screen.findByRole('searchbox')
    expect(window.scrollTo).toHaveBeenCalledTimes(2)
    expect(window.scrollTo).toHaveBeenLastCalledWith(0, 0)

    // Back to the home page (not the first entry): the browser restores the position, HomeFlix does not scroll.
    await userEvent.click(screen.getByRole('button', { name: 'Browser back' }))
    expect(await screen.findByRole('link', { name: 'Home', current: 'page' })).toBeInTheDocument()
    expect(window.scrollTo).toHaveBeenCalledTimes(2)
  })

  it('leaves focus where the new page put it', async () => {
    mockApi(signedInRoutes())
    renderApp('/nowhere')
    await userEvent.click(await screen.findByRole('link', { name: 'Search' }))

    expect(await screen.findByRole('searchbox')).toHaveFocus()
  })

  it('shows a not found page for unknown paths', async () => {
    mockApi(signedInRoutes())
    renderApp('/nowhere')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
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

    expect(await screen.findByRole('alert')).toHaveTextContent('HomeFlix cannot be reached')
    reachable = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('never shows technical details from the server', async () => {
    mockApi({ 'GET /api/identity/setup/status': () => jsonResponse({ message: 'NullPointerException at line 42' }, 500) })
    renderApp('/')
    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent('NullPointerException')
    expect(alert).toHaveTextContent('Something went wrong on our side')
  })
})
