import { describe, expect, it } from 'vitest'
import { formatClock, formatDuration, formatSpokenTime } from './format'

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

describe('formatClock', () => {
  it('formats player positions', () => {
    expect(formatClock(0)).toBe('0:00')
    expect(formatClock(245)).toBe('4:05')
    expect(formatClock(3725)).toBe('1:02:05')
    expect(formatClock(Number.NaN)).toBe('0:00')
  })
})

describe('formatSpokenTime', () => {
  it('formats positions for screen readers', () => {
    expect(formatSpokenTime(5)).toBe('5 s')
    expect(formatSpokenTime(605)).toBe('10 min 05 s')
    expect(formatSpokenTime(3725)).toBe('1 h 02 min 05 s')
  })
})
