import { describe, expect, it } from 'vitest'
import { forwardedHeaders, resolveClientIp } from '../../shared/utils/forwarded'

describe('resolveClientIp', () => {
  it('is the address that connected, and ignores what the visitor claims', () => {
    expect(
      resolveClientIp({ peer: '203.0.113.7', forwardedFor: '1.2.3.4', trustForwardedFor: false }),
    ).toBe('203.0.113.7')
    expect(
      resolveClientIp({
        peer: '203.0.113.7',
        forwardedFor: '1.1.1.1, 2.2.2.2',
        trustForwardedFor: false,
      }),
    ).toBe('203.0.113.7')
  })

  it('behind a trusted proxy, believes only the last entry, which the proxy appended', () => {
    expect(
      resolveClientIp({
        peer: '10.0.0.5',
        forwardedFor: '6.6.6.6, 198.51.100.9',
        trustForwardedFor: true,
      }),
    ).toBe('198.51.100.9')
    expect(
      resolveClientIp({
        peer: '10.0.0.5',
        forwardedFor: ' 198.51.100.9 ',
        trustForwardedFor: true,
      }),
    ).toBe('198.51.100.9')
  })

  it('falls back to the peer when a trusted proxy sent nothing usable', () => {
    for (const forwardedFor of [undefined, '', ' , ']) {
      expect(resolveClientIp({ peer: '10.0.0.5', forwardedFor, trustForwardedFor: true })).toBe(
        '10.0.0.5',
      )
    }
  })

  it('is undefined when nothing is known', () => {
    expect(
      resolveClientIp({ peer: undefined, forwardedFor: undefined, trustForwardedFor: false }),
    ).toBeUndefined()
  })
})

describe('forwardedHeaders', () => {
  it('replaces the forwarded address with a single one and adds host and protocol', () => {
    expect(
      forwardedHeaders({
        peer: '203.0.113.7',
        forwardedFor: '1.2.3.4',
        trustForwardedFor: false,
        host: 'forum.example',
        proto: 'https',
      }),
    ).toEqual({
      'x-forwarded-for': '203.0.113.7',
      'x-forwarded-host': 'forum.example',
      'x-forwarded-proto': 'https',
    })
  })

  it('still overrides the header when the address is unknown, so a forged one cannot pass', () => {
    expect(
      forwardedHeaders({ peer: undefined, forwardedFor: '1.2.3.4', trustForwardedFor: false }),
    ).toEqual({ 'x-forwarded-for': '' })
  })
})
