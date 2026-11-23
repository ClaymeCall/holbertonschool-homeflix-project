import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Account, Profile } from '../api/identity'
import { callsTo, jsonResponse, mockApi, signedInRoutes, testAccount, testProfile, type Route } from '../test/http'
import { renderApp } from '../test/render'

const sam: Profile = { id: 'p2', name: 'Sam', createdAt: '2026-11-02T09:00:00Z' }

/** A session just after sign-in: no profile is active yet. */
const noProfile: Account = { ...testAccount, profileId: null }

function household(routes: Record<string, Route> = {}, account: Account = noProfile, profiles: Profile[] = [testProfile, sam]) {
  return mockApi({
    ...signedInRoutes(account),
    'GET /api/identity/profiles': () => jsonResponse(profiles),
    'GET /api/library/media': () => jsonResponse([]),
    ...routes,
  })
}

/** The server re-issues the session with the chosen profile. */
function selecting(): Record<string, Route> {
  return {
    'POST /api/identity/profiles/p1/select': () => jsonResponse({ ...testAccount, profileId: 'p1' }),
    'POST /api/identity/profiles/p2/select': () => jsonResponse({ ...testAccount, profileId: 'p2' }),
  }
}

describe('ProfilesPage', () => {
  it('is shown instead of a viewing screen while no profile is chosen', async () => {
    const fetch = household()
    renderApp('/search')

    expect(await screen.findByRole('heading', { level: 1, name: 'Who is watching?' })).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'Profiles' })
    expect(within(list).getByRole('button', { name: /Camille/ })).toBeInTheDocument()
    expect(within(list).getByRole('button', { name: /Sam/ })).toBeInTheDocument()
    // The viewing screen did not load anything for a session without a profile.
    expect(fetch.mock.calls.some(([url]) => String(url).startsWith('/api/library'))).toBe(false)
  })

  it('comes right after signing in, since a new session has no profile', async () => {
    household({
      'GET /api/identity/auth/me': () => jsonResponse({ error: 'unauthenticated', message: 'Sign in to continue.' }, 401),
      'POST /api/identity/auth/sign-in': () => jsonResponse(noProfile),
    })
    renderApp('/sign-in')

    await userEvent.type(await screen.findByLabelText('Email address'), 'camille@home.example')
    await userEvent.type(screen.getByLabelText('Password'), 'a long passphrase{Enter}')

    expect(await screen.findByRole('heading', { level: 1, name: 'Who is watching?' })).toBeInTheDocument()
  })

  it('also stands in front of the player', async () => {
    household()
    renderApp('/watch/m1')
    expect(await screen.findByRole('heading', { level: 1, name: 'Who is watching?' })).toBeInTheDocument()
  })

  it('makes the chosen profile active, then opens the screen that was asked for', async () => {
    const fetch = household(selecting())
    renderApp('/search')

    await userEvent.click(await screen.findByRole('button', { name: /Sam/ }))

    expect(callsTo(fetch, 'POST /api/identity/profiles/p2/select')).toHaveLength(1)
    expect(await screen.findByRole('heading', { level: 1, name: 'Search' })).toBeInTheDocument()
    // The header shows who is watching.
    const header = screen.getByRole('banner')
    expect(await within(header).findByText('Sam')).toBeInTheDocument()
    expect(within(header).getByRole('link', { name: 'Switch profile' })).toHaveAttribute('href', '/profiles')
  })

  it('can be used with the keyboard only', async () => {
    const fetch = household(selecting())
    renderApp('/')
    const user = userEvent.setup()

    await screen.findByRole('list', { name: 'Profiles' })
    await user.tab()
    expect(screen.getByRole('button', { name: /Camille/ })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: /Sam/ })).toHaveFocus()
    await user.keyboard('{Enter}')

    await waitFor(() => expect(callsTo(fetch, 'POST /api/identity/profiles/p2/select')).toHaveLength(1))
  })

  it('switches profile from the header, marking the current one', async () => {
    const fetch = household(selecting(), testAccount)
    renderApp('/')

    const header = await screen.findByRole('banner')
    expect(await within(header).findByText('Camille')).toBeInTheDocument()
    await userEvent.click(within(header).getByRole('link', { name: 'Switch profile' }))

    expect(await screen.findByRole('button', { name: /Camille/ })).toHaveAttribute('aria-current', 'true')
    expect(screen.getByRole('button', { name: /Sam/ })).not.toHaveAttribute('aria-current')
    await userEvent.click(screen.getByRole('button', { name: /Sam/ }))

    expect(callsTo(fetch, 'POST /api/identity/profiles/p2/select')).toHaveLength(1)
    // The new profile's name is read again: the screens now show Sam's list and progress.
    expect(await within(await screen.findByRole('banner')).findByText('Sam')).toBeInTheDocument()
  })

  it('shows the server message when a profile cannot be chosen', async () => {
    household({
      'POST /api/identity/profiles/p2/select': () =>
        jsonResponse({ error: 'not_found', message: 'This address does not exist.' }, 404),
    })
    renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: /Sam/ }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This address does not exist.')
    expect(alert).toHaveFocus()
    expect(screen.getByRole('button', { name: /Sam/ })).toBeEnabled()
  })

  it('asks for a first profile when the account has none', async () => {
    household({}, noProfile, [])
    renderApp('/')

    expect(await screen.findByText('This account has no profile yet. Create one to start watching.')).toBeInTheDocument()
    expect(screen.getByLabelText('Name of your first profile')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Profiles' })).not.toBeInTheDocument()
  })

  it('creates a profile and lists it', async () => {
    let profiles = [testProfile]
    const fetch = household({
      'GET /api/identity/profiles': () => jsonResponse(profiles),
      'POST /api/identity/profiles': () => {
        profiles = [testProfile, sam]
        return jsonResponse(sam, 201)
      },
    })
    renderApp('/')

    await userEvent.type(await screen.findByLabelText('New profile'), '  Sam ')
    await userEvent.click(screen.getByRole('button', { name: 'Create the profile' }))

    const notice = await screen.findByRole('status')
    expect(notice).toHaveTextContent('The profile Sam was created. Choose it to start watching.')
    expect(notice).toHaveFocus()
    expect(JSON.parse(String(callsTo(fetch, 'POST /api/identity/profiles')[0][1]?.body))).toEqual({ name: 'Sam' })
    expect(await screen.findByRole('button', { name: /Sam/ })).toBeInTheDocument()
    expect(screen.getByLabelText('New profile')).toHaveValue('')
  })

  it('checks the name before sending it', async () => {
    const fetch = household()
    renderApp('/')

    await userEvent.type(await screen.findByLabelText('New profile'), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Create the profile' }))

    expect(screen.getByText('Enter a name for the profile.')).toBeInTheDocument()
    expect(screen.getByLabelText('New profile')).toHaveFocus()
    expect(callsTo(fetch, 'POST /api/identity/profiles')).toHaveLength(0)
  })

  it('shows on the field why the server refused the name', async () => {
    household({
      'POST /api/identity/profiles': () =>
        jsonResponse({ error: 'profile_name_taken', message: 'A profile with this name already exists.' }, 409),
    })
    renderApp('/')

    const field = await screen.findByLabelText('New profile')
    await userEvent.type(field, 'Sam')
    await userEvent.click(screen.getByRole('button', { name: 'Create the profile' }))

    expect(await screen.findByText('A profile with this name already exists.')).toBeInTheDocument()
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(field).toHaveFocus()
  })

  it('offers no new profile once the account has five', async () => {
    const five = ['A', 'B', 'C', 'D', 'E'].map((name, index) => ({ ...testProfile, id: `p${index + 1}`, name }))
    household({}, noProfile, five)
    renderApp('/')

    expect(await screen.findByText('This account has the maximum of 5 profiles.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create the profile' })).not.toBeInTheDocument()
  })

  it('offers to try again when the profiles cannot be loaded', async () => {
    let up = false
    household({
      'GET /api/identity/profiles': () =>
        up
          ? jsonResponse([testProfile])
          : jsonResponse({ error: 'service_unavailable', message: 'This part of HomeFlix is temporarily unavailable. Please try again in a moment.' }, 503),
    })
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'The profiles cannot be shown' })).toBeInTheDocument()
    up = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('button', { name: /Camille/ })).toBeInTheDocument()
  })

  it('lets the user sign out without choosing a profile', async () => {
    const fetch = household()
    renderApp('/')
    await screen.findByRole('list', { name: 'Profiles' })

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(callsTo(fetch, 'POST /api/identity/auth/sign-out')).toHaveLength(1)
  })
})
