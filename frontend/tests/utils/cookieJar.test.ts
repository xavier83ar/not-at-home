import { describe, expect, it } from 'vitest'
import { applySetCookies, parseCookieHeader, serializeCookieJar } from '~/utils/cookieJar'

const NOW = Date.parse('2026-10-15T12:00:00Z')

describe('parseCookieHeader / serializeCookieJar', () => {
  it('round-trips a header, values untouched', () => {
    const header = 'remember-me=YWJj%3D%3D; XSRF-TOKEN=abc-123; theme=dark'
    expect(serializeCookieJar(parseCookieHeader(header))).toBe(header)
  })

  it('keeps "=" inside values', () => {
    expect(parseCookieHeader('a=b=c').get('a')).toBe('b=c')
  })

  it('handles no cookies', () => {
    expect(serializeCookieJar(parseCookieHeader(undefined))).toBeUndefined()
    expect(serializeCookieJar(parseCookieHeader(''))).toBeUndefined()
  })
})

describe('applySetCookies', () => {
  it('adds new cookies and replaces changed ones, ignoring attributes', () => {
    const jar = parseCookieHeader('remember-me=old; theme=dark')
    applySetCookies(
      jar,
      [
        'SESSION=NjBlYTNi; Path=/; HttpOnly; SameSite=Lax',
        'remember-me=new; Max-Age=2592000; Path=/; HttpOnly',
      ],
      NOW,
    )
    expect(serializeCookieJar(jar)).toBe('remember-me=new; theme=dark; SESSION=NjBlYTNi')
  })

  it('applies the cookies of one response in order: the last one wins (CSRF rotation)', () => {
    const jar = parseCookieHeader('XSRF-TOKEN=old')
    applySetCookies(
      jar,
      ['XSRF-TOKEN=; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/', 'XSRF-TOKEN=new; Path=/'],
      NOW,
    )
    expect(jar.get('XSRF-TOKEN')).toBe('new')
  })

  it('removes cookies that the backend expires', () => {
    const jar = parseCookieHeader('SESSION=a; remember-me=b; XSRF-TOKEN=c; other=d')
    applySetCookies(
      jar,
      [
        'SESSION=; Max-Age=0; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Path=/',
        'remember-me=x; Max-Age=0; Path=/',
        'XSRF-TOKEN=c; Expires=Thu, 01 Jan 1970 00:00:10 GMT',
        'other=; Path=/',
      ],
      NOW,
    )
    expect(serializeCookieJar(jar)).toBeUndefined()
  })

  it('keeps cookies whose expiry is in the future', () => {
    const jar = parseCookieHeader('')
    applySetCookies(jar, ['a=1; Expires=Fri, 15 Oct 2027 12:00:00 GMT', 'b=2; Max-Age=60'], NOW)
    expect(serializeCookieJar(jar)).toBe('a=1; b=2')
  })

  it('ignores malformed headers', () => {
    const jar = parseCookieHeader('a=1')
    applySetCookies(jar, ['', 'novalue', '=x'], NOW)
    expect(serializeCookieJar(jar)).toBe('a=1')
  })
})
