import type { MediaSummary } from '../api/library'

export function media(overrides: Partial<MediaSummary> & { id: string; title: string }): MediaSummary {
  return {
    posterUrl: null,
    genre: null,
    durationSeconds: null,
    availability: 'AVAILABLE',
    ...overrides,
  }
}
