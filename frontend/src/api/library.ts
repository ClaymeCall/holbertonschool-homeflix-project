/**
 * Library service API (catalogue and playback).
 *
 * PROVISIONAL: the OpenAPI contract (docs/api/, ADR-0010) and the endpoints
 * (issues H7 and I3) do not exist yet. Paths and shapes below are assumptions
 * kept in this single file so they can be aligned with the contract later.
 */
import { apiRequest } from './client'

const BASE = '/api/library'

export type Availability = 'AVAILABLE' | 'UNAVAILABLE' | 'DOWNLOADING'

export interface MediaSummary {
  id: string
  title: string
  /** External image URL from the allowed host list (ADR-0018), or null. */
  posterUrl: string | null
  genre: string | null
  durationSeconds: number | null
  availability: Availability
}

export interface MediaDetails extends MediaSummary {
  description: string | null
}

export function getMedia(id: string, signal?: AbortSignal): Promise<MediaDetails> {
  return apiRequest<MediaDetails>(`${BASE}/media/${encodeURIComponent(id)}`, { signal })
}
