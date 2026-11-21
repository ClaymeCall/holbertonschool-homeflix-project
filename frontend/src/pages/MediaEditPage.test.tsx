import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { MediaDetails } from '../api/library'
import { callsTo, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { media } from '../test/fixtures'
import { renderApp } from '../test/render'

const heat: MediaDetails = {
  ...media({ id: 'm2', title: 'heat.1995' }),
  genres: ['Crime'],
  durationSeconds: 10235,
  description: null,
}

function library(routes: Record<string, Route> = {}, account = testAccount) {
  return mockApi({
    ...signedInRoutes(account),
    'GET /api/library/media/m2': () => jsonResponse(heat),
    ...routes,
  })
}

function sent(fetch: ReturnType<typeof mockApi>) {
  return JSON.parse(String(callsTo(fetch, 'PUT /api/library/media/m2')[0][1]?.body))
}

describe('MediaEditPage', () => {
  it('shows the current information of the title', async () => {
    library()
    renderApp('/admin/library/media/m2')

    expect(await screen.findByLabelText('Title')).toHaveValue('heat.1995')
    expect(screen.getByLabelText('Genres')).toHaveValue('Crime')
    expect(screen.getByLabelText('Duration in minutes')).toHaveValue('171')
    expect(screen.getByLabelText('Description')).toHaveValue('')
  })

  it('saves every field in the shape of the contract', async () => {
    const fetch = library({
      'PUT /api/library/media/m2': ({ init }) => jsonResponse({ ...heat, ...JSON.parse(String(init?.body)) }),
    })
    renderApp('/admin/library/media/m2')

    const title = await screen.findByLabelText('Title')
    await userEvent.clear(title)
    await userEvent.type(title, ' Heat ')
    await userEvent.type(screen.getByLabelText('Description'), 'A thief and a cop.')
    await userEvent.clear(screen.getByLabelText('Genres'))
    await userEvent.type(screen.getByLabelText('Genres'), 'Crime, Drama, ')
    await userEvent.type(screen.getByLabelText('Poster address'), 'https://images.example.org/heat.jpg')
    await userEvent.click(screen.getByRole('button', { name: 'Save the changes' }))

    expect(await screen.findByRole('status')).toHaveTextContent('The changes were saved.')
    expect(sent(fetch)).toEqual({
      title: 'Heat',
      description: 'A thief and a cop.',
      genres: ['Crime', 'Drama'],
      // Unchanged: the seconds are kept, not rounded to 171 minutes.
      durationSeconds: 10235,
      posterUrl: 'https://images.example.org/heat.jpg',
    })
    expect(await screen.findByRole('heading', { level: 1, name: 'Heat' })).toBeInTheDocument()
  })

  it('sends a changed duration in seconds, and an emptied field as null', async () => {
    const fetch = library({ 'PUT /api/library/media/m2': () => jsonResponse(heat) })
    renderApp('/admin/library/media/m2')

    const duration = await screen.findByLabelText('Duration in minutes')
    await userEvent.clear(duration)
    await userEvent.type(duration, '170')
    await userEvent.clear(screen.getByLabelText('Genres'))
    await userEvent.click(screen.getByRole('button', { name: 'Save the changes' }))

    await screen.findByRole('status')
    expect(sent(fetch)).toMatchObject({ durationSeconds: 10200, genres: [], description: null, posterUrl: null })
  })

  it('checks the title and the duration before sending', async () => {
    const fetch = library()
    renderApp('/admin/library/media/m2')

    await userEvent.clear(await screen.findByLabelText('Title'))
    await userEvent.clear(screen.getByLabelText('Duration in minutes'))
    await userEvent.type(screen.getByLabelText('Duration in minutes'), '2h50')
    await userEvent.click(screen.getByRole('button', { name: 'Save the changes' }))

    expect(screen.getByText('Enter a title.')).toBeInTheDocument()
    expect(screen.getByText('Enter the duration in whole minutes, for example 95.')).toBeInTheDocument()
    expect(screen.getByLabelText('Title')).toHaveFocus()
    expect(callsTo(fetch, 'PUT /api/library/media/m2')).toHaveLength(0)
  })

  it('shows the server message on the field it refused', async () => {
    library({
      'PUT /api/library/media/m2': () =>
        jsonResponse(
          { error: 'validation_failed', message: 'Some fields are invalid.', fields: { posterUrl: 'The poster must be an https address.' } },
          400,
        ),
    })
    renderApp('/admin/library/media/m2')

    const poster = await screen.findByLabelText('Poster address')
    await userEvent.type(poster, 'http://images.example.org/heat.jpg')
    await userEvent.click(screen.getByRole('button', { name: 'Save the changes' }))

    expect(await screen.findByText('The poster must be an https address.')).toBeInTheDocument()
    expect(poster).toHaveFocus()
  })

  it('says so when the title is no longer in the catalogue', async () => {
    library({
      'GET /api/library/media/m2': () => jsonResponse({ error: 'not_found', message: 'This address does not exist.' }, 404),
    })
    renderApp('/admin/library/media/m2')

    expect(await screen.findByRole('heading', { name: 'Title not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the library' })).toHaveAttribute('href', '/admin/library')
  })

  it('is for administrators only', async () => {
    const fetch = library({}, { ...testAccount, admin: false })
    renderApp('/admin/library/media/m2')

    expect(await screen.findByRole('heading', { name: 'Administrators only' })).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/library/media/m2')).toHaveLength(0)
  })
})
