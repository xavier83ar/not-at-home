import { describe, expect, it } from 'vitest'
import { resendErrorMessage } from '~/utils/resendError'

const problem = (status: number | undefined, retryAfterSeconds?: number) => ({
  status,
  detail: undefined,
  code: undefined,
  retryAfterSeconds,
  fieldErrors: {},
})

describe('resendErrorMessage', () => {
  it('says how long to wait when the limit for this address is used up', () => {
    expect(resendErrorMessage(problem(429, 1800))).toBe(
      'Too many attempts. Please wait 30 minutes and try again.',
    )
  })

  it('has a generic message for anything else', () => {
    for (const status of [401, 500, undefined]) {
      expect(resendErrorMessage(problem(status))).toBe(
        'Could not send the email. Please try again in a moment.',
      )
    }
  })
})
