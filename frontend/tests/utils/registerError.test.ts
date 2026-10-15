import { describe, expect, it } from 'vitest'
import { registerErrorMessage } from '~/utils/registerError'

const problem = (status: number | undefined, retryAfterSeconds?: number) => ({
  status,
  detail: undefined,
  code: undefined,
  retryAfterSeconds,
  fieldErrors: {},
})

describe('registerErrorMessage', () => {
  it('leaves validation failures to the field errors', () => {
    expect(registerErrorMessage(problem(400))).toBeUndefined()
  })

  it('says how long to wait when there were too many attempts', () => {
    expect(registerErrorMessage(problem(429, 720))).toBe(
      'Too many attempts. Please wait 12 minutes and try again.',
    )
  })

  it('tells people to reload when the CSRF token was rejected', () => {
    expect(registerErrorMessage(problem(403))).toContain('Reload the page')
  })

  it('has a generic message for server and network failures', () => {
    expect(registerErrorMessage(problem(500))).toBe('Something went wrong. Please try again.')
    expect(registerErrorMessage(problem(undefined))).toBe('Something went wrong. Please try again.')
  })
})
