import { describe, expect, it } from 'vitest'
import { firstQueryValue } from '~/utils/query'

describe('firstQueryValue', () => {
  it('returns a plain value', () => {
    expect(firstQueryValue('abc')).toBe('abc')
  })

  it('takes the first of repeated values', () => {
    expect(firstQueryValue(['abc', 'def'])).toBe('abc')
  })

  it('treats missing, empty and valueless parameters as absent', () => {
    expect(firstQueryValue(undefined)).toBeUndefined()
    expect(firstQueryValue(null)).toBeUndefined()
    expect(firstQueryValue('')).toBeUndefined()
    expect(firstQueryValue([])).toBeUndefined()
    expect(firstQueryValue([null])).toBeUndefined()
  })

  it('ignores anything that is not text', () => {
    expect(firstQueryValue(42)).toBeUndefined()
    expect(firstQueryValue({})).toBeUndefined()
  })
})
