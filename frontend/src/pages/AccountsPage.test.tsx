import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ManagedAccount } from '../api/identity'
import { callsTo, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'

const me: ManagedAccount = { ...testAccount, disabled: false, createdAt: '2026-11-01T09:00:00Z' }
const sam: ManagedAccount = {
  id: 'a2',
  email: 'sam@home.example',
  admin: false,
  disabled: false,
  createdAt: '2026-11-02T09:00:00Z',
}

function identity(routes: Record<string, Route> = {}, accounts: ManagedAccount[] = [me, sam]) {
  return mockApi({
    ...signedInRoutes(),
    'GET /api/identity/accounts': () => jsonResponse(accounts),
    ...routes,
  })
}

async function accountItem(email: string) {
  const region = await screen.findByRole('region', { name: 'Household accounts' })
  return (await within(region).findByRole('heading', { name: new RegExp(email) })).closest('li') as HTMLElement
}

function bodyOf(fetch: ReturnType<typeof mockApi>, key: string) {
  return JSON.parse(String(callsTo(fetch, key)[0][1]?.body))
}

describe('AccountsPage', () => {
  it('lists every account with its role, whether it is disabled, and marks your own', async () => {
    identity({}, [me, { ...sam, disabled: true }])
    renderApp('/admin/accounts')

    const own = await accountItem('camille@home.example')
    expect(within(own).getByText('(you)')).toBeInTheDocument()
    expect(within(own).getByText('Administrator')).toBeInTheDocument()
    expect(within(own).queryByRole('button')).not.toBeInTheDocument()

    const other = await accountItem('sam@home.example')
    expect(within(other).getByText('User')).toBeInTheDocument()
    expect(within(other).getByText('Disabled')).toBeInTheDocument()
    expect(within(other).getByRole('button', { name: 'Enable sam@home.example' })).toBeInTheDocument()
  })

  it('reads every page of accounts', async () => {
    const page = Array.from({ length: 100 }, (_, index) => ({ ...sam, id: `p${index}`, email: `p${index}@home.example` }))
    const fetch = identity({
      'GET /api/identity/accounts': ({ url }) => jsonResponse(url.searchParams.get('offset') === '0' ? page : [sam]),
    })
    renderApp('/admin/accounts')

    expect(await accountItem('sam@home.example')).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/identity/accounts').map(([url]) => url)).toEqual([
      '/api/identity/accounts?limit=100&offset=0',
      '/api/identity/accounts?limit=100&offset=100',
    ])
  })

  it('makes a user an administrator and shows the list again', async () => {
    let accounts = [me, sam]
    const fetch = identity({
      'GET /api/identity/accounts': () => jsonResponse(accounts),
      'PUT /api/identity/accounts/a2/role': () => {
        accounts = [me, { ...sam, admin: true }]
        return jsonResponse(accounts[1])
      },
    })
    renderApp('/admin/accounts')

    await userEvent.click(
      within(await accountItem('sam@home.example')).getByRole('button', { name: 'Make sam@home.example an administrator' }),
    )

    expect(await screen.findByRole('status')).toHaveTextContent('sam@home.example is now an administrator.')
    expect(bodyOf(fetch, 'PUT /api/identity/accounts/a2/role')).toEqual({ admin: true })
    expect(await within(await accountItem('sam@home.example')).findByText('Administrator')).toBeInTheDocument()
    // The button was disabled and the list loaded again: focus is on the outcome, not lost.
    expect(screen.getByRole('status')).toHaveFocus()
  })

  it('disables an account', async () => {
    const fetch = identity({ 'POST /api/identity/accounts/a2/disable': () => jsonResponse({ ...sam, disabled: true }) })
    renderApp('/admin/accounts')

    await userEvent.click(within(await accountItem('sam@home.example')).getByRole('button', { name: 'Disable sam@home.example' }))

    expect(await screen.findByRole('status')).toHaveTextContent('sam@home.example can no longer sign in.')
    expect(callsTo(fetch, 'POST /api/identity/accounts/a2/disable')).toHaveLength(1)
  })

  it('shows the server message when the change would leave no administrator', async () => {
    identity({
      'PUT /api/identity/accounts/a2/role': () =>
        jsonResponse({ error: 'last_administrator', message: 'HomeFlix needs at least one enabled administrator.' }, 409),
    }, [me, { ...sam, admin: true }])
    renderApp('/admin/accounts')

    await userEvent.click(within(await accountItem('sam@home.example')).getByRole('button', { name: 'Make sam@home.example a user' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('HomeFlix needs at least one enabled administrator.')
  })

  it('creates an account with the chosen role', async () => {
    const fetch = identity({
      'POST /api/identity/accounts': () =>
        jsonResponse({ id: 'a3', email: 'lou@home.example', admin: true, disabled: false, createdAt: '2026-11-21T09:00:00Z' }, 201),
    })
    renderApp('/admin/accounts')
    const form = await screen.findByRole('region', { name: 'Create an account' })

    await userEvent.type(within(form).getByLabelText('Email address'), ' lou@home.example ')
    await userEvent.type(within(form).getByLabelText('Password'), 'a long household phrase')
    await userEvent.type(within(form).getByLabelText('Confirm password'), 'a long household phrase')
    await userEvent.click(within(form).getByRole('checkbox', { name: /Administrator/ }))
    await userEvent.click(within(form).getByRole('button', { name: 'Create the account' }))

    expect(await screen.findByRole('status')).toHaveTextContent('The account lou@home.example was created.')
    expect(bodyOf(fetch, 'POST /api/identity/accounts')).toEqual({
      email: 'lou@home.example',
      password: 'a long household phrase',
      admin: true,
    })
    expect(within(form).getByLabelText('Email address')).toHaveValue('')
    expect(callsTo(fetch, 'GET /api/identity/accounts')).toHaveLength(2)
  })

  it('checks the form before sending it', async () => {
    const fetch = identity()
    renderApp('/admin/accounts')
    const form = await screen.findByRole('region', { name: 'Create an account' })

    await userEvent.type(within(form).getByLabelText('Email address'), 'lou@home.example')
    await userEvent.type(within(form).getByLabelText('Password'), 'short')
    await userEvent.click(within(form).getByRole('button', { name: 'Create the account' }))

    expect(within(form).getByLabelText('Password')).toHaveFocus()
    expect(within(form).getByText('The password must be at least 12 characters long.')).toBeInTheDocument()
    expect(callsTo(fetch, 'POST /api/identity/accounts')).toHaveLength(0)
  })

  it('shows the server message on the email field when the address is already used', async () => {
    identity({
      'POST /api/identity/accounts': () =>
        jsonResponse(
          {
            error: 'validation_failed',
            message: 'Some fields are invalid.',
            fields: { email: 'This email address is already used by another account.' },
          },
          400,
        ),
    })
    renderApp('/admin/accounts')
    const form = await screen.findByRole('region', { name: 'Create an account' })

    await userEvent.type(within(form).getByLabelText('Email address'), 'sam@home.example')
    await userEvent.type(within(form).getByLabelText('Password'), 'a long household phrase')
    await userEvent.type(within(form).getByLabelText('Confirm password'), 'a long household phrase')
    await userEvent.click(within(form).getByRole('button', { name: 'Create the account' }))

    expect(await within(form).findByText('This email address is already used by another account.')).toBeInTheDocument()
    expect(within(form).getByLabelText('Email address')).toHaveFocus()
  })

  it('is for administrators only', async () => {
    const fetch = mockApi({ ...signedInRoutes({ ...testAccount, admin: false }) })
    renderApp('/admin/accounts')

    expect(await screen.findByRole('heading', { name: 'Administrators only' })).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/identity/accounts')).toHaveLength(0)
  })

  it('offers to try again when the accounts cannot be loaded', async () => {
    let up = false
    identity({
      'GET /api/identity/accounts': () =>
        up
          ? jsonResponse([me, sam])
          : jsonResponse({ error: 'service_unavailable', message: 'This part of HomeFlix is temporarily unavailable. Please try again in a moment.' }, 503),
    })
    renderApp('/admin/accounts')

    expect(await screen.findByRole('heading', { name: 'The accounts cannot be shown' })).toBeInTheDocument()
    up = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await accountItem('sam@home.example')).toBeInTheDocument()
  })
})
