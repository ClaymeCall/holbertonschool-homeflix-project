import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Root, ScanReport } from '../api/library'
import { callsTo, emptyResponse, jsonResponse, mockApi, signedInRoutes, testAccount, type Route } from '../test/http'
import { media } from '../test/fixtures'
import { renderApp } from '../test/render'

const films: Root = { id: 'r1', path: '/media/library/films', createdAt: '2026-11-10T09:00:00Z' }
const sintel = media({ id: 'm1', title: 'Sintel' })
const heat = media({ id: 'm2', title: 'Heat', availability: 'UNAVAILABLE' })

const report: ScanReport = {
  startedAt: '2026-11-21T09:00:00Z',
  finishedAt: '2026-11-21T09:00:05Z',
  added: 2,
  updated: 1,
  markedUnavailable: 0,
  unsupported: [{ rootId: 'r1', relativePath: 'old/clip.avi', error: 'format_not_supported' }],
  inaccessibleRoots: [{ rootId: 'r1', error: 'directory_not_readable' }],
}

function library(routes: Record<string, Route> = {}) {
  return mockApi({
    ...signedInRoutes(),
    'GET /api/library/roots': () => jsonResponse([films]),
    'GET /api/library/media': () => jsonResponse([heat, sintel]),
    ...routes,
  })
}

function bodyOf(fetch: ReturnType<typeof mockApi>, key: string) {
  return JSON.parse(String(callsTo(fetch, key)[0][1]?.body))
}

describe('LibraryPage', () => {
  it('lists the media folders and every title with its availability and an edit link', async () => {
    const fetch = library()
    renderApp('/admin/library')

    const folders = await screen.findByRole('list', { name: 'Media folders' })
    expect(within(folders).getByRole('heading', { name: '/media/library/films' })).toBeInTheDocument()

    const titles = await screen.findByRole('list', { name: 'Titles' })
    const heatRow = within(titles).getByText('Heat').closest('li') as HTMLElement
    expect(within(heatRow).getByText('Unavailable')).toBeInTheDocument()
    expect(within(heatRow).getByRole('link', { name: 'Edit Heat' })).toHaveAttribute('href', '/admin/library/media/m2')
    expect(callsTo(fetch, 'GET /api/library/media')[0][0]).toBe('/api/library/media?sort=title&limit=100&offset=0')
  })

  it('explains what to do when there is no folder and no title yet', async () => {
    library({ 'GET /api/library/roots': () => jsonResponse([]), 'GET /api/library/media': () => jsonResponse([]) })
    renderApp('/admin/library')

    expect(await screen.findByRole('heading', { name: 'No media folder yet' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'No titles yet' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Scan the folders' })).toBeDisabled()
  })

  it('adds a folder', async () => {
    let roots = [films]
    const added: Root = { id: 'r2', path: '/media/downloads', createdAt: '2026-11-21T09:00:00Z' }
    const fetch = library({
      'GET /api/library/roots': () => jsonResponse(roots),
      'POST /api/library/roots': () => {
        roots = [films, added]
        return jsonResponse(added, 201)
      },
    })
    renderApp('/admin/library')

    await userEvent.type(await screen.findByLabelText('Folder to add'), ' /media/downloads ')
    await userEvent.click(screen.getByRole('button', { name: 'Add the folder' }))

    expect(await screen.findByRole('status')).toHaveTextContent('/media/downloads was added.')
    expect(bodyOf(fetch, 'POST /api/library/roots')).toEqual({ path: '/media/downloads' })
    expect(await screen.findByRole('heading', { name: '/media/downloads' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveFocus()
  })

  it('shows on the field why the server refused a folder', async () => {
    library({
      'POST /api/library/roots': () =>
        jsonResponse({ error: 'directory_outside_library', message: 'This folder is not inside the folders shared with HomeFlix.' }, 400),
    })
    renderApp('/admin/library')

    const field = await screen.findByLabelText('Folder to add')
    await userEvent.type(field, '/etc')
    await userEvent.click(screen.getByRole('button', { name: 'Add the folder' }))

    expect(await screen.findByText('This folder is not inside the folders shared with HomeFlix.')).toBeInTheDocument()
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(field).toHaveFocus()
  })

  it('removes a folder only after confirmation', async () => {
    const fetch = library({ 'DELETE /api/library/roots/r1': () => emptyResponse() })
    renderApp('/admin/library')

    await userEvent.click(await screen.findByRole('button', { name: 'Remove /media/library/films' }))
    const dialog = screen.getByRole('dialog', { name: 'Remove this folder?' })
    expect(dialog).toHaveTextContent('No file is deleted.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Keep the folder' }))
    expect(callsTo(fetch, 'DELETE /api/library/roots/r1')).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: 'Remove /media/library/films' }))
    await userEvent.click(screen.getByRole('button', { name: 'Remove the folder' }))

    expect(await screen.findByRole('status')).toHaveTextContent('/media/library/films was removed.')
    expect(callsTo(fetch, 'DELETE /api/library/roots/r1')).toHaveLength(1)
    // Its button is gone with the folder: focus is on the outcome, not lost.
    expect(screen.getByRole('status')).toHaveFocus()
  })

  it('scans the folders and shows the report, then the titles again', async () => {
    const fetch = library({ 'POST /api/library/scans': () => jsonResponse(report) })
    renderApp('/admin/library')

    await userEvent.click(await screen.findByRole('button', { name: 'Scan the folders' }))

    expect(await screen.findByText(/Scan finished: 2 titles added, 1 title seen again, 0 titles marked unavailable/)).toBeInTheDocument()
    expect(screen.getByText('/media/library/films: HomeFlix is not allowed to read this folder.')).toBeInTheDocument()
    expect(screen.getByText('/media/library/films/old/clip.avi')).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/library/media')).toHaveLength(2)
    // The scan button was disabled while scanning: focus is on the report.
    expect(screen.getByText(/Scan finished/).parentElement).toHaveFocus()
  })

  it('shows the server message when a scan is already running', async () => {
    library({
      'POST /api/library/scans': () =>
        jsonResponse({ error: 'scan_in_progress', message: 'A scan is already running. Try again when it has finished.' }, 409),
    })
    renderApp('/admin/library')

    await userEvent.click(await screen.findByRole('button', { name: 'Scan the folders' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('A scan is already running.')
  })

  it('is for administrators only', async () => {
    const fetch = mockApi({ ...signedInRoutes({ ...testAccount, admin: false }) })
    renderApp('/admin/library')

    expect(await screen.findByRole('heading', { name: 'Administrators only' })).toBeInTheDocument()
    expect(callsTo(fetch, 'GET /api/library/roots')).toHaveLength(0)
    expect(callsTo(fetch, 'GET /api/library/media')).toHaveLength(0)
  })

  it('offers to try again when the folders cannot be loaded', async () => {
    let up = false
    library({
      'GET /api/library/roots': () =>
        up ? jsonResponse([films]) : jsonResponse({ error: 'service_unavailable', message: 'This part of HomeFlix is temporarily unavailable. Please try again in a moment.' }, 503),
    })
    renderApp('/admin/library')

    expect(await screen.findByRole('heading', { name: 'The folders cannot be shown' })).toBeInTheDocument()
    up = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: '/media/library/films' })).toBeInTheDocument()
  })
})
