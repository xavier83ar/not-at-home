import { describe, expect, it } from 'vitest'
import { parseProblem, safeRedirect } from '~/utils/problem'

describe('parseProblem', () => {
  it('reads the status and detail of a ProblemDetail', () => {
    const error = { statusCode: 401, data: { status: 401, detail: 'Invalid username or password' } }
    expect(parseProblem(error)).toEqual({
      status: 401,
      detail: 'Invalid username or password',
      fieldErrors: {},
    })
  })

  it('collects field errors from a validation failure', () => {
    const error = {
      status: 400,
      data: {
        detail: 'Validation failed',
        errors: { login: ['must not be blank'], password: ['x', 'y'] },
      },
    }
    expect(parseProblem(error).fieldErrors).toEqual({
      login: ['must not be blank'],
      password: ['x', 'y'],
    })
  })

  it('reads the machine-readable code that some 403s carry', () => {
    const verify = {
      status: 403,
      data: { detail: 'Verify your email address to do this.', code: 'EMAIL_NOT_VERIFIED' },
    }
    expect(parseProblem(verify).code).toBe('EMAIL_NOT_VERIFIED')
    expect(parseProblem({ status: 403, data: {} }).code).toBeUndefined()
    expect(parseProblem({ status: 403, data: { code: 42 } }).code).toBeUndefined()
  })

  it('reads how long to wait from a 429', () => {
    const limited = { status: 429, data: { code: 'RATE_LIMITED', retryAfterSeconds: 12 } }
    expect(parseProblem(limited).retryAfterSeconds).toBe(12)
    expect(
      parseProblem({ status: 429, data: { retryAfterSeconds: '12' } }).retryAfterSeconds,
    ).toBeUndefined()
  })

  it('copes with errors that have no response (network failures) or odd shapes', () => {
    expect(parseProblem(new Error('fetch failed'))).toEqual({
      status: undefined,
      detail: undefined,
      code: undefined,
      fieldErrors: {},
    })
    expect(parseProblem(undefined).status).toBeUndefined()
    expect(parseProblem({ data: { errors: 'nope' } }).fieldErrors).toEqual({})
  })
})

describe('safeRedirect', () => {
  it('allows paths on this site', () => {
    expect(safeRedirect('/categories/hiking?page=2')).toBe('/categories/hiking?page=2')
  })

  it('falls back for anything that could leave the site', () => {
    for (const target of [
      'https://evil.example',
      '//evil.example',
      '/\\evil.example',
      'evil',
      '',
      42,
      undefined,
      ['/a'],
    ]) {
      expect(safeRedirect(target)).toBe('/')
    }
  })

  it('uses the given fallback', () => {
    expect(safeRedirect(undefined, '/home')).toBe('/home')
  })
})
