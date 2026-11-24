import type { DownloadState } from './api/acquisition'
import type { Availability } from './api/library'

export const availabilityLabel: Record<Availability, string> = {
  AVAILABLE: 'Available',
  UNAVAILABLE: 'Unavailable',
  DOWNLOADING: 'Downloading',
}

/** The four download states the brief asks to show (ACQ-04). */
export const downloadStateLabel: Record<DownloadState, string> = {
  QUEUED: 'Queued',
  DOWNLOADING: 'Downloading',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
}
