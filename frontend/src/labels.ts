import type { Availability } from './api/library'

export const availabilityLabel: Record<Availability, string> = {
  AVAILABLE: 'Disponible',
  UNAVAILABLE: 'Indisponible',
  DOWNLOADING: 'En téléchargement',
}
