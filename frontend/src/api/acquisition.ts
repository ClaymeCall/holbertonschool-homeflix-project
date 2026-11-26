/**
 * Acquisition service API (docs/api/acquisition.yaml). Every operation is administrator only
 * (ACQ-05): the server refuses it to anyone else, whatever the interface shows.
 *
 * No operation takes a URL, a magnet link or a file (ACQ-09, ACQ-10): a download names a catalogue
 * entry, and the server uses that entry's own source.
 */
import { apiRequest } from './client'

const BASE = '/api/acquisition'

/** Content the team has verified as downloadable and redistributable (ACQ-01, ACQ-02). */
export interface Entry {
  id: string
  title: string
  sourceName: string
  sourceUrl: string
  licence: string
  licenceUrl: string
  /** Credit the licence requires, or null. */
  attribution: string | null
  expectedSizeBytes: number
  expectedFormat: 'MP4'
}

export type DownloadState = 'QUEUED' | 'DOWNLOADING' | 'COMPLETED' | 'FAILED'

export interface Download {
  id: string
  entryId: string
  state: DownloadState
  /** 0 to 100. A download at 100 % that is still DOWNLOADING is being checked and added to the library. */
  progressPercent: number
  errorCode: string | null
  /** Why a failed download failed, written by the server for the user (ADR-0021). */
  errorMessage: string | null
  createdAt: string
  finishedAt: string | null
}

export function listEntries(signal?: AbortSignal): Promise<Entry[]> {
  return apiRequest<Entry[]>(`${BASE}/entries`, { signal })
}

/** Every download, newest first. */
export function listDownloads(signal?: AbortSignal): Promise<Download[]> {
  return apiRequest<Download[]>(`${BASE}/downloads`, { signal })
}

/**
 * Starts the download of a catalogue entry. `rightsAcknowledged` records that the administrator read the
 * rights reminder (ACQ-11): the server refuses the start without it.
 */
export function startDownload(entryId: string): Promise<Download> {
  return apiRequest<Download>(`${BASE}/downloads`, {
    method: 'POST',
    body: { entryId, rightsAcknowledged: true },
  })
}

export function cancelDownload(id: string): Promise<Download> {
  return apiRequest<Download>(`${BASE}/downloads/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
}

export function isActive(download: Download): boolean {
  return download.state === 'QUEUED' || download.state === 'DOWNLOADING'
}

/**
 * Deletes a finished download (completed, or failed) from the history. Only the record goes: the title stays in
 * the library. Fails with 409 `download_not_deletable` for an active download.
 */
export function deleteDownload(id: string): Promise<void> {
  return apiRequest<void>(`${BASE}/downloads/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
