import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { jsonResponse, mockApi, signedInRoutes, testAccount } from '../test/http'
import { renderApp } from '../test/render'

describe('AppLayout menu', () => {
  it('is behind a burger button that opens and closes it', async () => {
    mockApi({ ...signedInRoutes(testAccount), 'GET /api/library/titles': () => jsonResponse([]) })
    renderApp('/search')
    const user = userEvent.setup()

    const toggle = await screen.findByRole('button', { name: 'Open the menu' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveAttribute('aria-controls', 'site-menu')

    await user.click(toggle)
    expect(screen.getByRole('button', { name: 'Close the menu' })).toHaveAttribute('aria-expanded', 'true')

    await user.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Open the menu' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('closes after a link of the menu is followed', async () => {
    mockApi({ ...signedInRoutes(testAccount), 'GET /api/library/titles': () => jsonResponse([]) })
    renderApp('/search')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Open the menu' }))
    await user.click(screen.getByRole('link', { name: 'Home' }))
    expect(await screen.findByRole('button', { name: 'Open the menu' })).toHaveAttribute('aria-expanded', 'false')
  })
})
