import { describe, expect, it } from 'vitest'
import { loginErrorMessage } from '~/utils/loginError'

const problem = (status: number | undefined, code?: string, retryAfterSeconds?: number) => ({
  status,
  detail: undefined,
  code,
  retryAfterSeconds,
  fieldErrors: {},
})

describe('loginErrorMessage', () => {
  it('says the credentials were wrong on a 401', () => {
    expect(loginErrorMessage(problem(401))).toBe('Invalid username or password.')
  })

  it('leaves validation failures to the field errors', () => {
    expect(loginErrorMessage(problem(400))).toBeUndefined()
  })

  it('says how long to wait when there were too many attempts', () => {
    expect(loginErrorMessage(problem(429, 'RATE_LIMITED', 6))).toBe(
      'Too many attempts. Please wait 6 seconds and try again.',
    )
    expect(loginErrorMessage(problem(429))).toContain('wait a while')
  })

  it('says so when the account is banned', () => {
    expect(loginErrorMessage(problem(403, 'ACCOUNT_BANNED'))).toBe('This account has been banned.')
  })

  it('tells people to reload when the CSRF token was rejected', () => {
    expect(loginErrorMessage(problem(403))).toContain('Reload the page')
  })

  it('has a generic message for server and network failures', () => {
    expect(loginErrorMessage(problem(500))).toBe('Something went wrong. Please try again.')
    expect(loginErrorMessage(problem(undefined))).toBe('Something went wrong. Please try again.')
  })
})
