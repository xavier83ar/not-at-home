import { describe, expect, it } from 'vitest'
import { accountErrorMessage } from '~/utils/accountError'
import type { ApiProblem } from '~/utils/problem'

function problem(overrides: Partial<ApiProblem> = {}): ApiProblem {
  return { status: undefined, detail: undefined, code: undefined, fieldErrors: {}, ...overrides }
}

describe('accountErrorMessage', () => {
  it('explains a rate limit', () => {
    expect(accountErrorMessage(problem({ status: 429, retryAfterSeconds: 120 }))).toContain(
      '2 minutes',
    )
  })

  it('leaves field errors (a wrong password, a taken address) to the form', () => {
    expect(
      accountErrorMessage(
        problem({ status: 400, fieldErrors: { currentPassword: ['is incorrect'] } }),
      ),
    ).toBeUndefined()
  })

  it('still says something when a 400 has no field errors', () => {
    expect(accountErrorMessage(problem({ status: 400 }))).toBe(
      'Something went wrong. Please try again.',
    )
  })

  it('tells the last admin why they cannot delete their account, in the backend’s words', () => {
    expect(
      accountErrorMessage(
        problem({
          status: 409,
          code: 'LAST_ADMIN',
          detail: 'There must always be at least one admin. Make someone else an admin first.',
        }),
      ),
    ).toBe('There must always be at least one admin. Make someone else an admin first.')
  })

  it('does not take any other conflict for the last admin', () => {
    expect(accountErrorMessage(problem({ status: 409, detail: 'Something else' }))).toBe(
      'Something went wrong. Please try again.',
    )
  })

  it('asks to log in again or reload when the session is the problem', () => {
    expect(accountErrorMessage(problem({ status: 401 }))).toContain('Log in again')
    expect(accountErrorMessage(problem({ status: 403 }))).toContain('Reload the page')
  })

  it('has a generic message for anything else', () => {
    for (const status of [500, undefined]) {
      expect(accountErrorMessage(problem({ status }))).toBe(
        'Something went wrong. Please try again.',
      )
    }
  })
})
