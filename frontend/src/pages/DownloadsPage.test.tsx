import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Download, Entry } from '../api/acquisition'
import { callsTo, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { renderApp } from '../test/render'
import { REFRESH_MS } from './DownloadsPage'

const sintel: Entry = {
  id: 'e1',
  title: 'Sintel',
  sourceName: 'Blender Foundation',
  sourceUrl: 'https://durian.blender.org/',
  licence: 'Creative Commons Attribution 3.0 Unported (CC BY 3.0)',
  licenceUrl: 'https://creativecommons.org/licenses/by/3.0/',
  attribution: 'Sintel © copyright Blender Foundation | durian.blender.org',
  expectedSizeBytes: 129184608,
  expectedFormat: 'MP4',
}

const tears: Entry = { ...sintel, id: 'e2', title: 'Tears of Steel', attribution: null }

function download(overrides: Partial<Download> & { id: string; entryId: string }): Download {
  return {
    state: 'QUEUED',
    progressPercent: 0,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-11-20T10:00:00Z',
    finishedAt: null,
    ...overrides,
  }
}

function acquisition(routes: Record<string, Route> = {}, account = testAccount) {
  return mockApi({
    ...signedInRoutes(account),
    'GET /api/acquisition/entries': () => jsonResponse([sintel, tears]),
    'GET /api/acquisition/downloads': () => jsonResponse([]),
    ...routes,
  })
}

afterEach(() => {
  vi.useRealTimers()
})

describe('DownloadsPage', () => {
  // ---- Administrators only (ACQ-05) ----

  it('is not offered to an ordinary user, and never calls the acquisition API for one', async () => {
    const fetch = acquisition({}, { ...testAccount, admin: false })
    renderApp('/admin/downloads')

    expect(await screen.findByRole('heading', { name: 'Administrators only' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Administration' })).not.toBeInTheDocument()
    expect(fetch.mock.calls.some(([url]) => String(url).startsWith('/api/acquisition'))).toBe(false)
  })

  it('is a section of the administration area', async () => {
    acquisition()
    renderApp('/admin/downloads')
    const nav = await screen.findByRole('navigation', { name: 'Administration' })
    expect(within(nav).getByRole('link', { name: 'Downloads' })).toHaveAttribute('aria-current', 'page')
  })

  // ---- Verified catalogue (ACQ-01, ACQ-02) ----

  it('shows each catalogue entry with its source, licence, attribution, format and size', async () => {
    acquisition()
    renderApp('/admin/downloads')

    const catalogue = await screen.findByRole('region', { name: 'Verified catalogue' })
    const item = (await within(catalogue).findByRole('heading', { name: 'Sintel' })).closest('li') as HTMLElement
    expect(within(item).getByRole('link', { name: /Blender Foundation/ })).toHaveAttribute(
      'href',
      'https://durian.blender.org/',
    )
    const licence = within(item).getByRole('link', { name: /CC BY 3.0/ })
    expect(licence).toHaveAttribute('href', 'https://creativecommons.org/licenses/by/3.0/')
    expect(licence).toHaveAttribute('rel', 'noopener noreferrer')
    expect(within(item).getByText(/© copyright Blender Foundation/)).toBeInTheDocument()
    expect(within(item).getByText('MP4 · 129.2 MB')).toBeInTheDocument()
    expect(within(item).getByRole('button', { name: 'Download Sintel' })).toBeEnabled()
    // No field for a URL or a magnet link (ACQ-09, ACQ-10).
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('says when the catalogue is empty', async () => {
    acquisition({ 'GET /api/acquisition/entries': () => jsonResponse([]) })
    renderApp('/admin/downloads')
    expect(await screen.findByRole('heading', { name: 'The verified catalogue is empty' })).toBeInTheDocument()
  })

  it('shows the server message when the catalogue cannot be loaded, and tries again', async () => {
    let answer: Route = () =>
      jsonResponse({ error: 'service_unavailable', message: 'This part of HomeFlix is temporarily unavailable.' }, 503)
    acquisition({ 'GET /api/acquisition/entries': (request) => answer(request) })
    renderApp('/admin/downloads')

    const region = await screen.findByRole('region', { name: 'Verified catalogue' })
    expect(await within(region).findByText('This part of HomeFlix is temporarily unavailable.')).toBeInTheDocument()
    answer = () => jsonResponse([sintel])
    await userEvent.click(within(region).getByRole('button', { name: 'Try again' }))
    expect(await within(region).findByRole('heading', { name: 'Sintel' })).toBeInTheDocument()
  })

  // ---- Download states (ACQ-04) ----

  it('shows the four states, the progress, and the reason of a failure as the server wrote it', async () => {
    acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse([
          download({ id: 'd1', entryId: 'e1', state: 'DOWNLOADING', progressPercent: 42 }),
          download({ id: 'd2', entryId: 'e2', state: 'QUEUED' }),
          download({ id: 'd3', entryId: 'e2', state: 'COMPLETED', progressPercent: 100, finishedAt: '2026-11-19T09:00:00Z' }),
          download({
            id: 'd4',
            entryId: 'e1',
            state: 'FAILED',
            errorCode: 'cancelled',
            errorMessage: 'This download was cancelled.',
            finishedAt: '2026-11-18T09:00:00Z',
          }),
        ]),
    })
    renderApp('/admin/downloads')

    const region = await screen.findByRole('region', { name: 'All downloads' })
    const items = await within(region).findAllByRole('listitem')
    expect(items).toHaveLength(4)
    expect(within(items[0]).getByText('Downloading')).toBeInTheDocument()
    expect(within(items[0]).getByRole('progressbar', { name: 'Sintel, progress' })).toHaveAttribute('value', '42')
    expect(within(items[0]).getByText('42 %')).toBeInTheDocument()
    expect(within(items[1]).getByText('Queued')).toBeInTheDocument()
    expect(within(items[2]).getByText('Completed')).toBeInTheDocument()
    expect(within(items[2]).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(within(items[2]).queryByRole('button')).not.toBeInTheDocument()
    expect(within(items[3]).getByText('Failed')).toBeInTheDocument()
    expect(within(items[3]).getByText('This download was cancelled.')).toBeInTheDocument()
    // An entry with an active download cannot be started again, and says why.
    const button = screen.getByRole('button', { name: 'Downloading Sintel' })
    expect(button).toBeDisabled()
    expect(button).toHaveTextContent('Downloading…')
    expect(screen.getByRole('button', { name: 'Downloading Tears of Steel' })).toBeDisabled()
    const entry = within(screen.getByRole('region', { name: 'Verified catalogue' }))
    expect(entry.getAllByText('This title is already downloading. Follow it in All downloads.')).toHaveLength(2)
  })

  it('shows a download at 100 % as finishing, without cancel', async () => {
    acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse([download({ id: 'd1', entryId: 'e1', state: 'DOWNLOADING', progressPercent: 100 })]),
    })
    renderApp('/admin/downloads')

    const region = await screen.findByRole('region', { name: 'All downloads' })
    expect(await within(region).findByText('Finishing')).toBeInTheDocument()
    expect(within(region).getByText('The file is being checked and added to the library.')).toBeInTheDocument()
    expect(within(region).queryByRole('button', { name: /Cancel/ })).not.toBeInTheDocument()
  })

  it('says when there is no download yet', async () => {
    acquisition()
    renderApp('/admin/downloads')
    expect(await screen.findByRole('heading', { name: 'No downloads yet' })).toBeInTheDocument()
  })

  it('refreshes the list while a download is active, keeping it on screen', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let progress = 10
    const fetch = acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse([download({ id: 'd1', entryId: 'e1', state: 'DOWNLOADING', progressPercent: progress })]),
    })
    renderApp('/admin/downloads')
    expect(await screen.findByText('10 %')).toBeInTheDocument()

    progress = 55
    await act(() => vi.advanceTimersByTimeAsync(REFRESH_MS))
    expect(await screen.findByText('55 %')).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/acquisition/downloads').length).toBeGreaterThanOrEqual(2)
  })

  it('does not refresh when nothing is active', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const fetch = acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse([download({ id: 'd1', entryId: 'e1', state: 'COMPLETED', progressPercent: 100 })]),
    })
    renderApp('/admin/downloads')
    expect(await screen.findByText('Completed')).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(REFRESH_MS * 3))
    expect(callsTo(fetch, 'GET /api/acquisition/downloads')).toHaveLength(1)
  })

  // ---- Start, with the rights reminder (ACQ-03, ACQ-11) ----

  it('shows the rights reminder before a download, and starts it only after "I understand"', async () => {
    const fetch = acquisition({
      'POST /api/acquisition/downloads': () => jsonResponse(download({ id: 'd1', entryId: 'e1' }), 201),
    })
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Download Sintel' }))
    const dialog = screen.getByRole('dialog', { name: 'Before you download' })
    expect(dialog).toHaveFocus()
    // Announced with the dialog, so the reminder cannot be skipped.
    expect(dialog).toHaveAccessibleDescription(/pieces of it are automatically shared with other people/)
    expect(within(dialog).getByText(/pieces of it are automatically shared with other people/)).toBeInTheDocument()
    expect(within(dialog).getByText(/Peer-to-peer gives you no rights over a work/)).toBeInTheDocument()
    expect(within(dialog).getByText('This reminder does not replace checking the rights.')).toBeInTheDocument()
    expect(callsTo(fetch, 'POST /api/acquisition/downloads')).toHaveLength(0)

    await user.click(within(dialog).getByRole('button', { name: 'I understand' }))

    const [[, init]] = callsTo(fetch, 'POST /api/acquisition/downloads')
    expect(JSON.parse(String(init?.body))).toEqual({ entryId: 'e1', rightsAcknowledged: true })
    expect(await screen.findByRole('status')).toHaveTextContent('The download of Sintel has started.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(callsTo(fetch, 'GET /api/acquisition/downloads')).toHaveLength(2))
  })

  it('moves focus to the outcome once the download has started and its button is disabled', async () => {
    let started = false
    acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse(started ? [download({ id: 'd1', entryId: 'e1' })] : []),
      'POST /api/acquisition/downloads': () => {
        started = true
        return jsonResponse(download({ id: 'd1', entryId: 'e1' }), 201)
      },
    })
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Download Sintel' }))
    await user.click(screen.getByRole('button', { name: 'I understand' }))

    expect(await screen.findByRole('button', { name: 'Downloading Sintel' })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('The download of Sintel has started.')
    expect(screen.getByRole('status')).toHaveFocus()
  })

  it('starts nothing when the reminder is dismissed, with its button or Escape', async () => {
    const fetch = acquisition()
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    const button = await screen.findByRole('button', { name: 'Download Sintel' })
    await user.click(button)
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(button).toHaveFocus()

    await user.click(button)
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(callsTo(fetch, 'POST /api/acquisition/downloads')).toHaveLength(0)
  })

  it('keeps the keyboard inside the reminder', async () => {
    acquisition()
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Download Sintel' }))
    const dialog = screen.getByRole('dialog')
    await user.tab()
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
    await user.tab()
    expect(within(dialog).getByRole('button', { name: 'I understand' })).toHaveFocus()
    await user.tab()
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('shows the server message when a start is refused', async () => {
    acquisition({
      'POST /api/acquisition/downloads': () =>
        jsonResponse({ error: 'already_in_library', message: 'This title is already in the library.' }, 409),
    })
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Download Sintel' }))
    await user.click(screen.getByRole('button', { name: 'I understand' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This title is already in the library.')
  })

  // ---- Cancel (ACQ-03) ----

  it('asks before cancelling, then cancels and refreshes the list', async () => {
    const fetch = acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse([download({ id: 'd1', entryId: 'e1', state: 'DOWNLOADING', progressPercent: 30 })]),
      'POST /api/acquisition/downloads/d1/cancel': () =>
        jsonResponse(download({ id: 'd1', entryId: 'e1', state: 'FAILED', errorCode: 'cancelled' })),
    })
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Cancel the download of Sintel' }))
    const dialog = screen.getByRole('dialog', { name: 'Cancel this download?' })
    expect(within(dialog).getByText(/the part already downloaded is deleted/)).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Keep downloading' }))
    expect(callsTo(fetch, 'POST /api/acquisition/downloads/d1/cancel')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Cancel the download of Sintel' }))
    await user.click(screen.getByRole('button', { name: 'Cancel download' }))
    expect(callsTo(fetch, 'POST /api/acquisition/downloads/d1/cancel')).toHaveLength(1)
    expect(await screen.findByRole('status')).toHaveTextContent('The download of Sintel was cancelled.')
  })

  it('moves focus to the outcome once the download is cancelled and its button is gone', async () => {
    let cancelled = false
    acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse([
          download({ id: 'd1', entryId: 'e1', ...(cancelled ? { state: 'FAILED', errorCode: 'cancelled' } : { state: 'DOWNLOADING' }) }),
        ]),
      'POST /api/acquisition/downloads/d1/cancel': () => {
        cancelled = true
        return jsonResponse(download({ id: 'd1', entryId: 'e1', state: 'FAILED', errorCode: 'cancelled' }))
      },
    })
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Cancel the download of Sintel' }))
    await user.click(screen.getByRole('button', { name: 'Cancel download' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Cancel the download of Sintel' })).not.toBeInTheDocument())
    expect(screen.getByRole('status')).toHaveTextContent('The download of Sintel was cancelled.')
    expect(screen.getByRole('status')).toHaveFocus()
  })

  it('shows the server message when a cancel is refused', async () => {
    acquisition({
      'GET /api/acquisition/downloads': () =>
        jsonResponse([download({ id: 'd1', entryId: 'e1', state: 'DOWNLOADING', progressPercent: 30 })]),
      'POST /api/acquisition/downloads/d1/cancel': () =>
        jsonResponse(
          { error: 'download_not_cancellable', message: 'This download has already finished and cannot be cancelled.' },
          409,
        ),
    })
    renderApp('/admin/downloads')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Cancel the download of Sintel' }))
    await user.click(screen.getByRole('button', { name: 'Cancel download' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This download has already finished and cannot be cancelled.',
    )
  })
})
