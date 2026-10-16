import { describe, expect, it } from 'vitest'
import { firstQueryValue, parsePage } from '~/utils/query'

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

describe('parsePage', () => {
  it('reads a page number', () => {
    expect(parsePage('3')).toBe(3)
    expect(parsePage(['2', '5'])).toBe(2)
  })

  it('falls back to the first page for anything else', () => {
    for (const value of [undefined, '', '0', '-2', '1.5', 'abc', '2abc', '9999999', {}]) {
      expect(parsePage(value), String(value)).toBe(value === '9999999' ? 1 : 1)
    }
  })
})
