import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { callsTo, emptyResponse, jsonResponse, mockApi, signedInRoutes } from '../test/http'
import { renderApp } from '../test/render'

function firstRun(setupRoute = () => emptyResponse(201)) {
  let required = true
  return mockApi({
    'GET /api/identity/setup': () => jsonResponse({ required }),
    'POST /api/identity/setup': () => {
      required = false
      return setupRoute()
    },
    'GET /api/identity/auth/me': () => jsonResponse({}, 401),
  })
}

async function fillSetup(username: string, password: string, confirmation = password) {
  await userEvent.type(await screen.findByLabelText("Nom d'utilisateur"), username)
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
    await fillSetup('camille', 'une phrase de passe')
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Le compte administrateur est créé')
    const [[, init]] = callsTo(fetch, 'POST /api/identity/setup')
    expect(JSON.parse(String(init?.body))).toEqual({ username: 'camille', password: 'une phrase de passe' })
  })

  it('explains invalid input and focuses the first invalid field', async () => {
    const fetch = firstRun()
    renderApp('/setup')
    await fillSetup('c', 'court', 'autre')
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    const username = screen.getByLabelText("Nom d'utilisateur")
    expect(username).toHaveFocus()
    expect(username).toHaveAttribute('aria-invalid', 'true')
    expect(username).toHaveAccessibleDescription(/3 à 32 caractères/)
    expect(screen.getByLabelText('Mot de passe')).toHaveAccessibleDescription(/au moins 12 caractères/)
    expect(screen.getByLabelText('Confirmer le mot de passe')).toHaveAccessibleDescription(/ne correspondent pas/)
    expect(callsTo(fetch, 'POST /api/identity/setup')).toHaveLength(0)
  })

  it('tells the user when an administrator was created in the meantime', async () => {
    firstRun(() => jsonResponse({ code: 'SETUP_DONE' }, 409))
    renderApp('/setup')
    await fillSetup('camille', 'une phrase de passe')
    await userEvent.click(screen.getByRole('button', { name: 'Créer le compte administrateur' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Un administrateur existe déjà')
    await userEvent.click(screen.getByRole('button', { name: 'Aller à la connexion' }))
    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  })

  it('can be completed with the keyboard only', async () => {
    firstRun()
    renderApp('/setup')
    await screen.findByLabelText("Nom d'utilisateur")

    await userEvent.tab()
    await userEvent.keyboard('camille')
    await userEvent.tab()
    await userEvent.keyboard('une phrase de passe')
    await userEvent.tab()
    await userEvent.keyboard('une phrase de passe{Enter}')

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  })
})
