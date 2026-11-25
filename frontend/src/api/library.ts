/**
 * Library service API (catalogue and playback).
 *
 * PROVISIONAL: the OpenAPI contract (docs/api/, ADR-0010) and the endpoints
 * (issues H7 and I3) do not exist yet. Paths and shapes below are assumptions
 * kept in this single file so they can be aligned with the contract later.
 */
import { apiRequest, fetchAllPages } from './client'
import { isNotFound } from './errors'

const BASE = '/api/library'

export type Availability = 'AVAILABLE' | 'UNAVAILABLE' | 'DOWNLOADING'

export interface MediaSummary {
  id: string
  title: string
  /** External image URL from the allowed host list (ADR-0018), or null. */
  posterUrl: string | null
  /** Zero or more genre names (a title can have several). */
  genres: string[]
  durationSeconds: number | null
  availability: Availability
}

export interface MediaDetails extends MediaSummary {
  description: string | null
}

export function getMedia(id: string, signal?: AbortSignal): Promise<MediaDetails> {
  return apiRequest<MediaDetails>(`${BASE}/media/${encodeURIComponent(id)}`, { signal })
}

/** A home screen category: the titles of one genre. */
export interface Category {
  genre: string
  items: MediaSummary[]
}

export interface SearchFilters {
  /** Part of a title; empty for no title filter. */
  query: string
  /** One genre from getGenres(); empty for all genres. */
  genre: string
}

export function getRecentMedia(limit: number, signal?: AbortSignal): Promise<MediaSummary[]> {
  return apiRequest<MediaSummary[]>(`${BASE}/media?sort=recent&limit=${limit}`, { signal })
}

export function getCategories(signal?: AbortSignal): Promise<Category[]> {
  return apiRequest<Category[]>(`${BASE}/categories`, { signal })
}

export function searchMedia({ query, genre }: SearchFilters, signal?: AbortSignal): Promise<MediaSummary[]> {
  const params = new URLSearchParams()
  if (query) params.set('query', query)
  if (genre) params.set('genre', genre)
  const search = params.toString()
  return apiRequest<MediaSummary[]>(`${BASE}/media${search ? `?${search}` : ''}`, { signal })
}

export function getGenres(signal?: AbortSignal): Promise<string[]> {
  return apiRequest<string[]>(`${BASE}/genres`, { signal })
}

/** Streaming URL for the browser's <video> element. The cookie authenticates it (ADR-0009, issue I3). */
export function streamUrl(id: string): string {
  return `${BASE}/media/${encodeURIComponent(id)}/stream`
}

/**
 * The <video> element does not expose the HTTP status of a failed stream, so
 * after a playback error we ask for the first byte to learn why it failed.
 * Returns the HTTP status, or 0 when the server cannot be reached.
 */
export async function probeStream(id: string): Promise<number> {
  try {
    const response = await fetch(streamUrl(id), {
      headers: { Range: 'bytes=0-0' },
      credentials: 'same-origin',
    })
    await response.body?.cancel()
    return response.status
  } catch {
    return 0
  }
}

// ---- Administration (K1): docs/api/library.yaml, operations marked `x-permission: admin` ----

/** An authorized media folder (H2). */
export interface Root {
  id: string
  /** Folder inside the container, for example /media/library/films. */
  path: string
  createdAt: string
}

export type InaccessibleRootError =
  | 'directory_not_found'
  | 'directory_not_readable'
  | 'not_a_directory'
  | 'scan_failed'

/** The result of a manual indexing (H5). It is not stored by the server. */
export interface ScanReport {
  startedAt: string
  finishedAt: string
  added: number
  updated: number
  markedUnavailable: number
  unsupported: { rootId: string; relativePath: string; error: 'format_not_supported' }[]
  inaccessibleRoots: { rootId: string; error: InaccessibleRootError }[]
}

/** The editable information of a title (H8). Left out or null clears a field, except the title. */
export interface MediaUpdate {
  title: string
  description: string | null
  genres: string[]
  durationSeconds: number | null
  posterUrl: string | null
}

export function listRoots(signal?: AbortSignal): Promise<Root[]> {
  return apiRequest<Root[]>(`${BASE}/roots`, { signal })
}

/** Fails with a code per reason (`directory_not_found`, `root_overlaps`…) and a message for the user. */
export function addRoot(path: string): Promise<Root> {
  return apiRequest<Root>(`${BASE}/roots`, { method: 'POST', body: { path } })
}

/** Never deletes a file. The titles of this folder become unavailable and keep their information. */
export function removeRoot(id: string): Promise<void> {
  return apiRequest<void>(`${BASE}/roots/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/** Indexes the authorized folders and answers once it is done. Fails with 409 `scan_in_progress`. */
export function runScan(): Promise<ScanReport> {
  return apiRequest<ScanReport>(`${BASE}/scans`, { method: 'POST' })
}

/** The contract's largest page. */
const MEDIA_PAGE = 100

/** Every title of the catalogue, alphabetical, whatever its availability. */
export function listAllMedia(signal?: AbortSignal): Promise<MediaSummary[]> {
  return fetchAllPages(MEDIA_PAGE, (offset) =>
    apiRequest<MediaSummary[]>(`${BASE}/media?sort=title&limit=${MEDIA_PAGE}&offset=${offset}`, { signal }),
  )
}

/** Defined by the open pull request #165 (H8). Fails with 400 `validation_failed` and the rejected `fields`. */
export function updateMedia(id: string, update: MediaUpdate): Promise<MediaDetails> {
  return apiRequest<MediaDetails>(`${BASE}/media/${encodeURIComponent(id)}`, { method: 'PUT', body: update })
}

// ---- The active profile's list and progress (I7, I8, I9; docs/api/library.yaml) ----

/** Computed by the server: STARTED from 5 seconds to under 90 %, COMPLETED from 90 % (PLY-09). */
export type ProgressState = 'STARTED' | 'COMPLETED'

export interface ProgressEntry {
  mediaId: string
  positionSeconds: number
  durationSeconds: number
  /** Null when less than 5 seconds were watched. */
  state: ProgressState | null
  updatedAt: string
  /** The title, included by the list so it needs no second request. */
  media?: MediaSummary
}

/** "My List" of the active profile, most recently added first. */
export function listFavourites(signal?: AbortSignal): Promise<MediaSummary[]> {
  return apiRequest<MediaSummary[]>(`${BASE}/favourites`, { signal })
}

export function addFavourite(mediaId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/favourites/${encodeURIComponent(mediaId)}`, { method: 'PUT' })
}

export function removeFavourite(mediaId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/favourites/${encodeURIComponent(mediaId)}`, { method: 'DELETE' })
}

/** "Continue watching": the started titles, most recently watched first. */
export function listContinueWatching(limit: number, signal?: AbortSignal): Promise<ProgressEntry[]> {
  return apiRequest<ProgressEntry[]>(`${BASE}/progress?state=STARTED&limit=${limit}`, { signal })
}

/** Where the active profile stopped in a title, or null when nothing was saved (the server answers 404). */
export async function getProgress(mediaId: string, signal?: AbortSignal): Promise<ProgressEntry | null> {
  try {
    return await apiRequest<ProgressEntry>(`${BASE}/progress/${encodeURIComponent(mediaId)}`, { signal })
  } catch (error) {
    if (isNotFound(error)) return null
    throw error
  }
}

/** Saves the position, in whole seconds. `keepalive` lets the last save finish when the page closes. */
export function saveProgress(
  mediaId: string,
  positionSeconds: number,
  durationSeconds: number,
  { keepalive = false, signal }: { keepalive?: boolean; signal?: AbortSignal } = {},
): Promise<ProgressEntry> {
  return apiRequest<ProgressEntry>(`${BASE}/progress/${encodeURIComponent(mediaId)}`, {
    method: 'PUT',
    body: { positionSeconds, durationSeconds },
    keepalive,
    signal,
  })
}

export function resetProgress(mediaId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/progress/${encodeURIComponent(mediaId)}`, { method: 'DELETE' })
}
