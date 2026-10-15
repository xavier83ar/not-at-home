import { describe, expect, it } from 'vitest'
import { loginErrorMessage } from '~/utils/loginError'

const problem = (status: number | undefined) => ({ status, detail: undefined, fieldErrors: {} })

describe('loginErrorMessage', () => {
  it('says the credentials were wrong on a 401', () => {
    expect(loginErrorMessage(problem(401))).toBe('Invalid username or password.')
  })

  it('leaves validation failures to the field errors', () => {
    expect(loginErrorMessage(problem(400))).toBeUndefined()
  })

  it('tells people to reload when the CSRF token was rejected', () => {
    expect(loginErrorMessage(problem(403))).toContain('Reload the page')
  })

  it('has a generic message for server and network failures', () => {
    expect(loginErrorMessage(problem(500))).toBe('Something went wrong. Please try again.')
    expect(loginErrorMessage(problem(undefined))).toBe('Something went wrong. Please try again.')
  })
})
