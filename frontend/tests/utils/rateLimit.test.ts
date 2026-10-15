import { describe, expect, it } from 'vitest'
import { rateLimitMessage } from '~/utils/rateLimit'

describe('rateLimitMessage', () => {
  it('says how long to wait, in the natural unit, rounding up', () => {
    expect(rateLimitMessage(1)).toBe('Too many attempts. Please wait 1 second and try again.')
    expect(rateLimitMessage(6)).toBe('Too many attempts. Please wait 6 seconds and try again.')
    expect(rateLimitMessage(60)).toBe('Too many attempts. Please wait 60 seconds and try again.')
    expect(rateLimitMessage(61)).toBe('Too many attempts. Please wait 2 minutes and try again.')
    expect(rateLimitMessage(720)).toBe('Too many attempts. Please wait 12 minutes and try again.')
    expect(rateLimitMessage(3599)).toBe('Too many attempts. Please wait 60 minutes and try again.')
    expect(rateLimitMessage(3600)).toBe('Too many attempts. Please wait 1 hour and try again.')
    expect(rateLimitMessage(7201)).toBe('Too many attempts. Please wait 3 hours and try again.')
  })

  it('still makes sense when the backend gave no usable wait', () => {
    const generic = 'Too many attempts. Please wait a while and try again.'
    for (const value of [undefined, 0, -5, Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(rateLimitMessage(value)).toBe(generic)
    }
  })
})
