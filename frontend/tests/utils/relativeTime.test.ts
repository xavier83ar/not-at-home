import { describe, expect, it } from 'vitest'
import { initials } from '~/utils/initials'
import { absoluteDateUtc, relativeTime } from '~/utils/relativeTime'

const now = new Date('2026-10-16T12:00:00Z')

describe('relativeTime', () => {
  it('says "just now" for the last few seconds', () => {
    expect(relativeTime('2026-10-16T11:59:30Z', now)).toBe('just now')
  })

  it('uses the largest fitting unit', () => {
    expect(relativeTime('2026-10-16T11:55:00Z', now)).toBe('5 minutes ago')
    expect(relativeTime('2026-10-16T10:00:00Z', now)).toBe('2 hours ago')
    expect(relativeTime('2026-10-14T12:00:00Z', now)).toBe('2 days ago')
    expect(relativeTime('2026-09-01T12:00:00Z', now)).toBe('last month')
    expect(relativeTime('2024-10-16T12:00:00Z', now)).toBe('2 years ago')
  })

  it('handles singular units', () => {
    expect(relativeTime('2026-10-16T11:00:00Z', now)).toBe('1 hour ago')
  })

  it('handles dates slightly in the future (clock differences)', () => {
    expect(relativeTime('2026-10-16T12:00:10Z', now)).toBe('just now')
    expect(relativeTime('2026-10-16T15:00:00Z', now)).toBe('in 3 hours')
  })
})

describe('absoluteDateUtc', () => {
  it('is the same wherever it is rendered', () => {
    expect(absoluteDateUtc('2026-10-16T15:30:00Z')).toBe('Oct 16, 2026, 3:30 PM UTC')
    expect(absoluteDateUtc('2026-01-05T00:05:00Z')).toBe('Jan 5, 2026, 12:05 AM UTC')
    expect(absoluteDateUtc('2026-12-31T12:00:00Z')).toBe('Dec 31, 2026, 12:00 PM UTC')
  })
})

describe('initials', () => {
  it('takes the first letters of the first two words', () => {
    expect(initials('Javier Melero')).toBe('JM')
    expect(initials('ana maría de la cruz')).toBe('AM')
  })

  it('takes two letters of a single word', () => {
    expect(initials('alice')).toBe('AL')
    expect(initials('x')).toBe('X')
  })

  it('has a fallback for an empty name', () => {
    expect(initials('   ')).toBe('?')
  })
})
