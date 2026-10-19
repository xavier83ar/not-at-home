import { describe, expect, it } from 'vitest'
import { profileErrorMessage } from '~/utils/profileError'
import type { ApiProblem } from '~/utils/problem'

function problem(overrides: Partial<ApiProblem> = {}): ApiProblem {
  return { status: undefined, detail: undefined, code: undefined, fieldErrors: {}, ...overrides }
}

describe('profileErrorMessage', () => {
  it('explains a rate limit', () => {
    expect(profileErrorMessage(problem({ status: 429, retryAfterSeconds: 120 }))).toContain(
      '2 minutes',
    )
  })

  it('phrases profile text problems as sentences about it', () => {
    expect(
      profileErrorMessage(
        problem({ status: 400, fieldErrors: { profileContent: ['is too long'] } }),
      ),
    ).toBe('Your profile text is too long.')
    expect(
      profileErrorMessage(
        problem({
          status: 400,
          fieldErrors: { profileContent: ["block 2: 'table' blocks are not allowed"] },
        }),
      ),
    ).toBe("There is a problem with your profile text: block 2: 'table' blocks are not allowed.")
  })

  it('tells when the chosen avatar was refused', () => {
    expect(
      profileErrorMessage(
        problem({ status: 400, fieldErrors: { uploadId: ['upload not found'] } }),
      ),
    ).toBe('That image could not be used. Upload it again.')
  })

  it('leaves the display name error to the form', () => {
    expect(
      profileErrorMessage(
        problem({ status: 400, fieldErrors: { displayName: ['must be at most 50 characters'] } }),
      ),
    ).toBeUndefined()
  })

  it('asks to log in again or reload when the session is the problem', () => {
    expect(profileErrorMessage(problem({ status: 401 }))).toContain('Log in again')
    expect(profileErrorMessage(problem({ status: 403 }))).toContain('Reload the page')
  })

  it('has a generic message for anything else', () => {
    expect(profileErrorMessage(problem({ status: 500 }))).toBe(
      'Something went wrong. Please try again.',
    )
  })
})
