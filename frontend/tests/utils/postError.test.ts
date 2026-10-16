import { describe, expect, it } from 'vitest'
import { postErrorMessage } from '~/utils/postError'
import type { ApiProblem } from '~/utils/problem'

function problem(overrides: Partial<ApiProblem> = {}): ApiProblem {
  return { status: undefined, detail: undefined, code: undefined, fieldErrors: {}, ...overrides }
}

describe('postErrorMessage', () => {
  it('explains a rate limit', () => {
    expect(postErrorMessage(problem({ status: 429, retryAfterSeconds: 120 }))).toContain(
      '2 minutes',
    )
  })

  it('phrases content problems as sentences about the post', () => {
    expect(
      postErrorMessage(problem({ status: 400, fieldErrors: { content: ["can't be empty"] } })),
    ).toBe("Your post can't be empty.")
    expect(
      postErrorMessage(
        problem({
          status: 400,
          fieldErrors: { content: ["block 2: 'table' blocks are not allowed"] },
        }),
      ),
    ).toBe("There is a problem with your post: block 2: 'table' blocks are not allowed.")
  })

  it('leaves other field errors to the form', () => {
    expect(
      postErrorMessage(problem({ status: 400, fieldErrors: { title: ['is required'] } })),
    ).toBeUndefined()
  })

  it('tells apart the reasons for a 403', () => {
    expect(postErrorMessage(problem({ status: 403, code: 'EMAIL_NOT_VERIFIED' }))).toContain(
      'Verify',
    )
    expect(postErrorMessage(problem({ status: 403, code: 'TOPIC_LOCKED' }))).toContain('locked')
    expect(
      postErrorMessage(problem({ status: 403, detail: 'You can only change your own posts.' })),
    ).toBe('You can only change your own posts.')
    expect(postErrorMessage(problem({ status: 403 }))).toContain('Reload')
  })

  it('handles the rest', () => {
    expect(postErrorMessage(problem({ status: 401 }))).toContain('Log in')
    expect(postErrorMessage(problem({ status: 404 }))).toContain('no longer exists')
    expect(postErrorMessage(problem({ status: 500 }))).toContain('Something went wrong')
  })
})
