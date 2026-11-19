import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { callsTo, emptyResponse, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const movie = {
  id: 'm1',
  title: 'Night of the Stars',
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
    'POST /api/identity/auth/sign-in': login,
    'GET /api/library/media/m1': () => jsonResponse(movie),
  })
}

async function signInWith(email: string, password: string) {
  await userEvent.type(await screen.findByLabelText('Email address'), email)
  await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('SignInPage', () => {
  it('signs in and goes back to the page that was requested', async () => {
    const fetch = signedOut()
    renderApp('/movies/m1')
    await signInWith('camille@home.example', 'a long passphrase')

    expect(await screen.findByRole('heading', { name: movie.title })).toBeInTheDocument()
    expect(screen.getByText('camille@home.example')).toBeInTheDocument()
    const [[, init]] = callsTo(fetch, 'POST /api/identity/auth/sign-in')
    expect(JSON.parse(String(init?.body))).toEqual({ email: 'camille@home.example', password: 'a long passphrase' })
  })

  it('shows a clear message for wrong credentials and clears the password', async () => {
    signedOut(() =>
      jsonResponse({ error: 'invalid_credentials', message: 'The email or password is incorrect.' }, 401),
    )
    renderApp('/sign-in')
    await signInWith('camille@home.example', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.')
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.getByLabelText('Password')).toHaveFocus()
  })

  it('gives a disabled account the same answer as a wrong password', async () => {
    signedOut(() =>
      jsonResponse({ error: 'invalid_credentials', message: 'The email or password is incorrect.' }, 401),
    )
    renderApp('/sign-in')
    await signInWith('camille@home.example', 'a long passphrase')
    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.')
  })

  it('asks for both fields before sending anything', async () => {
    const fetch = signedOut()
    renderApp('/sign-in')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(screen.getByLabelText('Email address')).toHaveFocus()
    expect(screen.getByLabelText('Email address')).toHaveAccessibleDescription('Enter your email address.')
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Enter your password.')
    expect(callsTo(fetch, 'POST /api/identity/auth/sign-in')).toHaveLength(0)
  })

  it('can be used with the keyboard only', async () => {
    signedOut()
    renderApp('/sign-in')
    await screen.findByLabelText('Email address')

    await userEvent.tab()
    await userEvent.keyboard('camille@home.example')
    await userEvent.tab()
    await userEvent.keyboard('a long passphrase{Enter}')

    expect(await screen.findByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('signs out and says so', async () => {
    const fetch = mockApi(signedInRoutes())
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('You are signed out')
    expect(callsTo(fetch, 'POST /api/identity/auth/sign-out')).toHaveLength(1)
  })

  it('explains that the session expired when it cannot be renewed', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/library/media/m1': () => jsonResponse({}, 401),
      'POST /api/identity/auth/refresh': () => jsonResponse({}, 401),
    })
    renderApp('/movies/m1')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your session has expired')
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
