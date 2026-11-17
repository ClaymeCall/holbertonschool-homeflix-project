import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { callsTo, jsonResponse, mockApi, signedInRoutes } from '../test/http'
import { renderApp } from '../test/render'

const created = { id: 'a1', email: 'camille@home.example', admin: true }

function firstRun(setupRoute: () => Response = () => jsonResponse(created, 201)) {
  let setupRequired = true
  return mockApi({
    'GET /api/identity/setup/status': () => jsonResponse({ setupRequired }),
    'POST /api/identity/setup/admin': () => {
      const response = setupRoute()
      if (response.status === 201 || response.status === 409) setupRequired = false
      return response
    },
    'GET /api/identity/auth/me': () => jsonResponse({}, 401),
  })
}

async function fillSetup(email: string, password: string, confirmation = password) {
  await userEvent.type(await screen.findByLabelText('Email address'), email)
  await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.type(screen.getByLabelText('Confirm password'), confirmation)
}

describe('SetupPage', () => {
  it('is shown on first start, whatever page was requested', async () => {
    firstRun()
    renderApp('/movies/m1')
    expect(await screen.findByRole('heading', { name: 'Welcome to HomeFlix' })).toBeInTheDocument()
  })

  it('is not reachable once an administrator exists', async () => {
    mockApi({ ...signedInRoutes(), 'GET /api/identity/auth/me': () => jsonResponse({}, 401) })
    renderApp('/setup')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('creates the first administrator, then asks to sign in', async () => {
    const fetch = firstRun()
    renderApp('/setup')
    await fillSetup('  camille@home.example ', 'a long passphrase')
    await userEvent.click(screen.getByRole('button', { name: 'Create the administrator account' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('The administrator account has been created')
    const [[, init]] = callsTo(fetch, 'POST /api/identity/setup/admin')
    expect(JSON.parse(String(init?.body))).toEqual({ email: 'camille@home.example', password: 'a long passphrase' })
  })

  it('explains invalid input and focuses the first invalid field', async () => {
    const fetch = firstRun()
    renderApp('/setup')
    await fillSetup('camille', 'court', 'autre')
    await userEvent.click(screen.getByRole('button', { name: 'Create the administrator account' }))

    const email = screen.getByLabelText('Email address')
    expect(email).toHaveFocus()
    expect(email).toHaveAttribute('aria-invalid', 'true')
    expect(email).toHaveAccessibleDescription(/valid email address/)
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/at least 12 characters/)
    expect(screen.getByLabelText('Confirm password')).toHaveAccessibleDescription(/do not match/)
    expect(callsTo(fetch, 'POST /api/identity/setup/admin')).toHaveLength(0)
  })

  it('refuses a password longer than 72 bytes, as the server does', async () => {
    const fetch = firstRun()
    renderApp('/setup')
    const long = 'é'.repeat(37) // 37 characters, 74 bytes in UTF-8
    await fillSetup('camille@home.example', long)
    await userEvent.click(screen.getByRole('button', { name: 'Create the administrator account' }))

    expect(screen.getByLabelText('Password')).toHaveFocus()
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/72 bytes at most/)
    expect(callsTo(fetch, 'POST /api/identity/setup/admin')).toHaveLength(0)
  })

  it('explains the fields rejected by the server in its own words, never the server message', async () => {
    firstRun(() =>
      jsonResponse(
        { error: 'validation_failed', message: 'Some fields are invalid.', fields: { email: 'must be a well-formed email address' } },
        400,
      ),
    )
    renderApp('/setup')
    await fillSetup('camille@home', 'a long passphrase')
    await userEvent.click(screen.getByRole('button', { name: 'Create the administrator account' }))

    const email = await screen.findByLabelText('Email address')
    await waitFor(() => expect(email).toHaveFocus())
    expect(email).toHaveAccessibleDescription(/valid email address/)
    expect(screen.queryByText(/well-formed/)).not.toBeInTheDocument()
  })

  it('tells the user when an administrator was created in the meantime', async () => {
    firstRun(() =>
      jsonResponse({ error: 'setup_already_completed', message: 'HomeFlix is already set up. Sign in instead.' }, 409),
    )
    renderApp('/setup')
    await fillSetup('camille@home.example', 'a long passphrase')
    await userEvent.click(screen.getByRole('button', { name: 'Create the administrator account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('An administrator already exists')
    await userEvent.click(screen.getByRole('button', { name: 'Go to sign-in' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('can be completed with the keyboard only', async () => {
    firstRun()
    renderApp('/setup')
    await screen.findByLabelText('Email address')

    await userEvent.tab()
    await userEvent.keyboard('camille@home.example')
    await userEvent.tab()
    await userEvent.keyboard('a long passphrase')
    await userEvent.tab()
    await userEvent.keyboard('a long passphrase{Enter}')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
