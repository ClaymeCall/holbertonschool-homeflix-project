/** Formats a duration in seconds for display, for example "1 h 52 min". */
export function formatDuration(seconds: number | null): string | null {
  if (seconds === null || !Number.isFinite(seconds) || seconds <= 0) {
    return null
  }
  const totalMinutes = Math.max(1, Math.round(seconds / 60))
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours === 0) {
    return `${minutes} min`
  }
  return minutes === 0 ? `${hours} h` : `${hours} h ${String(minutes).padStart(2, '0')} min`
}

/** Player clock, for example "4:05" or "1:02:05". */
export function formatClock(seconds: number): string {
  const total = Number.isFinite(seconds) && seconds > 0 ? Math.floor(seconds) : 0
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const secs = String(total % 60).padStart(2, '0')
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${secs}` : `${minutes}:${secs}`
}

/** Spoken form of a player position for screen readers, for example "1 hour 2 minutes 5 seconds". */
export function formatSpokenTime(seconds: number): string {
  const total = Number.isFinite(seconds) && seconds > 0 ? Math.floor(seconds) : 0
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const secs = total % 60
  const parts = []
  if (hours > 0) parts.push(unit(hours, 'hour'))
  if (hours > 0 || minutes > 0) parts.push(unit(minutes, 'minute'))
  parts.push(unit(secs, 'second'))
  return parts.join(' ')
}

function unit(value: number, name: string): string {
  return `${value} ${name}${value === 1 ? '' : 's'}`
}
