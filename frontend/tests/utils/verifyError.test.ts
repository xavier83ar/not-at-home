import { describe, expect, it } from 'vitest'
import { verifyErrorMessage } from '~/utils/verifyError'

const problem = (status: number | undefined, detail?: string, retryAfterSeconds?: number) => ({
  status,
  detail,
  code: undefined,
  retryAfterSeconds,
  fieldErrors: {},
})

describe('verifyErrorMessage', () => {
  it('says how long to wait when there were too many attempts, not the generic detail', () => {
    expect(verifyErrorMessage(problem(429, 'Too many requests. Please try again later.', 90))).toBe(
      'Too many attempts. Please wait 2 minutes and try again.',
    )
  })

  it("uses the backend's sentence for a bad, used or expired link", () => {
    expect(
      verifyErrorMessage(problem(400, 'This verification link is invalid or has expired.')),
    ).toBe('This verification link is invalid or has expired.')
  })

  it('has a generic message when the backend said nothing', () => {
    for (const status of [500, undefined]) {
      expect(verifyErrorMessage(problem(status))).toBe('Something went wrong. Please try again.')
    }
  })
})
