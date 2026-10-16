import { describe, expect, it } from 'vitest'
import { uploadErrorMessage } from '~/utils/uploadError'
import type { ApiProblem } from '~/utils/problem'

function problem(overrides: Partial<ApiProblem> = {}): ApiProblem {
  return {
    status: undefined,
    detail: undefined,
    code: undefined,
    fieldErrors: {},
    ...overrides,
  }
}

describe('uploadErrorMessage', () => {
  it('shows the backend field message when there is one', () => {
    expect(
      uploadErrorMessage(
        problem({ status: 400, fieldErrors: { file: ['must be 5 MB or smaller'] } }),
      ),
    ).toBe('must be 5 MB or smaller')
  })

  it('explains a rate limit', () => {
    expect(uploadErrorMessage(problem({ status: 429, retryAfterSeconds: 60 }))).toContain('wait')
  })

  it('explains a missing login on 401', () => {
    expect(uploadErrorMessage(problem({ status: 401 }))).toBe(
      'You need to be logged in to upload a file.',
    )
  })

  it('falls back to a generic message otherwise', () => {
    expect(uploadErrorMessage(problem({ status: 500 }))).toBe(
      'Could not upload the file. Please try again.',
    )
  })
})
