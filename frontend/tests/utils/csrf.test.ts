import { describe, expect, it } from 'vitest'
import { needsCsrfToken, readCookie } from '~/utils/csrf'

describe('needsCsrfToken', () => {
  it('skips reads', () => {
    expect(needsCsrfToken(undefined)).toBe(false)
    expect(needsCsrfToken('GET')).toBe(false)
    expect(needsCsrfToken('get')).toBe(false)
    expect(needsCsrfToken('HEAD')).toBe(false)
  })

  it('requires the token for anything that changes state', () => {
    for (const method of ['POST', 'post', 'PUT', 'PATCH', 'DELETE']) {
      expect(needsCsrfToken(method)).toBe(true)
    }
  })
})

describe('readCookie', () => {
  it('finds a cookie among several', () => {
    const header = 'theme=dark; XSRF-TOKEN=abc-123; SESSION=xyz'
    expect(readCookie('XSRF-TOKEN', header)).toBe('abc-123')
  })

  it('does not match on a name suffix or prefix', () => {
    expect(readCookie('XSRF-TOKEN', 'OLD-XSRF-TOKEN=nope; XSRF-TOKEN-2=nope')).toBeUndefined()
  })

  it('keeps "=" inside the value and decodes it', () => {
    expect(readCookie('a', 'a=x%3Dy==')).toBe('x=y==')
  })

  it('treats an empty value (a cleared cookie) as missing', () => {
    expect(readCookie('XSRF-TOKEN', 'XSRF-TOKEN=; SESSION=1')).toBeUndefined()
  })

  it('handles no cookies at all', () => {
    expect(readCookie('XSRF-TOKEN', undefined)).toBeUndefined()
    expect(readCookie('XSRF-TOKEN', '')).toBeUndefined()
  })
})
