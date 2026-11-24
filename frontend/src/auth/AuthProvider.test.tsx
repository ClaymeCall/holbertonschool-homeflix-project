import { act, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { callsTo, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const REFRESH = 'POST /api/identity/auth/refresh'

let visibility: DocumentVisibilityState = 'visible'

/** Simulates the browser hiding or showing the tab. */
function setVisibility(state: DocumentVisibilityState) {
  visibility = state
  act(() => {
    document.dispatchEvent(new Event('visibilitychange'))
  })
}

async function signedInApp(routes: Record<string, Route> = {}) {
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility)
  const fetch = mockApi({ ...signedInRoutes(), ...routes })
  renderApp('/')
  await screen.findByRole('button', { name: 'Sign out' })
  return fetch
}

/** Moves the clock used to know when the session was last renewed. */
function minutesLater(minutes: number) {
  const now = Date.now()
  vi.spyOn(Date, 'now').mockReturnValue(now + minutes * 60 * 1000)
}

describe('AuthProvider session renewal', () => {
  afterEach(() => {
    visibility = 'visible'
  })

  it('renews the session when a hidden tab becomes visible again', async () => {
    const fetch = await signedInApp()
    setVisibility('hidden')
    minutesLater(5)
    setVisibility('visible')

    expect(callsTo(fetch, REFRESH)).toHaveLength(1)
  })

  it('does not renew again when the tab comes back right after a renewal', async () => {
    const fetch = await signedInApp()
    setVisibility('hidden')
    setVisibility('visible')

    expect(callsTo(fetch, REFRESH)).toHaveLength(0)
  })

  it('does not renew when the tab becomes hidden', async () => {
    const fetch = await signedInApp()
    minutesLater(5)
    setVisibility('hidden')

    expect(callsTo(fetch, REFRESH)).toHaveLength(0)
  })

  it('shows the profile screen again when the renewed session has lost its profile', async () => {
    // The active profile was deleted meanwhile, for example from another device (identity.yaml, /auth/refresh).
    await signedInApp({ [REFRESH]: () => jsonResponse({ ...testAccount, profileId: null }) })
    setVisibility('hidden')
    minutesLater(5)
    setVisibility('visible')

    expect(await screen.findByRole('heading', { level: 1, name: 'Who is watching?' })).toBeInTheDocument()
  })
})
