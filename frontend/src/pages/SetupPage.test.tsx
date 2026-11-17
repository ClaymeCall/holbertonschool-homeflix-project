import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { callsTo, jsonResponse, mockApi, signedInRoutes } from '../test/http'
import { renderApp } from '../test/render'

const created = { id: 'a1', email: 'camille@maison.fr', admin: true }

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
  await userEvent.type(await screen.findByLabelText('Adresse e-mail'), email)
  await userEvent.type(screen.getByLabelText('Mot de passe'), password)
  await userEvent.type(screen.getByLabelText('Confirmer le mot de passe'), confirmation)
}

describe('SetupPage', () => {
  it('is shown on first start, whatever page was requested', async () => {
    firstRun()
    renderApp('/movies/m1')
    expect(await screen.findByRole('heading', { name: 'Bienvenue sur HomeFlix' })).toBeInTheDocument()
  })

  it('is not reachable once an administrator exists', async () => {
    mockApi({ ...signedInRoutes(), 'GET /api/identity/auth/me': () => jsonResponse({}, 401) })
    renderApp('/setup')
    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  })

  it('creates the first administrator, then asks to sign in', async () => {
    const fetch = firstRun()
    renderApp('/setup')
    await fillSetup('  camille@maison.fr ', 'une phrase de passe')
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Le compte administrateur est créé')
    const [[, init]] = callsTo(fetch, 'POST /api/identity/setup/admin')
    expect(JSON.parse(String(init?.body))).toEqual({ email: 'camille@maison.fr', password: 'une phrase de passe' })
  })

  it('explains invalid input and focuses the first invalid field', async () => {
    const fetch = firstRun()
    renderApp('/setup')
    await fillSetup('camille', 'court', 'autre')
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    const email = screen.getByLabelText('Adresse e-mail')
    expect(email).toHaveFocus()
    expect(email).toHaveAttribute('aria-invalid', 'true')
    expect(email).toHaveAccessibleDescription(/adresse e-mail valide/)
    expect(screen.getByLabelText('Mot de passe')).toHaveAccessibleDescription(/au moins 12 caractères/)
    expect(screen.getByLabelText('Confirmer le mot de passe')).toHaveAccessibleDescription(/ne correspondent pas/)
    expect(callsTo(fetch, 'POST /api/identity/setup/admin')).toHaveLength(0)
  })

  it('refuses a password longer than 72 bytes, as the server does', async () => {
    const fetch = firstRun()
    renderApp('/setup')
    const long = 'é'.repeat(37) // 37 characters, 74 bytes in UTF-8
    await fillSetup('camille@maison.fr', long)
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    expect(screen.getByLabelText('Mot de passe')).toHaveFocus()
    expect(screen.getByLabelText('Mot de passe')).toHaveAccessibleDescription(/72 octets au maximum/)
    expect(callsTo(fetch, 'POST /api/identity/setup/admin')).toHaveLength(0)
  })

  it('shows the fields rejected by the server in French, never its own message', async () => {
    firstRun(() =>
      jsonResponse(
        { error: 'validation_failed', message: 'Some fields are invalid.', fields: { email: 'Enter a valid email address.' } },
        400,
      ),
    )
    renderApp('/setup')
    await fillSetup('camille@maison', 'une phrase de passe')
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    const email = await screen.findByLabelText('Adresse e-mail')
    await waitFor(() => expect(email).toHaveFocus())
    expect(email).toHaveAccessibleDescription(/adresse e-mail valide/)
    expect(screen.queryByText(/Enter a valid email/)).not.toBeInTheDocument()
  })

  it('tells the user when an administrator was created in the meantime', async () => {
    firstRun(() =>
      jsonResponse({ error: 'setup_already_completed', message: 'HomeFlix is already set up. Sign in instead.' }, 409),
    )
    renderApp('/setup')
    await fillSetup('camille@maison.fr', 'une phrase de passe')
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Un administrateur existe déjà')
    await userEvent.click(screen.getByRole('button', { name: 'Aller à la connexion' }))
    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  })

  it('can be completed with the keyboard only', async () => {
    firstRun()
    renderApp('/setup')
    await screen.findByLabelText('Adresse e-mail')

    await userEvent.tab()
    await userEvent.keyboard('camille@maison.fr')
    await userEvent.tab()
    await userEvent.keyboard('une phrase de passe')
    await userEvent.tab()
    await userEvent.keyboard('une phrase de passe{Enter}')

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  })
})
