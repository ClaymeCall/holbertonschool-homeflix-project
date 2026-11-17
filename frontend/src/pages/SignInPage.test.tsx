import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { callsTo, emptyResponse, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const movie = {
  id: 'm1',
  title: 'La Nuit des Étoiles',
  description: null,
  posterUrl: null,
  genre: null,
  durationSeconds: null,
  availability: 'AVAILABLE',
}

/** A household where setup is done and nobody is signed in yet. */
function signedOut(login: Route = () => jsonResponse(testAccount)) {
  return mockApi({
    ...signedInRoutes(),
    'GET /api/identity/auth/me': () => jsonResponse({}, 401),
    'POST /api/identity/auth/login': login,
    'GET /api/library/media/m1': () => jsonResponse(movie),
  })
}

async function signInWith(username: string, password: string) {
  await userEvent.type(await screen.findByLabelText("Nom d'utilisateur"), username)
  await userEvent.type(screen.getByLabelText('Mot de passe'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }))
}

describe('SignInPage', () => {
  it('signs in and goes back to the page that was requested', async () => {
    const fetch = signedOut()
    renderApp('/movies/m1')
    await signInWith('camille', 'une phrase de passe')

    expect(await screen.findByRole('heading', { name: movie.title })).toBeInTheDocument()
    expect(screen.getByText('camille')).toBeInTheDocument()
    const [[, init]] = callsTo(fetch, 'POST /api/identity/auth/login')
    expect(JSON.parse(String(init?.body))).toEqual({ username: 'camille', password: 'une phrase de passe' })
  })

  it('shows a clear message for wrong credentials and clears the password', async () => {
    signedOut(() => jsonResponse({ code: 'BAD_CREDENTIALS' }, 401))
    renderApp('/sign-in')
    await signInWith('camille', 'mauvais')

    expect(await screen.findByRole('alert')).toHaveTextContent("Nom d'utilisateur ou mot de passe incorrect.")
    expect(screen.getByLabelText('Mot de passe')).toHaveValue('')
    expect(screen.getByLabelText('Mot de passe')).toHaveFocus()
  })

  it('explains that a disabled account cannot sign in', async () => {
    signedOut(() => jsonResponse({ code: 'ACCOUNT_DISABLED' }, 403))
    renderApp('/sign-in')
    await signInWith('camille', 'une phrase de passe')
    expect(await screen.findByRole('alert')).toHaveTextContent('Ce compte est désactivé')
  })

  it('asks for both fields before sending anything', async () => {
    const fetch = signedOut()
    renderApp('/sign-in')
    await userEvent.click(await screen.findByRole('button', { name: 'Se connecter' }))

    expect(screen.getByLabelText("Nom d'utilisateur")).toHaveFocus()
    expect(screen.getByLabelText("Nom d'utilisateur")).toHaveAccessibleDescription("Saisissez votre nom d'utilisateur.")
    expect(screen.getByLabelText('Mot de passe')).toHaveAccessibleDescription('Saisissez votre mot de passe.')
    expect(callsTo(fetch, 'POST /api/identity/auth/login')).toHaveLength(0)
  })

  it('can be used with the keyboard only', async () => {
    signedOut()
    renderApp('/sign-in')
    await screen.findByLabelText("Nom d'utilisateur")

    await userEvent.tab()
    await userEvent.keyboard('camille')
    await userEvent.tab()
    await userEvent.keyboard('une phrase de passe{Enter}')

    expect(await screen.findByRole('button', { name: 'Se déconnecter' })).toBeInTheDocument()
  })

  it('signs out and says so', async () => {
    const fetch = mockApi(signedInRoutes())
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: 'Se déconnecter' }))

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Vous êtes déconnecté')
    expect(callsTo(fetch, 'POST /api/identity/auth/logout')).toHaveLength(1)
  })

  it('explains that the session expired when it cannot be renewed', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/library/media/m1': () => jsonResponse({}, 401),
      'POST /api/identity/auth/refresh': () => jsonResponse({}, 401),
    })
    renderApp('/movies/m1')

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Votre session a expiré')
  })

  it('renews the session silently when it can', async () => {
    let renewed = false
    mockApi({
      ...signedInRoutes(),
      'GET /api/library/media/m1': () => (renewed ? jsonResponse(movie) : jsonResponse({}, 401)),
      'POST /api/identity/auth/refresh': () => {
        renewed = true
        return emptyResponse()
      },
    })
    renderApp('/movies/m1')

    expect(await screen.findByRole('heading', { name: movie.title })).toBeInTheDocument()
  })
})
