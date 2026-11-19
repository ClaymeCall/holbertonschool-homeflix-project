import type { Availability } from './api/library'

export const availabilityLabel: Record<Availability, string> = {
  AVAILABLE: 'Available',
  UNAVAILABLE: 'Unavailable',
  DOWNLOADING: 'Downloading',
}
