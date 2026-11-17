import { describe, expect, it } from 'vitest'
import { formatDuration } from './format'

describe('formatDuration', () => {
  it('formats hours and minutes', () => {
    expect(formatDuration(6720)).toBe('1 h 52 min')
    expect(formatDuration(3900)).toBe('1 h 05 min')
  })

  it('formats whole hours and short durations', () => {
    expect(formatDuration(7200)).toBe('2 h')
    expect(formatDuration(2700)).toBe('45 min')
    expect(formatDuration(20)).toBe('1 min')
  })

  it('returns null when the duration is unknown', () => {
    expect(formatDuration(null)).toBeNull()
    expect(formatDuration(0)).toBeNull()
  })
})
